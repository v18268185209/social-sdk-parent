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
        client.navigate(url).join();
        sleep(1200);
    }

    public String getBodyText() {
        return eval("document.body?document.body.innerText:document.documentElement.innerText");
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
                + "var els=document.querySelectorAll('a,button,[role=button],div,span,li,td');"
                + "for(var i=els.length-1;i>=0;i--){"
                + "  var e=els[i];"
                + "  if(e.offsetParent===null) continue;"
                + "  var t=(e.innerText||e.textContent||'').trim();"
                + "  if(" + (contains ? "t.indexOf(txt)>=0" : "t===txt") + "){"
                + "    e.scrollIntoView({block:'center'});"
                + "    var r=e.getBoundingClientRect();"
                + "    if(r.width===0||r.height===0) continue;"
                + "    return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});"
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

    /** 截图视口，返回 base64 PNG。 */
    public String screenshotViewport() {
        try {
            JsonNode r = client.captureScreenshot().join();
            return r.get("data").asText();
        } catch (Exception e) {
            return "";
        }
    }

    /** 截图包含指定文本的元素的包围盒，返回 base64 PNG（用于登录二维码）。 */
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
            JsonNode shot = client.send("Page.captureScreenshot", p).join();
            return shot.get("data").asText();
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
        String body = getBodyText();
        boolean hasLoginButton = body.contains("登录");
        boolean loggedIn = !hasLoginButton
                && (body.contains("我发布的") || body.contains("退出登录") || body.contains("个人主页")
                || body.contains("我的闲鱼"));
        m.put("loggedIn", loggedIn);
        m.put("hasLoginButton", hasLoginButton);
        m.put("url", getUrl());
        m.put("title", eval("document.title"));
        return m;
    }

    /** 打开登录二维码并返回 base64 PNG（优先截取二维码区域）。 */
    public String getLoginQrBase64() {
        navigate(xianyuUrl);
        sleep(500);
        if (clickByText("登录", true)) {
            sleep(1500);
        }
        waitForText("扫码", 5000);
        waitForText("二维码", 5000);
        waitForText("登录", 5000);
        String b64 = screenshotElementByText("扫码登录");
        if (b64 == null || b64.isEmpty()) {
            b64 = screenshotElementByText("二维码");
        }
        if (b64 == null || b64.isEmpty()) {
            b64 = screenshotElementByText("扫码");
        }
        if (b64 == null || b64.isEmpty()) {
            b64 = screenshotViewport();
        }
        return b64;
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
