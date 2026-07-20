package cn.net.rjnetwork.xianyu.proxy.config;

/**
 * 会话粘性类型。单次请求 vs 粘性会话。
 *
 * <p>闲鱼滑块验证必须用 {@link #STICKY}，否则验证通过后 IP 变了，cookie 无法落盘到正确的 IP 上下文中。</p>
 */
public enum SessionType {

    /** 每次请求换一个 IP，仅用于 IP 属地伪装，不能用于登录 */
    ROTATING,

    /** 粘性会话，一次会话内 IP 不变，用于滑块验证 + cookie 链路 */
    STICKY;
}
