package cn.net.rjnetwork.demo.cdp;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于 CDP 的闲鱼操作机器人。
 *
 * <p>所有交互都以「可见文本 / CSS 选择器」为定位依据（而非脆弱的 XPath 或写死的层级），
 * 因此能容忍闲鱼前端 DOM 结构的变动。对于需要登录态的功能（商品、消息、发货、验收），
 * 只要当前标签页处于登录态即可直接驱动；未登录时可通过 {@link #getLoginQrBase64()} 获取
 * 登录二维码，用户在手机上扫码后即进入登录态。</p>
 */
public class XianyuCdpBot {

    private final CdpClient client;
    private final String xianyuUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public XianyuCdpBot(CdpClient client, String xianyuUrl) {
        this.client = client;
        this.xianyuUrl = xianyuUrl;
    }

    // ============================ 基础工具 ============================

    /** 将 Java 字符串安全转义为 JS 字符串字面量（带引号）。 */
    private String js(String s) {
        try {
            return mapper.writeValueAsString(s == null ? "" : s);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private String eval(String expression) {
        try {
            return client.evaluateString(expression);
        } catch (Exception e) {
            return "";
        }
    }

    private JsonNode evalJson(String expression) {
        try {
            JsonNode r = client.evaluate(expression).join();
            if (r != null && r.has("result") && r.get("result").has("value")) {
                return mapper.readTree(r.get("result").get("value").asText());
            }
        } catch (Exception ignore) {
        }
        return mapper.createArrayNode();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    public void navigate(String url) {
        try {
            // ⚠️ 远端后台标签页的 Page.navigate【永远不会触发 load 事件】，
            // 因此不能大超时硬等——用 5s 兜底即可（实际导航命令本身是异步立即返回的）。
            client.navigate(url).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // SPA 页面（如 goofish）不提供 load 命令确认，超时正常，忽略
        }
        sleep(3000); // SPA 渲染需要更长时间
    }

    /**
     * 提取页面可见文本（轻量版）：使用 TreeWalker 遍历前 200 个文本节点，
     * 避免 innerText 在 heavy SPA 页上触发布局/样式计算导致长时间阻塞。
     * 返回前 2500 字符的拼接，足以做登录态等关键词检测。
     */
    public String getBodyText() {
        return eval("(function(){"
                + "var s='',n;"
                + "var it=document.createTreeWalker(document.body||document.documentElement,NodeFilter.SHOW_TEXT,null,false);"
                + "for(var c=0;(n=it.nextNode())&&c<200;c++){"
                + "  var t=n.textContent||'';"
                + "  s+=t;"
                + "  if(s.length>2500)break;"
                + "}"
                + "return s;"
                + "})()");
    }

    public String getUrl() {
        return eval("location.href");
    }

    /** 轮询页面文本直到包含目标串或超时。 */
    public boolean waitForText(String text, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (getBodyText().contains(text)) {
                return true;
            }
            sleep(400);
        }
        return false;
    }

    /**
     * 按可见文本点击元素（真实鼠标事件）。exact=true 要求 trimmed 文本相等，
     * 否则包含即可。返回是否找到并点击。
     */
    public boolean clickByText(String text) {
        return clickByText(text, false);
    }

    public boolean clickByText(String text, boolean contains) {
        String expr = "(function(){"
                + "var txt=" + js(text) + ";"
                + "var vh = window.innerHeight || document.documentElement.clientHeight;"
                + "var els=document.querySelectorAll('a,button,[role=button],div,span,li,td');"
                + "var best=null, bestScore=1e9, bestEl=null;"
                + "for(var i=0;i<els.length;i++){"
                + "  var e=els[i];"
                + "  if(e.offsetParent===null) continue;"
                + "  var t=(e.innerText||e.textContent||'').trim();"
                + "  if(!t) continue;"
                + "  var matched=" + (contains ? "t.indexOf(txt)>=0" : "t===txt") + ";"
                + "  if(matched){"
                + "    var r=e.getBoundingClientRect();"
                + "    if(r.width===0||r.height===0) continue;"
                + "    var exact=t===txt;"
                + "    var leaf=e.children.length===0;"
                + "    var textPenalty=exact?0:Math.min(5000,t.length);"
                + "    var areaPenalty=Math.min(5000,(r.width*r.height)/100);"
                + "    var leafPenalty=leaf?0:1000;"
                + "    var cy=r.top+r.height/2;"
                + "    var posPenalty=(cy>=0 && cy<=vh) ? Math.abs(cy - vh/2) : (1e6 + Math.abs(cy));"
                + "    var score=textPenalty*10 + areaPenalty + leafPenalty + posPenalty/100;"
                + "    if(score<bestScore){bestScore=score;best={x:r.left+r.width/2,y:cy,text:t,score:score};bestEl=e;}"
                + "  }"
                + "}"
                + "if(!bestEl) return null;"
                + "if(best.y<0 || best.y>vh){"
                + "  bestEl.scrollIntoView({block:'center'});"
                + "  var r2=bestEl.getBoundingClientRect();"
                + "  best={x:r2.left+r2.width/2,y:r2.top+r2.height/2};"
                + "}"
                + "return JSON.stringify(best);"
                + "})()";
        String res = eval(expr);
        if (res == null || res.equals("null")) {
            return false;
        }
        try {
            JsonNode n = mapper.readTree(res);
            client.click(n.get("x").asDouble(), n.get("y").asDouble());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在包含 keyword 的容器内的按钮（文本为 buttonText）上点击。 */
    public boolean clickActionNear(String keyword, String buttonText) {
        String expr = "(function(){"
                + "var kw=" + js(keyword) + ";var btn=" + js(buttonText) + ";"
                + "var all=document.querySelectorAll('*');"
                + "for(var i=0;i<all.length;i++){"
                + "  var e=all[i];"
                + "  if(e.children.length===0){"
                + "    var t=(e.innerText||e.textContent||'').trim();"
                + "    if(t===btn||t.indexOf(btn)>=0){"
                + "      var p=e.parentElement;"
                + "      while(p){"
                + "        if((p.innerText||'').indexOf(kw)>=0){"
                + "          var r=e.getBoundingClientRect();"
                + "          if(r.width>0&&r.height>0) return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});"
                + "        }"
                + "        p=p.parentElement;"
                + "      }"
                + "    }"
                + "  }"
                + "}"
                + "return null;"
                + "})()";
        String res = eval(expr);
        if (res == null || res.equals("null")) {
            return false;
        }
        try {
            JsonNode n = mapper.readTree(res);
            client.click(n.get("x").asDouble(), n.get("y").asDouble());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 聚焦并清空 input/textarea，再真实键入文本（兼容 React/Vue 受控组件）。 */
    public void typeInto(String selector, String text) {
        String focus = "(function(){var e=document.querySelector(" + js(selector) + ");if(!e)return null;"
                + "e.scrollIntoView({block:'center'});e.focus();"
                + "var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()";
        String f = eval(focus);
        if (f == null || f.equals("null")) {
            throw new RuntimeException("找不到输入框: " + selector);
        }
        try {
            JsonNode n = mapper.readTree(f);
            client.click(n.get("x").asDouble(), n.get("y").asDouble());
            String clear = "(function(){var e=document.querySelector(" + js(selector) + ");if(!e)return;"
                    + "var proto=Object.getPrototypeOf(e);"
                    + "var setter=Object.getOwnPropertyDescriptor(proto,'value');"
                    + "if(setter&&setter.set){setter.set.call(e,'');}else{e.value='';}"
                    + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "e.dispatchEvent(new Event('change',{bubbles:true}));})()";
            eval(clear);
            client.type(text);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 截图视口，返回 base64 PNG（15s 上限，兼容大图网络传输）。 */
    public String screenshotViewport() {
        try {
            client.send("Page.enable").get(5, TimeUnit.SECONDS);
            JsonNode r = client.captureScreenshot().get(15, TimeUnit.SECONDS);
            return r.get("data").asText();
        } catch (Exception e) {
            return "";
        }
    }

    /** 截图包含指定文本的元素的包围盒，返回 base64 PNG（带 8s 上限）。 */
    public String screenshotElementByText(String text) {
        String rectExpr = "(function(){var txt=" + js(text) + ";"
                + "var els=document.querySelectorAll('*');"
                + "for(var i=els.length-1;i>=0;i--){"
                + "  var e=els[i];if(e.offsetParent===null)continue;"
                + "  var t=(e.innerText||'').trim();"
                + "  if(t.indexOf(txt)>=0){"
                + "    var r=e.getBoundingClientRect();"
                + "    if(r.width>0&&r.height>0) return JSON.stringify({x:r.left,y:r.top,w:r.width,h:r.height});"
                + "  }"
                + "}"
                + "return null;})()";
        String res = eval(rectExpr);
        if (res == null || res.equals("null")) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(res);
            ObjectNode p = mapper.createObjectNode();
            p.put("format", "png");
            p.put("captureBeyondViewport", false);
            ObjectNode clip = p.putObject("clip");
            clip.put("x", n.get("x").asInt());
            clip.put("y", n.get("y").asInt());
            clip.put("width", n.get("w").asInt());
            clip.put("height", n.get("h").asInt());
            clip.put("scale", 1);
            JsonNode shot = client.send("Page.captureScreenshot", p).get(8, TimeUnit.SECONDS);
            return shot.get("data").asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 直接从 DOM 提取登录二维码来源，优先于合成截图（远端/后台标签页的
     * Page.captureScreenshot 经常卡死）。多重兜底：
     *   1) 常见二维码选择器（src/alt 含 qr/login/ewm/code/扫码/二维码）
     *   2) 含「扫码/二维码」文本元素附近的 img / canvas
     *   3) 页面任意 canvas（toDataURL）
     *   4) 最大且接近方形的 img（二维码多为 60~500px 方形）
     * 返回 {mode:'img'|'image', data:src或base64}，找不到返回 null。
     */
    private Map<String, Object> extractQrSource() {
        String expr = "(function(){"
                + "function pick(){"
                + "  var sel='img[src*=qr],img[src*=login],img[src*=ewm],img[src*=code],img[src*=qrcode],"
                + "img[alt*=二维码],img[alt*=扫码],img[alt*=qrcode]';"
                + "  var im=document.querySelector(sel); if(im&&im.src) return {mode:'img',data:im.src};"
                + "  var all=document.querySelectorAll('*');"
                + "  for(var i=0;i<all.length;i++){var e=all[i];if(e.children.length===0){"
                + "    var t=(e.innerText||'').trim();"
                + "    if(t.indexOf('扫码')>=0||t.indexOf('二维码')>=0){"
                + "      var p=e.parentElement;var c=0;while(p&&c<6){"
                + "        var imgs=p.getElementsByTagName('img');"
                + "        if(imgs.length&&imgs[0].src) return {mode:'img',data:imgs[0].src};"
                + "        var cv=p.getElementsByTagName('canvas');"
                + "        if(cv.length){try{return {mode:'image',data:cv[0].toDataURL('image/png')};}catch(_){}}"
                + "        p=p.parentElement;c++;}}}"
                + "  }"
                + "  var anyC=document.querySelector('canvas');"
                + "  if(anyC){try{return {mode:'image',data:anyC.toDataURL('image/png')};}catch(_){}}"
                + "  var best=null,bestScore=0;var ia=document.images;"
                + "  for(var j=0;j<ia.length;j++){var e2=ia[j];var r=e2.getBoundingClientRect();"
                + "    var w=Math.round(r.width),h=Math.round(r.height);"
                + "    if(w<60||h<60||w>500||h>500)continue;"
                + "    var sq=1-Math.abs(w-h)/Math.max(w,h);"
                + "    var score=Math.min(w,h)*sq;"
                + "    if(score>bestScore){bestScore=score;best=e2;}}"
                + "  if(best&&best.src) return {mode:'img',data:best.src};"
                + "  return null;"
                + "}"
                + "return JSON.stringify(pick());"
                + "})()";
        String res = eval(expr);
        if (res == null || res.equals("null")) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(res);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", n.get("mode").asText());
            m.put("data", n.get("data").asText());
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private int countImgWithQr() {
        String expr = "(function(){var n=0;var imgs=document.images;"
                + "for(var i=0;i<imgs.length;i++){var s=imgs[i].src||'';var w=imgs[i].getBoundingClientRect().width;"
                + "if((/qr|login|code|ewm/i.test(s)||w>60)&&w<400)n++;}return n;})()";
        try {
            return Integer.parseInt(eval(expr).replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ============================ 1. 登录管理 ============================

    /** 读取当前登录态。 */
    public Map<String, Object> loginStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        String url = getUrl();
        String title = eval("document.title");
        String body = getBodyText();
        String cookie = eval("document.cookie");
        boolean hasLoginButton = body.contains("登录") || body.contains("立即登录");
        boolean hasLoginCookie = cookie.contains("tracknick=") || cookie.contains("unb=")
                || cookie.contains("cookie2=") || cookie.contains("_nk_=");
        boolean hasLoggedInText = body.contains("我发布的") || body.contains("退出登录")
                || body.contains("个人主页") || body.contains("我的闲鱼") || body.contains("发闲置")
                || body.contains("消息") || body.contains("订单");
        boolean pageReady = url != null && !url.isBlank() && body != null && !body.isBlank();
        boolean loggedIn = pageReady && (hasLoginCookie || (!hasLoginButton && hasLoggedInText));
        m.put("loggedIn", loggedIn);
        m.put("hasLoginButton", hasLoginButton);
        m.put("hasLoginCookie", hasLoginCookie);
        m.put("pageReady", pageReady);
        m.put("url", url);
        m.put("title", title);
        return m;
    }

    /**
     * 获取登录二维码。直接读取页面 DOM 中二维码图片/Canvas 的 src（base64 或 URL）。
     * 不依赖 Page.captureScreenshot（背景标签无合成帧，必然卡死 60s）。
     */
    /**
     * 获取登录二维码。流程：点击「登录」→ 点击「扫码登录」→ 截图返回。
     * 截图方案是唯一可靠方案：二维码在跨域 iframe（login.taobao.com）中，无法 DOM 提取；
     * 新建 target 已激活到前台，Page.captureScreenshot 可用（后台标签才卡死）。
     */
    /**
     * 获取登录二维码。流程：点击「登录」→ 点击「扫码登录」→ 截图返回。
     * 截图方案是唯一可靠方案：二维码在跨域 iframe（login.taobao.com）中，无法 DOM 提取。
     */
    public Map<String, Object> getLoginQrBase64() {
        Map<String, Object> out = new LinkedHashMap<>();

        // 1. 点击「登录」按钮
        clickByText("登录", true);
        sleep(1500);

        // 2. 如果默认不是扫码面板，点击「扫码登录」切换
        clickByText("扫码登录", true);
        sleep(2000);

        // 3. 截图（使用已激活的 tab）
        String b64 = screenshotViewport();
        if (b64 != null && !b64.isEmpty()) {
            out.put("present", true);
            out.put("mode", "image");
            out.put("qr", b64);
            out.put("message", "已截取登录页面，请用手机闲鱼 App 扫描图中的二维码");
            return out;
        }

        out.put("present", false);
        out.put("message", "未能截取二维码截图，请确认登录面板已显示二维码");
        return out;
    }

    /**
     * 轻量级二维码提取（eval 代价低：直接查询 img/canvas 元素 src）。不读取 body 文本。
     */
    private Map<String, Object> extractQrSourceLightweight() {
        // 策略1: 找含 qr/login 关键词的 img
        String js1 = "(function(){"
            + "var imgs=document.querySelectorAll('img');"
            + "for(var i=0;i<imgs.length;i++){"
            + "  var s=imgs[i].src||'';"
            + "  if(s.indexOf('qr')>=0||s.indexOf('login')>=0||s.indexOf('ewm')>=0||s.indexOf('code')>=0){"
            + "    return {mode:'img',data:s};"
            + "  }"
            + "}"
            + "return null;})()";
        try {
            String r = eval(js1);
            if (r != null && !r.isEmpty() && !r.equals("null") && r.contains("data")) {
                JsonNode n = mapper.readTree(r);
                if (n.has("data") && n.get("data") != null && !n.get("data").asText().isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", n.get("mode").asText());
                    m.put("qr", n.get("data").asText());
                    return m;
                }
            }
        } catch (Exception ignored) {}

        // 策略2: 找 canvas 二维码（当前页面截图的一部分）
        String js2 = "(function(){"
            + "var canvases=document.querySelectorAll('canvas');"
            + "for(var i=0;i<canvases.length;i++){"
            + "  try{"
            + "    var d=canvases[i].toDataURL('image/png');"
            + "    if(d&&d.length>100)return {mode:'image',data:d};"
            + "  }catch(e){}"
            + "}"
            + "return null;})()";
        try {
            String r = eval(js2);
            if (r != null && !r.isEmpty() && !r.equals("null") && r.contains("data")) {
                JsonNode n = mapper.readTree(r);
                if (n.has("data") && n.get("data") != null && !n.get("data").asText().isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", n.path("mode").asText("image"));
                    m.put("qr", n.get("data").asText());
                    return m;
                }
            }
        } catch (Exception ignored) {}

        // 策略3: 取最大正方形 img（通常是二维码）
        String js3 = "(function(){"
            + "var imgs=document.querySelectorAll('img'),best=null,bestSize=0;"
            + "for(var i=0;i<imgs.length;i++){"
            + "  var w=imgs[i].naturalWidth||imgs[i].width||0,"
            + "      h=imgs[i].naturalHeight||imgs[i].height||0;"
            + "  if(w>80&&h>80&&Math.abs(w-h)<w*0.2&&w*h>bestSize){"
            + "    bestSize=w*h;best=imgs[i].src;"
            + "  }"
            + "}"
            + "return best?{mode:'img',data:best}:null;})()";
        try {
            String r = eval(js3);
            if (r != null && !r.isEmpty() && !r.equals("null") && r.contains("data")) {
                JsonNode n = mapper.readTree(r);
                if (n.has("data") && n.get("data") != null && !n.get("data").asText().isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", n.path("mode").asText("img"));
                    m.put("qr", n.get("data").asText());
                    return m;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 轮询登录态直到登录成功或超时，返回最终状态（含 loggedIn）。
     * 供「页面扫码 → 代码检测登录」闭环使用：前端展示二维码后轮询本方法直至 loggedIn=true。
     */
    public Map<String, Object> waitForLogin(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map<String, Object> last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                last = loginStatus();
                if (Boolean.TRUE.equals(last.get("loggedIn"))) {
                    return last;
                }
            } catch (Exception ignore) {
                // 远端标签偶发卡顿，忽略单次失败继续轮询
            }
            sleep(2000);
        }
        if (last == null) {
            last = new LinkedHashMap<>();
        }
        last.put("timedOut", true);
        return last;
    }

    /** 退出登录（点击「退出登录」）。 */
    public Map<String, Object> logout() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean ok = clickByText("退出登录", true);
        m.put("clicked", ok);
        sleep(1500);
        m.put("loginStatus", loginStatus());
        return m;
    }

    // ============================ 2. 基本信息管理 ============================

    public Map<String, Object> getBasicInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 1. 确认当前在 goofish，不在则导航过去
        String url = getUrl();
        if (url == null || url.isBlank() || !url.contains("goofish.com")) {
            navigate(xianyuUrl);
        }
        // 2. cookie 取 tracknick（闲鱼登录后最可靠的昵称来源）
        String cookie = eval("document.cookie");
        String nickFromCookie = "";
        if (cookie != null) {
            for (String kv : cookie.split(";\\s*")) {
                if (kv.startsWith("tracknick=")) {
                    nickFromCookie = kv.substring("tracknick=".length()).trim();
                    try {
                        nickFromCookie = java.net.URLDecoder.decode(nickFromCookie, "UTF-8");
                    } catch (Exception ignore) {
                    }
                    break;
                }
            }
        }
        // 3. DOM 取昵称
        String nick = eval("(function(){var e=document.querySelector("
                + "[class*='nick'],[class*='Nick'],[class*='userName'],[class*='Username'],"
                + "[class*='user-name'],[class*='profile'],[class*='name']);"
                + "return e?(e.innerText||e.textContent||'').trim():'';})()");
        if (nick.isEmpty()) {
            nick = nickFromCookie;
        }
        m.put("nickname", nick);
        m.put("nickFromCookie", nickFromCookie);
        m.put("url", getUrl());
        // 4. 导航到个人主页提取详细资料字段
        navigate(personalProfileUrl());
        sleep(2500);
        String profileJson = eval("(function(){"
                + "var out={};"
                + "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);"
                + "var textNodes=[];var n;"
                + "while((n=walker.nextNode())&&textNodes.length<400){"
                + "  var t=(n.textContent||'').trim();"
                + "  if(t)textNodes.push(t);"
                + "}"
                + "var joined=textNodes.join(' ');"
                + "var nums=joined.match(/(\\d+(?:[,.]\\d+)*(?:[千万Kk]*))(?:\\s*(?:发布|商品|粉丝|关注|信用|好评|我想要|超赞|被浏览量|被想要))/g);"
                + "out.numbers=nums||[];"
                + "var brief=textNodes.filter(function(t){return t.length>1&&t.length<60&&!/[¥￥€£]/.test(t)&&!/[0-9]{5,}/.test(t)}).slice(0,30);"
                + "out.texts=brief;"
                + "out.title=document.title;"
                + "out.url=location.href;"
                + "return JSON.stringify(out);"
                + "})()");
        m.put("profileUrl", getUrl());
        m.put("excerpt", profileJson);
        m.put("loggedIn", nick != null && !nick.isEmpty());
        return m;
    }

    /** 构造个人主页 URL（基于登录 cookie 的 user id）。 */
    private String personalProfileUrl() {
        // 闲鱼个人主页路径；如无法解析则回退到首页
        String cookie = eval("document.cookie");
        if (cookie != null) {
            for (String kv : cookie.split(";\\s*")) {
                if (kv.startsWith("unb=")) {
                    String uid = kv.substring("unb=".length()).trim();
                    if (!uid.isEmpty() && uid.matches("\\d+")) {
                        return "https://www.goofish.com/personal?id=" + uid;
                    }
                }
            }
        }
        return "https://www.goofish.com/personal";
    }

    // ============================ 3. 商品上下架 ============================

    /** 进入「我发布的」并提取商品卡片（标题/价格/状态）。 */
    public List<Map<String, Object>> listProducts() {
        // 直接导航到个人主页，避免 SPA 侧边栏点击跳转失败
        navigate(personalProfileUrl());
        sleep(2500);
        // 在个人主页里直接执行 JS click 找「我发布的」标签（比坐标模拟更稳）
        String clickExpr = "(function(){"
                + "var els=document.querySelectorAll('a,button,div,span,li,td');"
                + "for(var i=0;i<els.length;i++){"
                + "  var t=(els[i].innerText||'').trim();"
                + "  if(t==='我发布的'&&els[i].offsetParent!==null){"
                + "    els[i].click();"
                + "    return 'clicked';"
                + "  }"
                + "}"
                + "return 'not found';"
                + "})()";
        eval(clickExpr);
        sleep(2500);
        return extractCards();
    }

    /** 上架指定商品（按标题关键字定位所在行，点击「上架」）。 */
    public Map<String, Object> upShelf(String keyword) {
        return shelf(keyword, "上架");
    }

    /** 下架指定商品。 */
    public Map<String, Object> downShelf(String keyword) {
        return shelf(keyword, "下架");
    }

    private Map<String, Object> shelf(String keyword, String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少商品关键字");
            return m;
        }
        // 确保位于「我发布的」
        clickByText("我的", true);
        sleep(600);
        clickByText("我发布的", true);
        sleep(1200);
        boolean ok = clickActionNear(keyword, action);
        sleep(1500);
        m.put("success", ok);
        m.put("keyword", keyword);
        m.put("action", action);
        m.put("message", ok ? ("已点击「" + action + "」") : "未找到匹配的商品/按钮");
        return m;
    }

    // ============================ 4. 消息收发 ============================

    public List<Map<String, Object>> listConversations() {
        clickByText("消息", true);
        sleep(1500);
        return extractCards();
    }

    /** 打开与 conversation 的会话并发送文本。 */
    public Map<String, Object> sendMessage(String conversation, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (conversation == null || conversation.isBlank() || text == null || text.isBlank()) {
            m.put("success", false);
            m.put("message", "会话名称与内容均不能为空");
            return m;
        }
        clickByText("消息", true);
        sleep(1000);
        boolean opened = clickByText(conversation, true);
        if (!opened) {
            m.put("success", false);
            m.put("message", "未找到会话: " + conversation);
            return m;
        }
        sleep(1500);
        // 定位会话输入框（textarea 优先，其次 contenteditable）
        String selector = "textarea";
        boolean hasTa = eval("!!document.querySelector('textarea')").equals("true");
        if (!hasTa) {
            selector = "[contenteditable='true']";
        }
        try {
            typeInto(selector, text);
            sleep(400);
            boolean sent = clickByText("发送", true);
            sleep(800);
            m.put("success", sent);
            m.put("message", sent ? "消息已发送" : "已输入但未找到「发送」按钮");
        } catch (Exception e) {
            m.put("success", false);
            m.put("message", "发送失败: " + e.getMessage());
        }
        return m;
    }

    // ============================ 5. 自动发货 ============================

    public List<Map<String, Object>> listPendingShipments() {
        clickByText("我的", true);
        sleep(600);
        clickByText("我卖出的", true);
        sleep(1500);
        return extractCards();
    }

    /** 对指定订单发货（填物流单号后确认）。 */
    public Map<String, Object> ship(String keyword, String logisticsNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少订单关键字");
            return m;
        }
        clickByText("我的", true);
        sleep(600);
        clickByText("我卖出的", true);
        sleep(1200);
        boolean ok = clickActionNear(keyword, "发货");
        if (!ok) {
            m.put("success", false);
            m.put("message", "未找到订单/发货按钮");
            return m;
        }
        sleep(1500);
        // 填写物流单号（如有）
        if (logisticsNo != null && !logisticsNo.isBlank()) {
            boolean filled = safeFillLogistics(logisticsNo);
            m.put("logisticsFilled", filled);
        }
        // 确认（提交 / 确定 / 确认发货）
        boolean confirmed = clickByText("确认发货", true)
                || clickByText("提交", true)
                || clickByText("确定", true);
        sleep(1200);
        m.put("success", confirmed);
        m.put("keyword", keyword);
        m.put("message", confirmed ? "已提交发货" : "已点击发货，但未能确认提交（可能需手动选择物流）");
        return m;
    }

    private boolean safeFillLogistics(String no) {
        // 尝试常见物流单号输入框
        for (String sel : new String[]{"input[placeholder*='物流']", "input[placeholder*='单号']",
                "input[placeholder*='运单']", "input[type='text']"}) {
            String exists = eval("!!document.querySelector(" + js(sel) + ")");
            if ("true".equals(exists)) {
                try {
                    typeInto(sel, no);
                    return true;
                } catch (Exception ignore) {
                }
            }
        }
        return false;
    }

    // ============================ 6. 收货验收 ============================

    public List<Map<String, Object>> listPendingReceipts() {
        clickByText("我的", true);
        sleep(600);
        clickByText("我买到的", true);
        sleep(1500);
        return extractCards();
    }

    /** 确认收货/验收指定订单。 */
    public Map<String, Object> acceptOrder(String keyword) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少订单关键字");
            return m;
        }
        clickByText("我的", true);
        sleep(600);
        clickByText("我买到的", true);
        sleep(1200);
        boolean ok = clickActionNear(keyword, "确认收货")
                || clickActionNear(keyword, "验收")
                || clickActionNear(keyword, "确认");
        sleep(1200);
        m.put("success", ok);
        m.put("keyword", keyword);
        m.put("message", ok ? "已点击确认收货/验收" : "未找到订单/确认按钮");
        return m;
    }

    // ============================ 通用提取 ============================

    private List<Map<String, Object>> extractCards() {
        List<Map<String, Object>> domCards = extractProductCardsFromDom();
        if (!domCards.isEmpty()) {
            return domCards;
        }

        String body = getBodyText();
        List<Map<String, Object>> cards = new ArrayList<>();

        java.util.regex.Matcher sellerMatcher = java.util.regex.Pattern
                .compile("Preview(.+?)(?:浙江省|江苏省|广东省|福建省|山东省|河南省|河北省|湖南省|湖北省|四川省|安徽省|江西省|辽宁省|吉林省|黑龙江省|陕西省|山西省|云南省|贵州省|广西|内蒙古|新疆|西藏|宁夏|北京|上海|天津|重庆|\\d+粉丝)")
                .matcher(body);
        if (!sellerMatcher.find()) {
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("rawText", body);
            cards.add(fb);
            return cards;
        }

        String seller = sellerMatcher.group(1).trim();
        String[] segments = body.split(java.util.regex.Pattern.quote(seller));
        java.util.regex.Pattern pricePattern = java.util.regex.Pattern.compile("[¥￥]\\s*[\\d,]+(?:\\.\\d+)?");
        Set<String> seen = new HashSet<>();
        String status = body.contains("宝贝管理") && body.contains("在售") ? "在售" : "";

        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i].replaceAll("[\\s　]+", "").trim();
            java.util.regex.Matcher pm = pricePattern.matcher(segment);
            List<String> prices = new ArrayList<>();
            int firstPriceStart = -1;
            while (pm.find()) {
                if (firstPriceStart < 0) firstPriceStart = pm.start();
                String p = pm.group().replaceAll("\\s+", "");
                if (!prices.contains(p)) prices.add(p);
            }
            if (prices.isEmpty() || firstPriceStart <= 0) continue;

            String title = segment.substring(0, firstPriceStart);
            title = title.replaceAll("^.*(?:综合|在售\\d*|已售出\\d*|筛选)", "").trim();
            title = title.replaceAll("(发闲置|消息|闲鱼号|APP反馈|客服|回顶部).*$", "").trim();
            if (title.length() < 2 || title.length() > 120) continue;
            if (title.contains("我的闲鱼") || title.contains("账户设置") || title.contains("信用及评价")) continue;

            String key = title + prices;
            if (!seen.add(key)) continue;

            String priceDisplay;
            if (prices.size() >= 2) {
                priceDisplay = prices.get(1) + "（原价 " + prices.get(0) + "）";
            } else {
                priceDisplay = prices.get(0);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("price", priceDisplay);
            item.put("status", status);
            item.put("url", "");
            item.put("detail", title);
            item.put("image", "");
            item.put("images", List.of());
            cards.add(item);
        }

        if (cards.isEmpty()) {
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("rawText", body);
            cards.add(fb);
        }
        return cards;
    }

    /** 从当前「我发布的」页面 DOM 提取商品详情、链接和图片；失败时由 bodyText 解析兜底。 */
    private List<Map<String, Object>> extractProductCardsFromDom() {
        String expr = "(function(){"
                + "function clean(s){return (s||'').replace(/\\s+/g,' ').trim();}"
                + "function abs(u){try{return u?new URL(u,location.href).href:'';}catch(e){return u||'';}}"
                + "function uniq(a){var m={},r=[];for(var i=0;i<a.length;i++){var v=a[i];if(v&&!m[v]){m[v]=1;r.push(v);}}return r;}"
                + "function bad(t){return /我的闲鱼|账户设置|信用及评价|Goofish\\.com|阿里巴巴|统一社会信用|隐私政策|用户服务协议|发闲置0消息/.test(t);}"
                + "var nodes=[].slice.call(document.querySelectorAll('a[href*=\\\"/item\\\"],a[href*=\\\"item?id=\\\"]'));"
                + "var out=[],seen={};"
                + "for(var i=0;i<nodes.length;i++){"
                + "  var a=nodes[i];"
                + "  var txt=clean(a.innerText||a.textContent||'');"
                + "  if(!/[¥￥]\\s*\\d/.test(txt)||txt.length<8||txt.length>220||bad(txt))continue;"
                + "  var prices=txt.match(/[¥￥]\\s*[\\d,.]+/g)||[];"
                + "  if(!prices.length)continue;"
                + "  var first=txt.search(/[¥￥]\\s*\\d/);"
                + "  var title=clean(txt.slice(0,first));"
                + "  title=title.replace(/^.*(?:综合|在售\\d*|已售出\\d*|筛选)/,'');"
                + "  title=title.replace(/(发闲置|消息|闲鱼号|APP反馈|客服|回顶部).*$/,'');"
                + "  title=clean(title);"
                + "  if(title.length<2||title.length>120||bad(title))continue;"
                + "  var imgs=[].slice.call(a.querySelectorAll('img')).map(function(img){return abs(img.currentSrc||img.src||img.getAttribute('src')||'');});"
                + "  imgs=uniq(imgs).filter(function(u){return u&&!/^data:image\\/svg/i.test(u)&&!/TB1LFGeKV|tps-84-60|tps-546-546/.test(u);});"
                + "  var price=prices.length>=2?prices[1].replace(/\\s/g,'')+'（原价 '+prices[0].replace(/\\s/g,'')+'）':prices[0].replace(/\\s/g,'');"
                + "  var url=abs(a.href);"
                + "  var id=(url.match(/[?&]id=([^&]+)/)||[])[1]||'';"
                + "  var key=id||title+'|'+price; if(seen[key])continue; seen[key]=1;"
                + "  out.push({id:id,title:title,price:price,status:/下架/.test(txt)?'下架':(/已售|售出/.test(txt)?'已售出':'在售'),url:url,detail:txt,image:imgs[0]||'',images:imgs});"
                + "}"
                + "return JSON.stringify(out);"
                + "})()";
        String json = eval(expr);
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) return result;
            for (JsonNode n : arr) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", n.path("title").asText(""));
                item.put("price", n.path("price").asText(""));
                item.put("status", n.path("status").asText(""));
                item.put("url", n.path("url").asText(""));
                item.put("detail", n.path("detail").asText(""));
                item.put("image", n.path("image").asText(""));
                List<String> images = new ArrayList<>();
                JsonNode imgArr = n.path("images");
                if (imgArr.isArray()) {
                    for (JsonNode img : imgArr) images.add(img.asText(""));
                }
                item.put("images", images);
                result.add(item);
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    /** 解析 JS 返回的卡片 JSON 字符串（直接 eval 返回的字符串更可靠）。 */
    private List<Map<String, Object>> parseCardJson(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", n.path("title").asText(""));
                    item.put("price", n.path("price").asText(""));
                    item.put("status", n.path("status").asText(""));
                    result.add(item);
                }
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    /** 工具：直接执行任意 JS 表达式（供前端调试用）。 */
    public String evalExpression(String expression) {
        return eval(expression);
    }
}
