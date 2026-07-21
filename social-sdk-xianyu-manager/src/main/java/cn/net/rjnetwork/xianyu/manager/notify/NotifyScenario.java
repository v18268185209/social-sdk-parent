package cn.net.rjnetwork.xianyu.manager.notify;

import java.util.Arrays;

/**
 * 通知场景枚举。每个场景自带默认标题/正文模板与去重冷却时间（防骚扰）。
 * 数据库 notify_template 表中若存在同 scenario 的启用模板，则优先使用库内模板。
 */
public enum NotifyScenario {

    ACCOUNT_COOKIE_EXPIRED("账号Cookie过期",
            "闲鱼账号 {accountName} 登录已失效",
            "账号 {accountName} 的登录状态已失效（Cookie 过期），请尽快重新扫码登录，否则相关自动化将中断。",
            3600),
    ACCOUNT_OFFLINE("账号离线",
            "闲鱼账号 {accountName} 已离线",
            "账号 {accountName} 当前处于离线状态，可能影响消息/订单处理。",
            3600),
    NEW_ORDER("新订单",
            "账号 {accountName} 收到新订单",
            "账号 {accountName} 收到新订单：{itemTitle}，金额 {amount}，对手方 {counterparty}。",
            60),
    ORDER_STATUS_CHANGED("订单状态变更",
            "订单 {orderId} 状态更新",
            "账号 {accountName} 的订单 {orderId}（{itemTitle}）状态变更为：{status}。",
            60),
    NEW_MESSAGE("新消息",
            "账号 {accountName} 收到买家消息",
            "账号 {accountName} 收到来自买家的消息：{content}",
            30),
    CAPTCHA_REQUIRED("滑块验证需要处理",
            "账号 {accountName} 触发闲鱼滑块验证",
            "账号 {accountName} 消息同步触发闲鱼风控（{errorSummary}）。系统已优先尝试全自动处理；如仍未通过，请直接打开人工控制页：{controlUrl}。该页面会代理到已登录的 CDP Chrome，无需远程桌面；请在页面截图上拖动滑块，完成后点「检查并保存 Cookie」。验证页仅用于排查：{captchaUrl}；CDP：{cdpEndpoint}。若控制页显示登录页，请在控制页内完成登录。",
            300),
    WALLET_LOW_BALANCE("钱包余额预警",
            "账号 {accountName} 钱包余额偏低",
            "账号 {accountName} 钱包余额 {balance}，已低于预警阈值 {threshold}。",
            3600),
    WALLET_LARGE_TXN("大额交易",
            "账号 {accountName} 发生大额交易",
            "账号 {accountName} 发生大额交易：{amount}（{bizType}），当前余额 {balance}。",
            300),

    // ===== 新增场景 =====
    // 监控匹配
    MONITOR_MATCH("监控发现匹配商品",
            "关键词 {keyword} 发现匹配商品",
            "任务 {taskName} 发现匹配商品：{itemTitle}，价格 {price} 元，卖家 {sellerNickname}。AI 评分：{aiScore} 分。理由：{aiReason}。链接：{itemUrl}",
            0),

    // AI 客服会话
    AI_CS_DEAL_CLOSED("AI客服成交",
            "AI 客服促成成交",
            "账号 {accountName} 与买家 {buyerNickname} 经 {bargainRound} 轮议价，以 {dealPrice} 元成交。商品：{productTitle}",
            0),
    AI_CS_DEAL_LOST("AI客服丢单",
            "AI 客服未能成交",
            "账号 {accountName} 与买家 {buyerNickname} 议价 {bargainRound} 轮未达成一致，买家最后出价 {lowestOffer} 元，商品原价 {originalPrice} 元",
            300),

    // 账号健康
    ACCOUNT_HEALTH_DROP("账号健康度下降",
            "账号 {accountName} 健康度降至 {healthScore}",
            "账号 {accountName} 健康度从 {oldScore} 降至 {newScore}。原因：{reason}。建议：{suggestion}",
            1800),

    // 熔断器事件
    CIRCUIT_BREAKER_OPENED("熔断器开闸",
            "服务 {serviceName} 触发熔断",
            "账号 {accountName} 的服务 {serviceName} 连续失败 {failureCount} 次，已熔断 {cooldownSeconds} 秒。最后错误：{lastError}",
            600),
    CIRCUIT_BREAKER_RECOVERED("熔断器恢复",
            "服务 {serviceName} 已恢复",
            "账号 {accountName} 的服务 {serviceName} 在熔断后已自动恢复运行",
            600),

    // 市场情报
    MARKET_PRICE_DROP("市场价格下降",
            "关键词 {keyword} 市场均价下降",
            "关键词 {keyword} 今日均价 {avgPrice} 元，较昨日变化 {changePercent}%。最低价 {minPrice} 元，最高价 {maxPrice} 元，成交量 {volume}。",
            86400);

    private final String label;
    private final String defaultTitle;
    private final String defaultBody;
    private final int cooldownSeconds;

    NotifyScenario(String label, String defaultTitle, String defaultBody, int cooldownSeconds) {
        this.label = label;
        this.defaultTitle = defaultTitle;
        this.defaultBody = defaultBody;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getLabel() { return label; }
    public String getDefaultTitle() { return defaultTitle; }
    public String getDefaultBody() { return defaultBody; }
    public int getCooldownSeconds() { return cooldownSeconds; }

    public static NotifyScenario fromName(String name) {
        return Arrays.stream(values())
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
