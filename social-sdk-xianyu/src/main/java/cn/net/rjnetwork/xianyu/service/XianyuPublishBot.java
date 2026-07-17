package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import cn.net.rjnetwork.xianyu.model.PublishItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 闲鱼「发闲置」商品发布能力。
 *
 * <p>闲鱼发布页是 SPA，CSS 类名带构建 hash 不稳定（如 {@code title-container--VX6tnK0J}），
 * 因此本类采用「可见文本/占位符」策略定位字段：先滚动到含目标文案的可编辑元素，
 * 再用 {@link XianyuCdpBot#typeInto} 填写，最后按可见文本点击「发布」按钮。</p>
 *
 * <p>草稿与正式发布共用同一流程：{@link PublishItem#isPublish()} = false 时仅填写不提交，
 * true 时在填写完成后触发「发布」按钮并校验页面跳回管理页/出现成功提示。</p>
 *
 * <p>图片上传采用最稳妥的双轨策略：①若页面暴露了 {@code input[type=file]}，
 * 用 CDP {@code DOM.setFileInputFiles} 注入本地路径；②否则在前端注入一个可拖拽的
 * drop area，并直接 fetch 图片字节走闲鱼自有上传接口（后续迭代补）。</p>
 */
public class XianyuPublishBot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CdpClient client;
    private final String xianyuUrl;
    private final XianyuCdpBot bot;

    public XianyuPublishBot(CdpClient client, String xianyuUrl, XianyuCdpBot bot) {
        this.client = client;
        this.xianyuUrl = (xianyuUrl == null ? "https://www.goofish.com" : xianyuUrl);
        this.bot = bot;
    }

    // ============================ 进入发布表单 ============================

    /**
     * 导航到发布页。闲鱼是 SPA，{@code /publish} URL 不变但内容会被首页路由替换，
     * 因此需要先到首页再点「发布」入口进入真正的发布表单。
     * 返回是否成功进入发布表单（页面出现「发闲置」/「发布」相关文案）。
     */
    public boolean enterPublishForm() {
        bot.navigate(xianyuUrl);
        bot.sleep(2000);
        // 首页右上角「发布」入口
        boolean clicked = bot.clickByText("发布", true);
        if (!clicked) {
            clicked = bot.clickByText("发闲置", true);
        }
        bot.sleep(2500);
        return isInPublishForm();
    }

    /** 判定是否已进入发布表单（页面出现宝贝图片/描述/价格等核心字段文案）。 */
    public boolean isInPublishForm() {
        String body = bot.getBodyText();
        return body != null && (body.contains("宝贝图片") || body.contains("基础信息")
                || body.contains("发闲置") || body.contains("描述"));
    }

    // ============================ 草稿/发布 ============================

    /**
     * 填写发布表单。{@link PublishItem#isPublish()} = true 时在填写后点击「发布」按钮。
     * 返回填写结果与关键字段命中情况。
     */
    public Map<String, Object> fillAndPublish(PublishItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (item == null || item.getTitle() == null || item.getTitle().isBlank()) {
            result.put("success", false);
            result.put("message", "标题必填");
            return result;
        }
        if (!isInPublishForm()) {
            boolean entered = enterPublishForm();
            if (!entered) {
                result.put("success", false);
                result.put("message", "无法进入发布表单");
                return result;
            }
        }

        boolean titleOk = fillTitle(item.getTitle());
        boolean descOk = item.getDescription() == null || item.getDescription().isBlank()
                || fillDescription(item.getDescription());
        boolean priceOk = item.getPrice() == null || fillPrice(item.getPrice());
        boolean imgOk = item.getImages() == null || item.getImages().isEmpty()
                || fillImages(item.getImages());
        boolean catOk = item.getCategory() == null || item.getCategory().isBlank()
                || fillCategory(item.getCategory());
        boolean stockOk = item.getStock() == null || fillStock(item.getStock());

        result.put("title", titleOk);
        result.put("description", descOk);
        result.put("price", priceOk);
        result.put("images", imgOk);
        result.put("category", catOk);
        result.put("stock", stockOk);

        if (!item.isPublish()) {
            result.put("success", true);
            result.put("message", "草稿已填写（未提交）");
            result.put("screenshot", bot.screenshotViewport());
            return result;
        }

        // 正式发布：点击「发布」按钮并校验
        boolean published = clickPublish();
        bot.sleep(2500);
        String keyword = item.getSuccessKeyword() != null && !item.getSuccessKeyword().isBlank()
                ? item.getSuccessKeyword() : item.getTitle();
        boolean verified = bot.waitForText(keyword, 8000);
        result.put("publishClicked", published);
        result.put("verified", verified);
        result.put("success", published && verified);
        result.put("message", verified ? "发布成功" : "已点击发布但未校验到成功提示");
        result.put("screenshot", bot.screenshotViewport());
        return result;
    }

    /** 仅草稿（不点击发布按钮）。 */
    public Map<String, Object> saveDraft(PublishItem item) {
        if (item == null) return draft(false);
        item.setPublish(false);
        return fillAndPublish(item);
    }

    private Map<String, Object> draft(boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", ok);
        return m;
    }

    // ============================ 字段填写 ============================

    /**
     * 填标题。闲鱼标题为单行 input，用占位符/相邻「标题」文案定位。
     * 策略：找可见的可编辑元素，其 nearest 祖先含「标题」或自身 placeholder 含「标题」。
     */
    public boolean fillTitle(String title) {
        return fillEditableNear("标题", title, 30);
    }

    /** �填描述/正文。描述为富文本编辑器（contenteditable）或 textarea。 */
    public boolean fillDescription(String description) {
        return fillEditableNear("描述", description, 2000);
    }

    /** �价格。价格 input 通常在「价格」文案附近。 */
    public boolean fillPrice(double price) {
        return fillEditableNear("价格", String.valueOf(price), 20);
    }

    /** �库存。 */
    public boolean fillStock(int stock) {
        return fillEditableNear("库存", String.valueOf(stock), 10);
    }

    /** 选分类（点击「分类」入口，再点击分类名）。 */
    public boolean fillCategory(String category) {
        boolean opened = bot.clickByText("分类", true);
        if (!opened) return false;
        bot.sleep(800);
        return bot.clickByText(category, true);
    }

    /**
     * 上传图片。当前实现：点击「宝贝图片」/「添加图片」入口，若页面暴露了
     * {@code input[type=file]} 则用 CDP DOM.setFileInputFiles 注入本地路径。
     * URL 形式的图片需先下载到本地临时文件（后续迭代补）。
     */
    public boolean fillImages(List<String> localPaths) {
        if (localPaths == null || localPaths.isEmpty()) return false;
        boolean opened = bot.clickByText("添加图片", true);
        if (!opened) opened = bot.clickByText("宝贝图片", true);
        bot.sleep(800);
        return setFileInputFiles(localPaths);
    }

    /** 触发「发布」按钮。 */
    public boolean clickPublish() {
        return bot.clickByText("发布", true);
    }

    // ============================ 核心：按可见文案定位可编辑元素 ============================

    /**
     * 在含 {@code labelText}（如「标题」「价格」）的容器附近找可见可编辑元素，
     * 滚动到视窗、聚焦、清空、输入文本，触发 input/change 事件。
     * 返回是否成功输入。
     */
    private boolean fillEditableNear(String labelText, String text, int maxLength) {
        // 1) JS 端定位：找文本包含 labelText 的祖先容器内第一个可见可编辑元素
        String locator = "(function(){"
                + "var kw=" + bot.js(labelText) + ";"
                + "var editable=document.querySelectorAll('input:not([type=hidden]):not([type=radio]):not([type=checkbox]),textarea,[contenteditable=true]');"
                + "var best=null,bestScore=1e9;"
                + "for(var i=0;i<editable.length;i++){"
                + "  var e=editable[i];"
                + "  if(e.offsetParent===null) continue;"
                + "  var r=e.getBoundingClientRect();"
                + "  if(r.width===0||r.height===0) continue;"
                + "  var score=1e6;"
                + "  var p=e.parentElement, hops=0;"
                + "  while(p&&hops<6){"
                + "    var pt=(p.innerText||'').trim();"
                + "    if(pt.indexOf(kw)>=0){ score=hops; break; }"
                + "    p=p.parentElement; hops++;"
                + "  }"
                + "  if(score>=1e6) continue;"
                + "  var ph=(e.placeholder||e.getAttribute('data-placeholder')||'');"
                + "  if(ph.indexOf(kw)>=0) score=Math.min(score,0);"
                + "  var r2=e.getBoundingClientRect();"
                + "  score += Math.abs(r2.top + r2.height/2 - (window.innerHeight||0)/2)/200;"
                + "  if(score<bestScore){bestScore=score;best={node:e,x:r2.left+r2.width/2,y:r2.top+r2.height/2};}"
                + "}"
                + "if(!best) return null;"
                + "best.node.scrollIntoView({block:'center'});"
                + "var r3=best.node.getBoundingClientRect();"
                + "return JSON.stringify({x:r3.left+r3.width/2,y:r3.top+r3.height/2,tag:best.node.tagName});"
                + "})()";
        String res = bot.eval(locator);
        if (res == null || res.equals("null")) return false;
        try {
            JsonNode n = MAPPER.readTree(res);
            double x = n.get("x").asDouble();
            double y = n.get("y").asDouble();
            String tag = n.get("tag").asText();
            // 点击聚焦
            client.click(x, y);
            bot.sleep(300);
            // 清空：选中全删
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyDown").put("key", "ControlLeft")).get(3, TimeUnit.SECONDS);
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyDown").put("key", "A").put("modifiers", 2)).get(3, TimeUnit.SECONDS);
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyUp").put("key", "A").put("modifiers", 2)).get(3, TimeUnit.SECONDS);
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyUp").put("key", "ControlLeft")).get(3, TimeUnit.SECONDS);
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyDown").put("key", "Backspace")).get(3, TimeUnit.SECONDS);
            client.send("Input.dispatchKeyEvent", jsonNode("type", "keyUp").put("key", "Backspace")).get(3, TimeUnit.SECONDS);
            // 输入文本（按字符插以兼容中文）
            String val = text.length() > maxLength ? text.substring(0, maxLength) : text;
            if ("INPUT".equals(tag) || "TEXTAREA".equals(tag)) {
                client.type(val);
            } else {
                // contenteditable：用 insertText
                client.send("Input.insertText", MAPPER.createObjectNode().put("text", val)).get(3, TimeUnit.SECONDS);
            }
            bot.sleep(400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode jsonNode(String k, String v) {
        return MAPPER.createObjectNode().put(k, v);
    }

    /**
     * 定位页面上的 {@code input[type=file]} 并注入本地文件路径。
     * 使用 CDP DOM.requestNode + DOM.setFileInputFiles（需先 resolveNodeId）。
     */
    private boolean setFileInputFiles(List<String> localPaths) {
        try {
            // 找 file input 的 backendNodeId（通过 Runtime 拿不到 nodeId，需走 DOM.getDocument + querySelector）
            JsonNode doc = client.getDocument();
            int rootId = doc.get("root").get("nodeId").asInt();
            JsonNode q = client.querySelector(rootId, "input[type=file]");
            int nodeId = q.has("nodeId") ? q.get("nodeId").asInt() : 0;
            if (nodeId == 0) return false;
            com.fasterxml.jackson.databind.node.ObjectNode p = MAPPER.createObjectNode();
            p.put("nodeId", nodeId);
            com.fasterxml.jackson.databind.node.ArrayNode files = p.putArray("files");
            for (String path : localPaths) {
                files.addObject().put("path", path);
            }
            client.send("DOM.setFileInputFiles", p).get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
