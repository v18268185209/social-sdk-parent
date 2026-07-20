package cn.net.rjnetwork.xianyu.manager.monitor.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProductApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketSnapshot;
import cn.net.rjnetwork.xianyu.manager.market.mapper.MarketSnapshotMapper;
import cn.net.rjnetwork.xianyu.manager.market.service.PriceHistoryService;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorResultMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorTaskMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorResult;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监控任务执行服务 — 搜索商品、AI 分析、存储结果
 */
@Service
public class MonitorTaskRunner {

    private static final Logger logger = LoggerFactory.getLogger(MonitorTaskRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MonitorTaskMapper taskMapper;
    private final MonitorResultMapper resultMapper;
    private final AccountMapper accountMapper;
    private final CircuitBreakerService circuitBreaker;
    private final PriceHistoryService priceHistoryService;
    private final MarketSnapshotMapper snapshotMapper;
    private final AiChatService aiChatService;

    public MonitorTaskRunner(MonitorTaskMapper taskMapper, MonitorResultMapper resultMapper,
                              AccountMapper accountMapper, CircuitBreakerService circuitBreaker,
                              PriceHistoryService priceHistoryService, MarketSnapshotMapper snapshotMapper,
                              AiChatService aiChatService) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.accountMapper = accountMapper;
        this.circuitBreaker = circuitBreaker;
        this.priceHistoryService = priceHistoryService;
        this.snapshotMapper = snapshotMapper;
        this.aiChatService = aiChatService;
    }

