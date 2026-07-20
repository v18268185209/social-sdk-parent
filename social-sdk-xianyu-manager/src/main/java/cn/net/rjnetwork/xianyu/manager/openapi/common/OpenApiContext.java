package cn.net.rjnetwork.xianyu.manager.openapi.common;

import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;

/**
 * 当前请求的对外应用上下文（ThreadLocal）。
 * 由 OpenApiAuthInterceptor 在 preHandle 中写入，请求结束后清除。
 * 控制器通过 getOpenApp() 获取调用方身份，配合 OpenAppService.assertAccountAccessible 做账号作用域校验。
 */
public final class OpenApiContext {

    private static final ThreadLocal<OpenApp> CURRENT = new ThreadLocal<>();

    private OpenApiContext() {}

    public static void setOpenApp(OpenApp app) {
        CURRENT.set(app);
    }

    public static OpenApp getOpenApp() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
