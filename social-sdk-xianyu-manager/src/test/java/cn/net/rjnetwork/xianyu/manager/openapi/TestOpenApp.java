package cn.net.rjnetwork.xianyu.manager.openapi;

import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;

/**
 * 测试用 OpenApp 构造器。统一 fixture，避免每个测试类重复样板。
 * 默认：ENABLED / 不限流 / 不绑定账号白名单 / 未过期。
 */
public final class TestOpenApp {

    private TestOpenApp() {}

    public static OpenApp enabled(String appKey) {
        OpenApp app = new OpenApp();
        app.setId(1L);
        app.setAppName("test-app");
        app.setAppKey(appKey);
        app.setAppSecretEnc("enc-secret");
        app.setStatus("ENABLED");
        app.setBoundAccountIds(null); // 空白名单 = 不限制
        app.setRateLimitPerMinute(60);
        app.setExpireAt(null);
        return app;
    }

    /** 绑定账号白名单（JSON 数组） */
    public static OpenApp bound(String appKey, Long... boundIds) {
        OpenApp app = enabled(appKey);
        if (boundIds != null && boundIds.length > 0) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < boundIds.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(boundIds[i]);
            }
            sb.append("]");
            app.setBoundAccountIds(sb.toString());
        }
        return app;
    }

    public static OpenApp disabled(String appKey) {
        OpenApp app = enabled(appKey);
        app.setStatus("DISABLED");
        return app;
    }
}
