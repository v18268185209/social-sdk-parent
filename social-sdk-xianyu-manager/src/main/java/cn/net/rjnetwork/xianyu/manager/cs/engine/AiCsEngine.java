^package cn.net.rjnetwork.xianyu.manager.cs.engine;

import cn.net.rjnetwork.core.ai.model.AiMessage;
import cn.net.rjnetwork.core.ai.model.AiRequest;
import cn.net.rjnetwork.core.ai.model.AiResponse;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsKnowledge;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsMessage;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsPolicy;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsSession;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsMessageMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsSessionMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsKnowledgeMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsPolicyMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 客服引擎（意图识别 + 自动回复 + 议价策略）
 */
@Component
public class AiCsEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 意图分类正则（轻量级规则识别，兜底用） */
    private static final Map<String, List<Pattern>> INTENT_PATTERNS = new LinkedHashMap<>();

    static {
        INTENT_PATTERNS.put("PRICE_NEGOTIATION", List.of(
                Pattern.compile("(便宜|降价|优惠|打折|最低|能少|砍价|让价|再少|折扣|实惠|划算)"),
                Pattern.compile("(多少\\s*钱|什么价|报个价|给个价|最低价|底价)")
        ));
        INTENT_PATTERNS.put("PRODUCT_INQUIRY", List.of(
                Pattern.compile("(有货|还在|还有|库存|成色|几成新|配件|原装|电池|保修|颜色|内存|容量)"),
                Pattern.compile("(真的吗|正品|假货|高仿|二手|几手)")
        ));
        INTENT_PATTERNS.put("LOGISTICS", List.of(
                Pattern.compile("(发货|快递|物流|单号|签收|寄出|多久到|几天到|包邮|运费)"),
                Pattern.compile("(什么时候发|发什么快递|能发|寄)")
        ));
        INTENT_PATTERNS.put("AFTERSALES", List.of(
                Pattern.compile("(退货|退款|换货|售后|质量问题|坏了|维修|保修|维权|投诉|举报)"),
                Pattern.compile("(不想要|不买了|取消|退)")
        ));
        INTENT_PATTERNS.put("PURCHASE", List.of(
                Pattern.compile("(拍下|下单|购买|买|要了|想要|帮我留|留一个)"),
                Pattern.compile("(怎么买|怎么拍|链接)")
        ));
    }

    private final AiChatService chatService;
    private final AiCsMessageMapper messageMapper;
    private final AiCsSessionMapper sessionMapper;
    private final AiCsKnowledgeMapper knowledgeMapper;
    private final AiCsPolicyMapper policyMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    public AiCsEngine(AiChatService chatService, AiCsMessageMapper messageMapper,
                      AiCsSessionMapper sessionMapper, AiCsKnowledgeMapper knowledgeMapper,
                      AiCsPolicyMapper policyMapper, ProductMapper productMapper,
                      OrderMapper orderMapper) {
        this.chatService = chatService;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.policyMapper = policyMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
    }

    // ======================================================================
    // 意图识别
    // ======================================================================

    /**
     * 识别买家消息意图（规则 + AI 双路识别）
     * 返回意图 + 置信度
     */
    public IntentResult recognizeIntent(String message, Long modelId) {
        // 1. 先走规则匹配（快、低成本）
        IntentResult ruleResult = matchByRules(message);
        if (ruleResult.confidence >= 0.8) {
            return ruleResult;
        }

        // 2. 规则不确定时，走 AI 识别
        if (modelId != null) {
            try {
                IntentResult aiResult = recognizeByAi(message, modelId);
                if (aiResult.confidence >= ruleResult.confidence) {
                    return aiResult;
                }
            } catch (Exception e) {
                System.err.println("[AiCsEngine] AI intent recognition failed: " + e.getMessage());
            }
        }

        return ruleResult;
    }

    private IntentResult matchByRules(String message) {
        if (message == null || message.isBlank()) {
            return new IntentResult("CHAT", 0.3);
        }
        for (Map.Entry<String, List<Pattern>> entry : INTENT_PATTERNS.entrySet()) {
            for (Pattern p : entry.getValue()) {
                if (p.matcher(message).find()) {
                    return new IntentResult(entry.getKey(), 0.85);
                }
            }
        }
        return new IntentResult("CHAT", 0.5);
    }

    private IntentResult recognizeByAi(String message, Long modelId) {
        String systemPrompt = """
                你是一个意图识别器。根据买家消息，判断属于以下哪种意图：
                - PRICE_NEGOTIATION: 议价、砍价、问最低价
                - PRODUCT_INQUIRY: 商品咨询（成色、配件、保修等）
                - LOGISTICS: 物流查询（发货、快递、单号等）
                - AFTERSALES: 售后（退货、退款、质量问题等）
                - PURCHASE: 购买意向（拍下、下单等）
                - CHAT: 闲聊、打招呼、无关内容

                只返回 JSON：{"intent":"xxx","confidence":0.95}
                """;

        try {
            String raw = chatService.chat(modelId, systemPrompt, message);
            JsonNode node = MAPPER.readTree(raw);
            String intent = node.path("intent").asText("CHAT");
            double confidence = node.path("confidence").asDouble(0.5);
            return new IntentResult(intent, confidence);
        } catch (Exception e) {
            return new IntentResult("CHAT", 0.3);
        }
    }

    // ======================================================================
    // 自动回复
    // ======================================================================

    /**
     * 处理买家消息：识别意图 → 生成回复 → 保存消息
     * @return AI 生成的回复（待运营确认 or 自动发送）
     */
    public AiCsMessage handleIncomingMessage(Long accountId, String buyerId, String buyerNickname,
                                            String messageContent, Long modelId) {
        // 1. 找到或创建会话
        AiCsSession session = findOrCreateSession(accountId, buyerId, buyerNickname);

        // 2. 保存买家消息
        AiCsMessage incomingMsg = new AiCsMessage();
        incomingMsg.setSessionId(session.getId());
        incomingMsg.setDirection("INCOMING");
        incomingMsg.setContent(messageContent);
        incomingMsg.setSentBy("HUMAN");
        incomingMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(incomingMsg);

        // 3. 识别意图
        IntentResult intent = recognizeIntent(messageContent, modelId);
        incomingMsg.setIntent(intent.intent);
        incomingMsg.setIntentConfidence(intent.confidence);
        messageMapper.updateById(incomingMsg);

        // 4. 根据意图生成回复
        AiCsPolicy policy = getPolicy(accountId);
        String replyContent;

        switch (intent.intent) {
            case "PRICE_NEGOTIATION":
                replyContent = handlePriceNegotiation(session, messageContent, policy, modelId);
                break;
            case "PRODUCT_INQUIRY":
                replyContent = handleProductInquiry(session, messageContent, policy, modelId);
                break;
            case "LOGISTICS":
                replyContent = handleLogisticsInquiry(session, messageContent, policy, modelId);
                break;
            case "AFTERSALES":
                replyContent = handleAftersales(session, messageContent, policy, modelId);
                break;
            case "PURCHASE":
                replyContent = handlePurchaseIntention(session, messageContent, policy, modelId);
                break;
            default:
                replyContent = handleChat(session, messageContent, policy, modelId);
        }

        // 5. 保存 AI 回复
        AiCsMessage replyMsg = new AiCsMessage();
        replyMsg.setSessionId(session.getId());
        replyMsg.setDirection("OUTGOING");
        replyMsg.setContent(replyContent);
        replyMsg.setIntent(intent.intent);
        replyMsg.setAiGenerated(true);
        replyMsg.setSentBy(policy != null && "AUTO".equals(policy.getMode()) ? "AUTO" : "AI_ASSIST");
        replyMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(replyMsg);

        // 6. 更新会话时间
        session.setLastMessageAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        return replyMsg;
    }

    // ======================================================================
    // 各意图处理
    // ======================================================================

    private String handlePriceNegotiation(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        // 1. 先查知识库
        String kbAnswer = searchKnowledge(session.getAccountId(), session.getProductId(), "PRICE", message);
        if (kbAnswer != null) return kbAnswer;

        // 2. 查商品信息
        XianyuProduct product = session.getProductId() != null
                ? productMapper.selectById(session.getProductId()) : null;

        if (product == null) {
            return "亲，看中哪款可以告诉我，我帮你查价~";
        }

        // 3. 议价策略：计算可降空间
        BigDecimal currentPrice = product.getPrice();
        BigDecimal originalPrice = product.getOriginalPrice() != null ? product.getOriginalPrice() : currentPrice;
        double floorPct = policy != null && policy.getPriceFloorPct() != null ? policy.getPriceFloorPct() : 0.80;
        double stepPct = policy != null && policy.getPriceStepPct() != null ? policy.getPriceStepPct() : 0.05;
        int maxSteps = policy != null && policy.getMaxDiscountSteps() != null ? policy.getMaxDiscountSteps() : 3;

        BigDecimal floorPrice = currentPrice.multiply(BigDecimal.valueOf(floorPct)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal stepAmount = currentPrice.multiply(BigDecimal.valueOf(stepPct)).setScale(0, RoundingMode.HALF_UP);

        // 4. 用 AI 生成议价回复
        if (modelId != null) {
            String systemPrompt = String.format("""
                    你是闲鱼卖家客服。买家正在议价。
                    商品：%s
                    当前售价：%s 元
                    原价：%s 元
                    底价：%s 元（不能低于这个价格）
                    每次可降：%s 元
                    最多降 %d 次
                    话术风格：%s

                    要求：
                    1. 友好但坚定，不要轻易让到底价
                    2. 可以强调商品卖点（成色、配件、保修）
                    3. 适当给出小优惠（降 %s 元）引导成交
                    4. 控制在 50 字以内
                    """,
                    product.getTitle(), currentPrice, originalPrice, floorPrice, stepAmount, maxSteps,
                    policy != null && policy.getTone() != null ? policy.getTone() : "FRIENDLY",
                    stepAmount);

            try {
                String aiReply = chatService.chat(modelId, systemPrompt, message);
                if (aiReply != null && !aiReply.isBlank()) return aiReply;
            } catch (Exception e) {
                System.err.println("[AiCsEngine] AI price negotiation failed: " + e.getMessage());
            }
        }

        // 5. 兜底话术
        return String.format("亲，这款 %s 已经很低了，成色很好。诚心想要的话可以再降 %s 元，%s 元拿走~",
                product.getTitle(), stepAmount, currentPrice.subtract(stepAmount));
    }

    private String handleProductInquiry(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        String kbAnswer = searchKnowledge(session.getAccountId(), session.getProductId(), "PRODUCT", message);
        if (kbAnswer != null) return kbAnswer;

        XianyuProduct product = session.getProductId() != null
                ? productMapper.selectById(session.getProductId()) : null;

        if (product == null) return "亲，请问看中哪款了？发个链接我帮你查~";

        if (modelId != null) {
            String systemPrompt = String.format("""
                    你是闲鱼卖家客服。买家在咨询商品信息。
                    商品：%s
                    描述：%s
                    成色/状态：%s
                    话术风格：%s

                    要求：如实回答，突出卖点，引导成交，50 字以内。
                    """,
                    product.getTitle(), product.getDescription(),
                    product.getStatus(), policy != null && policy.getTone() != null ? policy.getTone() : "FRIENDLY");

            try {
                String aiReply = chatService.chat(modelId, systemPrompt, message);
                if (aiReply != null && !aiReply.isBlank()) return aiReply;
            } catch (Exception e) {
                // ignore
            }
        }

        return String.format("亲，%s 成色很好，%s，支持验机，放心拍~",
                product.getTitle(),
                product.getDetailUrl() != null ? "详情看描述" : "原装正品");
    }

    private String handleLogisticsInquiry(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        String kbAnswer = searchKnowledge(session.getAccountId(), session.getOrderId(), "SHIPPING", message);
        if (kbAnswer != null) return kbAnswer;

        XianyuOrder order = session.getOrderId() != null
                ? orderMapper.selectById(session.getOrderId()) : null;

        if (order != null && order.getTrackingNo() != null) {
            return String.format("亲，已发货，快递单号：%s，可以查物流了~", order.getTrackingNo());
        }

        return "亲，拍下后 24 小时内发货，发顺丰/京东，放心~";
    }

    private String handleAftersales(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        // 售后转人工
        return "亲，这个问题我帮您转接人工客服，稍等一下~";
    }

    private String handlePurchaseIntention(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        XianyuProduct product = session.getProductId() != null
                ? productMapper.selectById(session.getProductId()) : null;

        if (product != null) {
            return String.format("亲，%s 有货，直接拍下就行，%s 元，包邮~",
                    product.getTitle(), product.getPrice());
        }
        return "亲，看中哪款直接拍下就行，有货的~";
    }

    private String handleChat(AiCsSession session, String message, AiCsPolicy policy, Long modelId) {
        String kbAnswer = searchKnowledge(session.getAccountId(), null, "GENERAL", message);
        if (kbAnswer != null) return kbAnswer;

        if (modelId != null) {
            String systemPrompt = "你是闲鱼卖家客服，友好回复买家闲聊，引导到商品上，30 字以内。";
            try {
                String aiReply = chatService.chat(modelId, systemPrompt, message);
                if (aiReply != null && !aiReply.isBlank()) return aiReply;
            } catch (Exception e) {
                // ignore
            }
        }

        return "亲，有什么可以帮你的？看中哪款了？";
    }

    // ======================================================================
    // 知识库查询
    // ======================================================================

    private String searchKnowledge(Long accountId, Long productId, String category, String query) {
        LambdaQueryWrapper<AiCsKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCsKnowledge::getCategory, category)
                .eq(AiCsKnowledge::getIsActive, true)
                .and(w -> w.isNull(AiCsKnowledge::getAccountId).or().eq(AiCsKnowledge::getAccountId, accountId))
                .and(w -> w.isNull(AiCsKnowledge::getProductId).or().eq(AiCsKnowledge::getProductId, productId))
                .orderByAsc(AiCsKnowledge::getPriority)
                .last("LIMIT 5");

        List<AiCsKnowledge> candidates = knowledgeMapper.selectList(wrapper);
        if (candidates.isEmpty()) return null;

        // 简单关键词匹配
        for (AiCsKnowledge kb : candidates) {
            if (kb.getQuestion() != null && query != null && query.contains(kb.getQuestion())) {
                return kb.getAnswer();
            }
        }

        return null;
    }

    // ======================================================================
    // 会话管理
    // ======================================================================

    private AiCsSession findOrCreateSession(Long accountId, String buyerId, String buyerNickname) {
        AiCsSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<AiCsSession>()
                        .eq(AiCsSession::getAccountId, accountId)
                        .eq(AiCsSession::getBuyerId, buyerId));

        if (session == null) {
            session = new AiCsSession();
            session.setAccountId(accountId);
            session.setBuyerId(buyerId);
            session.setBuyerNickname(buyerNickname);
            session.setStatus("ACTIVE");
            session.setCreatedAt(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        return session;
    }

    private AiCsPolicy getPolicy(Long accountId) {
        return policyMapper.selectOne(
                new LambdaQueryWrapper<AiCsPolicy>().eq(AiCsPolicy::getAccountId, accountId));
    }

    // ======================================================================
    // 内部类
    // ======================================================================

    public static class IntentResult {
        public final String intent;
        public final double confidence;

        public IntentResult(String intent, double confidence) {
            this.intent = intent;
            this.confidence = confidence;
        }
    }
}
