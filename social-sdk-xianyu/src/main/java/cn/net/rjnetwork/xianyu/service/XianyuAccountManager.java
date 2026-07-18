package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼账户管理能力：登出/验证登录/个人信息管理/切换账号。
 *
 * <p>登出策略：清当前 target 的闲鱼域 Cookie（tracknick/unb/cookie2/_nk_/login Rita 等），
 * 再刷新页面回到未登录态。不清浏览器其它域 Cookie，避免影响其它账号。</p>
 *
 * <p>切换账号：先登出当前账号 → 重新扫码登录新账号 →
 * 由上层 {@link XianyuCdpSessionPool} 决定是否为不同账号分配不同会话。</p>
 *
 * <p>本类无 Spring 注解，由 {@link XianyuSdk.XianyuAccount#accountManager()} 懒加载实例化。
 * 每账号独立实例，绑定该账号的 {@link CdpClient}。</p>
 */
public class XianyuAccountManager {

    private final CdpClient client;
    private final XianyuCdpBot bot;

    public XianyuAccountManager(CdpClient client, XianyuCdpBot bot) {
        this.client = client;
        this.bot = bot;
    }

    /** 验证当前账号登录态（cookie + 页面文案共同判定）。 */
    public boolean verifyLogin() {
        return bot.isLoggedIn();
    }

    /** 读取当前账号登录态详情。 */
    public Map<String, Object> loginStatus() {
        return bot.loginStatus();
    }

    /** 读取当前账号个人信息（昵称/主页 URL/资料 JSON）。 */
    public Map<String, Object> personalInfo() {
        return bot.getBasicInfo();
    }

    /** 读取当前账号昵称（从 cookie tracknick 解析）。 */
    public String nickname() {
        String cookie = bot.eval("document.cookie");
        if (cookie == null) return "";
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("tracknick=")) {
                try {
                    return java.net.URLDecoder.decode(p.substring("tracknick=".length()), "UTF-8");
                } catch (Exception e) {
                    return p.substring("tracknick=".length());
                }
            }
        }
        return "";
    }

    /**
     * 登出当前账号：清当前 target 的闲鱼域 Cookie → 刷新页面回到未登录态。
     * 不清其它域 Cookie，避免影响浏览器其它账号会话。
     */
    public Map<String, Object> logout() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            // 闲鱼登录态 Cookie 清单（按经验覆盖淘宝/闲鱼共用登录域）
            String[] cookies = {
                    "tracknick", "unb", "cookie2", "_nk_", "login Rita",
                    "_l_g_", "mtop_partitioned_detect", "sgcookie"
            };
            StringBuilder js = new StringBuilder("(function(){");
            for (String c : cookies) {
                js.append("document.cookie='").append(c).append("=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=.goofish.com';");
                js.append("document.cookie='").append(c).append("=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=.taobao.com';");
            }
            js.append("})()");
            bot.eval(js.toString());
            bot.sleep(500);
            // 刷新页面回到未登录态
            bot.navigate(bot.getUrl());
            bot.sleep(2500);
            boolean stillLoggedIn = bot.isLoggedIn();
            m.put("success", !stillLoggedIn);
            m.put("loggedIn", stillLoggedIn);
            m.put("message", stillLoggedIn ? "登出后仍检测到登录态（Cookie 可能在其它域）" : "已登出");
        } catch (Exception e) {
            m.put("success", false);
            m.put("message", "登出失败: " + e.getMessage());
        }
        return m;
    }

    /**
     * 切换账号：登出当前账号 → 返回扫码二维码供新账号登录。
     * 上层拿到二维码展示给用户扫码后，调用 {@link XianyuCdpBot#waitForLogin(long)} 轮询登录态。
     */
    public Map<String, Object> switchAccount() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> lo = logout();
        out.put("logout", lo);
        try {
            // 登出后取扫码二维码（bot 内部会点击「登录」「扫码登录」入口）
            Map<String, Object> qr = bot.getLoginQrBase64();
            out.put("qr", qr);
            out.put("success", Boolean.TRUE.equals(lo.get("success")) && qr != null);
            out.put("message", "已登出并返回新账号扫码二维码，扫码后调用 waitForLogin 验证");
        } catch (Exception e) {
            out.put("success", false);
            out.put("message", "登出成功但获取扫码失败: " + e.getMessage());
        }
        return out;
    }
}
