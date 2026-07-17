package cn.net.rjnetwork.demo.cdp;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        clickByText("我的", true);
        sleep(1500);
        String nick = eval("(function(){var e=document.querySelector("
                + "[class*='nick'],[class*='Name'],[class*='user'],[class*='profile']);"
                + "return e?(e.innerText||e.textContent||'').trim():'';})()");
        m.put("nickname", nick);
        String body = getBodyText().replaceAll("\\s+", " ").trim();
        m.put("excerpt", body.length() > 800 ? body.substring(0, 800) : body);
        m.put("loggedIn", !body.contains("登录"));
        return m;
    }

    // ============================ 3. 商品上下架 ============================

    /** 进入「我发布的」并提取商品卡片（标题/价格/状态）。 */
    public List<Map<String, Object>> listProducts() {
        clickByText("我的", true);
        sleep(800);
        clickByText("我发布的", true);
        sleep(1500);
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

    /** 从当前页面抽取「卡片」：含 ¥ 的叶子文本视为商品/订单价格锚点。 */
    private List<Map<String, Object>> extractCards() {
        String expr = "(function(){"
                + "var out=[];"
                + "var els=document.querySelectorAll('*');"
                + "for(var i=0;i<els.length;i++){"
                + "  var e=els[i];"
                + "  if(e.children.length===0){"
                + "    var t=(e.innerText||'').trim();"
                + "    if((t.indexOf('¥')>=0||t.indexOf('￥')>=0)&&t.length<60){"
                + "      out.push(t);"
                + "    }"
                + "  }"
                + "}"
                + "return JSON.stringify(out.slice(0,60));"
                + "})()";
        JsonNode arr = evalJson(expr);
        List<Map<String, Object>> result = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("price", n.asText());
                result.add(item);
            }
        }
        if (result.isEmpty()) {
            // 退化：返回页面文本片段，便于人工核对
            Map<String, Object> fallback = new LinkedHashMap<>();
            String body = getBodyText().replaceAll("\\s+", " ").trim();
            fallback.put("rawText", body.length() > 1000 ? body.substring(0, 1000) : body);
            result.add(fallback);
        }
        return result;
    }

    /** 工具：直接执行任意 JS 表达式（供前端调试用）。 */
    public String evalExpression(String expression) {
        return eval(expression);
    }
}
