package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import cn.net.rjnetwork.xianyu.model.PublishItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼商品能力聚合：发布/编辑/上架/下架/批量/搜索/浏览/点赞。
 *
 * <p>发布与编辑共用 {@link XianyuPublishBot}（编辑=先进入「我发布的」找到商品点「编辑」
 * 再走填写流程）；上架/下架复用 {@link XianyuCdpBot#upShelf}/{@link XianyuCdpBot#downShelf}；
 * 搜索/浏览/点赞为本类新增能力，基于 SPA 内点击跳转 + 可见文本定位。</p>
 *
 * <p>每账号独立实例，由 {@link XianyuSdk.XianyuAccount} 懒加载。</p>
 */
public class XianyuProductBot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CdpClient client;
    private final XianyuCdpBot bot;
    private final XianyuPublishBot publish;

    public XianyuProductBot(CdpClient client, String xianyuUrl, XianyuCdpBot bot, XianyuPublishBot publish) {
        this.client = client;
        this.bot = bot;
        this.publish = publish;
    }

    // ---------- 发布/编辑 ----------

    /** 发布草稿（仅填写不提交）。 */
    public Map<String, Object> saveDraft(PublishItem item) {
        return publish.saveDraft(item);
    }

    /** 正式发布（填写并点击「发布」按钮，校验成功关键字）。 */
    public Map<String, Object> publish(PublishItem item) {
        item.setPublish(true);
        return publish.fillAndPublish(item);
    }

    /**
     * 编辑已发布商品。
     * 进入「我发布的」→ 点击匹配 keyword 的商品上的「编辑」按钮 → 进入编辑表单 → 按新参数填写 → 保存。
     * {@code item} 中 null 字段跳过（保留原值），仅填非 null 字段。
     */
    public Map<String, Object> edit(String keyword, PublishItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少商品关键字");
            return m;
        }
        // 进入「我发布的」
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我发布的", true);
        bot.sleep(1500);
        // 点击「编辑」按钮（在含 keyword 的商品卡片附近）
        boolean opened = bot.clickActionNear(keyword, "编辑")
                || bot.clickActionNear(keyword, "修改");
        if (!opened) {
            m.put("success", false);
            m.put("message", "未找到商品/编辑入口: " + keyword);
            return m;
        }
        bot.sleep(2000);
        // 等待编辑表单加载（复用发布表单检测）
        boolean inForm = false;
        for (int i = 0; i < 8; i++) {
            if (publish.isInPublishForm()) { inForm = true; break; }
            bot.sleep(800);
        }
        if (!inForm) {
            m.put("success", false);
            m.put("message", "进入编辑表单超时");
            return m;
        }
        // 按非 null 字段填写（null 跳过，保留原值）
        boolean any = false;
        if (item.getTitle() != null) { publish.fillTitle(item.getTitle()); any = true; }
        if (item.getDescription() != null) { publish.fillDescription(item.getDescription()); any = true; }
        if (item.getPrice() != null) { publish.fillPrice(item.getPrice()); any = true; }
        if (item.getStock() != null) { publish.fillStock(item.getStock()); any = true; }
        if (item.getCategory() != null && !item.getCategory().isBlank()) { publish.fillCategory(item.getCategory()); any = true; }
        if (item.getImages() != null && !item.getImages().isEmpty()) { publish.fillImages(item.getImages()); any = true; }
        bot.sleep(500);
        // 保存（点击「保存」/「确定」/「发布」）
        boolean saved = bot.clickByText("保存", true)
                || bot.clickByText("确定", true)
                || bot.clickByText("发布", true);
        bot.sleep(2000);
        m.put("success", saved);
        m.put("keyword", keyword);
        m.put("fieldsUpdated", any);
        m.put("message", saved ? "编辑已保存" : "未找到保存按钮");
        m.put("screenshot", bot.screenshotViewport());
        return m;
    }

    // ---------- 上架/下架/批量 ----------

    public Map<String, Object> upShelf(String keyword) { return bot.upShelf(keyword); }
    public Map<String, Object> downShelf(String keyword) { return bot.downShelf(keyword); }

    /** 列出当前在售商品（复用 bot）。 */
    public List<Map<String, Object>> list() { return bot.listProducts(); }

    /**
     * 批量上架/下架：对 {@code keywords} 列表中每个商品执行 {@code action}（"上架"或"下架"）。
     * 返回每个商品的执行结果。
     */
    public List<Map<String, Object>> batchShelf(List<String> keywords, String action) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (keywords == null) return out;
        for (String kw : keywords) {
            Map<String, Object> r = "上架".equals(action) ? bot.upShelf(kw) : bot.downShelf(kw);
            out.add(r);
            bot.sleep(800);
        }
        return out;
    }

    // ---------- 搜索 ----------

    /**
     * 搜索商品。
     * 进入首页 → 找搜索框输入 keyword → 回车触发搜索 → 提取结果列表（标题/价格/卖家/URL/图片）。
     */
    public Map<String, Object> search(String keyword) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少搜索关键字");
            return m;
        }
        bot.navigate("https://www.goofish.com");
        bot.sleep(2500);
        // 定位搜索框：优先找 placeholder 含「搜索」的 input，否则找首个可见 text input
        String focus = "(function(){"
                + "var inputs=document.querySelectorAll('input[type=text],input[type=search],input:not([type])');"
                + "var best=null,bestScore=1e6;"
                + "for(var i=0;i<inputs.length;i++){"
                + "  var e=inputs[i];if(e.offsetParent===null) continue;"
                + "  var ph=(e.placeholder||e.getAttribute('data-placeholder')||'').toLowerCase();"
                + "  var score=ph.indexOf('搜索')>=0?0:ph.indexOf('search')>=0?1:5;"
                + "  if(score<bestScore){bestScore=score;best=e;}"
                + "}"
                + "if(!best) return null;"
                + "best.scrollIntoView({block:'center'});"
                + "best.focus();"
                + "var r=best.getBoundingClientRect();"
                + "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,tag:'INPUT'});"
                + "})()";
        String res = bot.eval(focus);
        if (res == null || res.equals("null")) {
            m.put("success", false);
            m.put("message", "未找到搜索框");
            return m;
        }
        try {
            JsonNode n = MAPPER.readTree(res);
            client.click(n.get("x").asDouble(), n.get("y").asDouble());
            bot.sleep(400);
            // 清空 + 输入（JS 端设 value + dispatch input，兼容 React/Vue 受控组件）
            bot.eval("(function(){var el=document.activeElement;if(!el)return;"
                    + "el.value=" + bot.js(keyword) + ";"
                    + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "el.dispatchEvent(new Event('change',{bubbles:true}));})()");
            bot.sleep(500);
            // 回车触发搜索（JS 端提交表单或按键事件，避免 client.send 阻塞）
            bot.eval("(function(){var el=document.activeElement;if(!el)return;"
                    + "var kc=13;"
                    + "el.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',code:'Enter',keyCode:kc,which:kc}));"
                    + "el.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',code:'Enter',keyCode:kc,which:kc}));"
                    + "if(el.form){el.form.submit();}else{el.dispatchEvent(new Event('submit',{bubbles:true}));}})()");
            bot.sleep(3000);
            // 提取搜索结果（复用商品卡片提取逻辑：a[href*="/item"] 节点）
            List<Map<String, Object>> results = extractSearchResults();
            m.put("success", true);
            m.put("keyword", keyword);
            m.put("count", results.size());
            m.put("results", results);
            m.put("screenshot", bot.screenshotViewport());
            return m;
        } catch (Exception e) {
            m.put("success", false);
            m.put("message", "搜索失败: " + e.getMessage());
            return m;
        }
    }

    /** 提取当前页面搜索结果卡片（a[href*="/item"] 节点 → 标题/价格/卖家/URL/图片）。 */
    private List<Map<String, Object>> extractSearchResults() {
        String expr = "(function(){"
                + "var out=[];var links=document.querySelectorAll('a[href*=\"/item\"]');"
                + "for(var i=0;i<links.length&&i<30;i++){"
                + "  var a=links[i];"
                + "  var r=a.getBoundingClientRect();"
                + "  if(r.width===0||r.height===0) continue;"
                + "  var imgs=a.querySelectorAll('img');var imgArr=[];"
                + "  for(var j=0;j<imgs.length;j++){var s=imgs[j].src;if(s) imgArr.push(s);}"
                + "  out.push({title:(a.innerText||'').trim().substring(0,80),url:a.href,images:imgArr});"
                + "}"
                + "return JSON.stringify(out);})()";
        String res = bot.eval(expr);
        List<Map<String, Object>> out = new ArrayList<>();
        if (res == null || res.equals("null")) return out;
        try {
            JsonNode arr = MAPPER.readTree(res);
            for (JsonNode n : arr) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", n.path("title").asText(""));
                item.put("url", n.path("url").asText(""));
                List<String> imgs = new ArrayList<>();
                JsonNode imgArr = n.path("images");
                if (imgArr.isArray()) for (JsonNode im : imgArr) imgs.add(im.asText());
                item.put("images", imgs);
                out.add(item);
            }
        } catch (Exception ignore) {}
        return out;
    }

    // ---------- 浏览/点赞 ----------

    /**
     * 浏览指定商品详情页（进入商品页 → 滚动到底 → 截图）。
     * 用于「自动浏览商品」场景，模拟真实用户访问提升商品权重。
     * @param itemUrl 商品详情 URL（如 https://www.goofish.com/item?id=xxx）
     * @param scrollSteps 滚动步数（每步 800ms，模拟真实滚动）
     */
    public Map<String, Object> visit(String itemUrl, int scrollSteps) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (itemUrl == null || itemUrl.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少商品 URL");
            return m;
        }
        bot.navigate(itemUrl);
        bot.sleep(3000);
        int steps = scrollSteps > 0 ? scrollSteps : 3;
        for (int i = 0; i < steps; i++) {
            bot.eval("window.scrollBy(0, " + (300 + i * 200) + ")");
            bot.sleep(800);
        }
        // 提取详情页关键信息
        String title = bot.eval("document.title");
        m.put("success", true);
        m.put("url", itemUrl);
        m.put("page", title != null ? title.replace("\"", "") : "");
        m.put("scrolled", steps);
        m.put("screenshot", bot.screenshotViewport());
        return m;
    }

    /**
     * 对指定商品点赞（点击「我想要」/「超赞」按钮）。
     * 闲鱼无传统「点赞」，「我想要」/「超赞」等价于点赞行为。
     * @param itemUrl 商品详情 URL
     */
    public Map<String, Object> like(String itemUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        bot.navigate(itemUrl);
        bot.sleep(3000);
        boolean clicked = bot.clickByText("我想要", true)
                || bot.clickByText("超赞", true)
                || bot.clickByText("收藏", true)
                || bot.clickByText("关注", true);
        bot.sleep(1200);
        m.put("success", clicked);
        m.put("url", itemUrl);
        m.put("message", clicked ? "已点赞/收藏" : "未找到点赞按钮");
        m.put("screenshot", bot.screenshotViewport());
        return m;
    }

    /**
     * 自动浏览并点赞搜索结果中的商品（批量养号/提升权重场景）。
     * @param keyword 搜索关键字
     * @param maxCount 最多浏览/点赞的数量
     */
    public Map<String, Object> browseAndLike(String keyword, int maxCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> sr = search(keyword);
        if (!Boolean.TRUE.equals(sr.get("success"))) {
            return sr;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) sr.get("results");
        int visited = 0, liked = 0;
        int limit = maxCount > 0 ? Math.min(maxCount, results.size()) : Math.min(results.size(), 5);
        for (int i = 0; i < limit; i++) {
            String url = String.valueOf(results.get(i).get("url"));
            if (url == null || url.isBlank()) continue;
            try {
                visit(url, 3);
                visited++;
                like(url);
                liked++;
                bot.sleep(1500);
            } catch (Exception ignore) {}
        }
        m.put("success", true);
        m.put("keyword", keyword);
        m.put("searchCount", results.size());
        m.put("visited", visited);
        m.put("liked", liked);
        m.put("message", "已浏览 " + visited + " 个商品，点赞 " + liked + " 个");
        return m;
    }
}