    @Transactional
    public void executeTask(MonitorTask task) {
        if (!circuitBreaker.allowRequest(task.getAccountId(), "MONITOR")) {
            logger.warn("任务 {} 触发熔断，跳过执行", task.getId());
            return;
        }

        XianyuAccount account = accountMapper.selectById(task.getAccountId());
        if (account == null || account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            logger.warn("任务 {} 的账号 {} 不可用", task.getId(), task.getAccountId());
            circuitBreaker.recordFailure(task.getAccountId(), "MONITOR", "账号不可用");
            task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);
            checkCircuit(task);
            taskMapper.updateById(task);
            return;
        }

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);

            // 1. 激活搜索
            productApi.activateSearch(task.getKeyword());

            // 2. 搜索商品
            JsonNode searchResult = productApi.searchProducts(task.getKeyword(), "1", "30");

            List<MonitorResult> newResults = new ArrayList<>();
            MarketSnapshot snapshot = new MarketSnapshot();
            snapshot.setTaskId(task.getId());
            snapshot.setKeyword(task.getKeyword());
            snapshot.setAccountId(task.getAccountId());
            snapshot.setRawData(searchResult != null ? searchResult.toString() : "");

            int totalResults = 0;

            if (searchResult != null && searchResult.has("data")) {
                JsonNode data = searchResult.get("data");
                JsonNode items = data.has("items") ? data.get("items") : data.has("resultList") ? data.get("resultList") : null;

                if (items != null && items.isArray()) {
                    totalResults = items.size();
                    snapshot.setTotalResults(totalResults);

                    for (JsonNode item : items) {
                        try {
                            MonitorResult result = parseItem(item, task);
                            if (result == null) continue;
                            if (passesFilter(result, task)) {
                                // AI 分析
                                if (Boolean.TRUE.equals(task.getAiEnabled()) && task.getAiModelId() != null) {
                                    analyzeWithAi(result, task);
                                }
                                result.setDeleted(0);
                                result.setCreatedAt(LocalDateTime.now());
                                resultMapper.insert(result);
                                newResults.add(result);

                                // 记录价格历史
                                PriceHistory ph = new PriceHistory();
                                ph.setKeyword(task.getKeyword());
                                ph.setItemId(result.getItemId());
                                ph.setItemTitle(result.getItemTitle());
                                ph.setPrice(result.getPrice());
                                ph.setSellerNickname(result.getSellerNickname());
                                ph.setSellerCreditScore(result.getSellerCreditScore());
                                ph.setSnapshotId(snapshot.getId());
                                priceHistoryService.batchRecordPriceHistory(task.getKeyword(), snapshot.getId(), List.of(ph));
                            }
                        } catch (Exception e) {
                            logger.warn("处理商品失败: {}", e.getMessage());
                        }
                    }
                }
            }

            snapshot.setSnapshotTime(LocalDateTime.now());
            snapshot.setDeleted(0);
            snapshotMapper.insert(snapshot);

            // 更新任务状态
            task.setLastRunAt(LocalDateTime.now());
            task.setRunCount(task.getRunCount() + 1);
            task.setConsecutiveFailures(0);
            task.setLastResultSummary(MAPPER.writeValueAsString(Map.of(
                    "totalResults", totalResults,
                    "newMatches", newResults.size(),
                    "timestamp", LocalDateTime.now().toString()
            )));
            task.setNextRunAt(LocalDateTime.now().plusMinutes(
                    task.getIntervalMinutes() != null ? task.getIntervalMinutes() : 30));
            taskMapper.updateById(task);

            circuitBreaker.recordSuccess(task.getAccountId(), "MONITOR");

        } catch (Exception e) {
            logger.error("执行任务 {} 失败", task.getId(), e);
            circuitBreaker.recordFailure(task.getAccountId(), "MONITOR", e.getMessage());
            task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);
            checkCircuit(task);
            task.setNextRunAt(LocalDateTime.now().plusMinutes(5); // 失败后 5 分钟重试
            taskMapper.updateById(task);
        }
    }

    private MonitorResult parseItem(JsonNode item, MonitorTask task) {
        String itemId = getText(item, "itemId", "id", "item_id");
        String title = getText(item, "title", "name", "itemTitle");
        if (itemId == null && title == null) return null;

        MonitorResult r = new MonitorResult();
        r.setTaskId(task.getId());
        r.setItemId(itemId != null ? itemId : UUID.randomUUID().toString());
        r.setItemTitle(title != null ? title : "未知商品");
        r.setPrice(parseDouble(item, "price", "currentPrice", "salePrice"));
        r.setImageUrl(getText(item, "imageUrl", "pic", "imgUrl", "cover"));
        r.setSellerNickname(getText(item, "sellerNickname", "sellerName", "nickname"));
        r.setSellerCreditScore(parseInt(item, "sellerCreditScore", "creditScore"));
        r.setItemUrl(getText(item, "itemUrl", "url", "detailUrl"));
        r.setNotified(false);
        return r;
    }

    private boolean passesFilter(MonitorResult result, MonitorTask task) {
        if (task.getMinPrice() != null && result.getPrice() != null && result.getPrice() < task.getMinPrice()) return false;
        if (task.getMaxPrice() != null && result.getPrice() != null && result.getPrice() > task.getMaxPrice()) return false;
        return true;
    }

    private void analyzeWithAi(MonitorResult result, MonitorTask task) {
        try {
            String prompt = task.getAiPrompt() != null ? task.getAiPrompt() :
                    "判断以下闲鱼商品是否值得购买。当前价格 %s 元，标题：%s，卖家：%s。请用 JSON 返回 {score: 0-100, reason: 推荐理由}。只返回 JSON。";
            String userMsg = String.format(prompt, result.getPrice(), result.getItemTitle(), result.getSellerNickname());

            String aiResponse = aiChatService.chat(task.getAiModelId(),
                    "你是二手商品采购专家，擅长判断性价比和风险。请用 JSON 返回 score（0-100）和 reason（简短理由）。", userMsg);

            // 简单解析
            JsonNode json = MAPPER.readTree(aiResponse);
            if (json.has("score")) result.setAiScore(json.get("score").asDouble());
            if (json.has("reason")) result.setAiReason(json.get("reason").asText());
        } catch (Exception e) {
            logger.warn("AI 分析失败: {}", e.getMessage());
        }
    }

    private void checkCircuit(MonitorTask task) {
        if (task.getConsecutiveFailures() >= 5) {
            task.setCircuitOpen(true);
            task.setCircuitOpenUntil(LocalDateTime.now().plusMinutes(30));
        }
    }

    private String getText(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) return node.get(f).asText();
        }
        return null;
    }

    private Double parseDouble(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                try { return node.get(f).asDouble(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Integer parseInt(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                try { return node.get(f).asInt(); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
