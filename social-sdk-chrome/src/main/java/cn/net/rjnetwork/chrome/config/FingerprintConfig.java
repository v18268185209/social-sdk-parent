package cn.net.rjnetwork.chrome.config;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 指纹伪装配置
 * 提供全面的浏览器指纹伪装功能，包括Canvas、WebGL、Audio、Navigator等
 */
public class FingerprintConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private static final Random RANDOM = new Random();

    // ==================== 基础伪装开关 ====================
    
    /**
     * 是否启用指纹伪装（全局开关）
     */
    private boolean enabled = true;

    /**
     * 是否启用Canvas指纹伪装
     */
    private boolean canvasSpoofEnabled = true;

    /**
     * 是否启用WebGL指纹伪装
     */
    private boolean webglSpoofEnabled = true;

    /**
     * 是否启用Audio指纹伪装
     */
    private boolean audioSpoofEnabled = true;

    /**
     * 是否启用Navigator属性伪装
     */
    private boolean navigatorSpoofEnabled = true;

    /**
     * 是否启用Screen属性伪装
     */
    private boolean screenSpoofEnabled = true;

    /**
     * 是否启用Timezone伪装
     */
    private boolean timezoneSpoofEnabled = true;

    /**
     * 是否启用WebRTC伪装
     */
    private boolean webrtcSpoofEnabled = true;

    /**
     * 是否启用Language伪装
     */
    private boolean languageSpoofEnabled = true;

    // ==================== Canvas伪装配置 ====================

    /**
     * 伪造的Canvas渲染噪声级别 (0.0-1.0)
     */
    private float canvasNoiseLevel = 0.5f;

    /**
     * Canvas指纹种子（用于生成一致的伪装）
     */
    private Long canvasSeed;

    // ==================== WebGL伪装配置 ====================

    /**
     * 伪造的WebGL渲染器 vendor
     */
    private String webglVendor = "Google Inc. (NVIDIA)";

    /**
     * 伪造的WebGL渲染器 renderer
     */
    private String webglRenderer = "ANGLE (NVIDIA GeForce GTX 1660 Super Direct3D11 vs_0_0 mb_0_0)";

    /**
     * WebGL版本号
     */
    private String webglVersion = "WebGL 2.0 (OpenGL ES 3.0 Chromium 120.0.0.0)";

    // ==================== Audio伪装配置 ====================

    /**
     * 伪造的AudioContext采样率
     */
    private int audioSampleRate = 44100;

    /**
     * 伪造的AudioContext通道数
     */
    private int audioChannels = 2;

    // ==================== Navigator伪装配置 ====================

    /**
     * 伪造的User-Agent
     */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 伪造的Platform
     */
    private String platform = "Win32";

    /**
     * 伪造的AppName
     */
    private String appName = "Netscape";

    /**
     * 伪造的AppVersion
     */
    private String appVersion = "5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 伪造的产品名称
     */
    private String product = "Gecko";

    /**
     * 伪造的Vendor
     */
    private String vendor = "Google Inc.";

    /**
     * 伪造的硬件并发数 (CPU核心数)
     */
    private int hardwareConcurrency = 8;

    /**
     * 伪造的设备内存 (GB)
     */
    private float deviceMemory = 8.0f;

    /**
     * 伪造的最大触摸点数
     */
    private int maxTouchPoints = 0;

    // ==================== Screen伪装配置 ====================

    /**
     * 伪造的屏幕宽度
     */
    private int screenWidth = 1920;

    /**
     * 伪造的屏幕高度
     */
    private int screenHeight = 1080;

    /**
     * 伪造的可用屏幕宽度
     */
    private int availScreenWidth = 1920;

    /**
     * 伪造的可用屏幕高度
     */
    private int availScreenHeight = 1040;

    /**
     * 伪造的颜色深度
     */
    private int colorDepth = 24;

    /**
     * 伪造的像素深度
     */
    private int pixelDepth = 24;

    /**
     * 伪造的屏幕缩放比例
     */
    private double screenScale = 1.0;

    // ==================== Timezone伪装配置 ====================

    /**
     * 伪造的时区
     */
    private String timezone = "Asia/Shanghai";

    /**
     * 伪造的时区偏移 (分钟)
     */
    private int timezoneOffset = 480;

    // ==================== Language伪装配置 ====================

    /**
     * 伪造的语言
     */
    private String language = "zh-CN";

    /**
     * 伪造的用户语言列表
     */
    private String[] languages = {"zh-CN", "zh", "en-US", "en"};

    /**
     * 伪造的Accept-Language
     */
    private String acceptLanguage = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";

    // ==================== WebRTC伪装配置 ====================

    /**
     * 伪造的本地IP (用于WebRTC泄露防护)
     */
    private String fakeLocalIP = "192.168.1.100";

    /**
     * 是否启用WebRTC ICE候选者伪装
     */
    private boolean spoofIceCandidates = true;

    // ==================== 连接伪装配置 ====================

    /**
     * 伪造的网络类型
     */
    private String connectionType = "4g";

    /**
     * 伪造的下载带宽 (Mbps)
     */
    private double downlink = 100.0;

    /**
     * 伪造的上传带宽 (Mbps)
     */
    private double uplink = 50.0;

    /**
     * 伪造的RTT (毫秒)
     */
    private int rtt = 50;

    /**
     * 伪造的DataSaver启用状态
     */
    private boolean saveData = false;

    // ==================== 额外指纹保护配置 ====================

    /**
     * 是否移除自动化特征
     */
    private boolean removeAutomationFlags = true;

    /**
     * 是否启用Client Rect随机化
     */
    private boolean randomizeClientRects = true;

    /**
     * 是否启用字体列表伪装
     */
    private boolean spoofFontList = true;

    /**
     * 伪造的插件列表
     */
    private String[] plugins = {
        "Chrome PDF Plugin",
        "Chrome PDF Viewer",
        "Native Client",
        "Adobe Flash Player"
    };

    /**
     * 伪造的MimeType列表
     */
    private String[] mimeTypes = {
        "application/pdf",
        "application/x-google-chrome-pdf",
        "application/x-shockwave-flash",
        "application/x-nacl"
    };

    // ==================== 构造函数 ====================

    public FingerprintConfig() {
        // 使用随机种子
        this.canvasSeed = System.currentTimeMillis() + RANDOM.nextLong();
    }

    public FingerprintConfig(boolean enabled) {
        this.enabled = enabled;
        this.canvasSeed = System.currentTimeMillis() + RANDOM.nextLong();
    }

    // ==================== 预置配置 ====================

    /**
     * 获取Windows伪装配置
     */
    public static FingerprintConfig windowsConfig() {
        FingerprintConfig config = new FingerprintConfig();
        config.setPlatform("Win32");
        config.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        config.setScreenWidth(1920);
        config.setScreenHeight(1080);
        config.setTimezone("Asia/Shanghai");
        config.setTimezoneOffset(480);
        config.setLanguage("zh-CN");
        config.setLanguages(new String[]{"zh-CN", "zh", "en-US", "en"});
        return config;
    }

    /**
     * 获取macOS伪装配置
     */
    public static FingerprintConfig macosConfig() {
        FingerprintConfig config = new FingerprintConfig();
        config.setPlatform("MacIntel");
        config.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        config.setScreenWidth(2560);
        config.setScreenHeight(1440);
        config.setTimezone("America/Los_Angeles");
        config.setTimezoneOffset(-480);
        config.setLanguage("en-US");
        config.setLanguages(new String[]{"en-US", "en", "zh-CN", "zh"});
        config.setHardwareConcurrency(8);
        config.setDeviceMemory(16.0f);
        return config;
    }

    /**
     * 获取Linux伪装配置
     */
    public static FingerprintConfig linuxConfig() {
        FingerprintConfig config = new FingerprintConfig();
        config.setPlatform("Linux x86_64");
        config.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        config.setScreenWidth(1920);
        config.setScreenHeight(1080);
        config.setTimezone("Europe/London");
        config.setTimezoneOffset(0);
        config.setLanguage("en-US");
        config.setLanguages(new String[]{"en-US", "en", "zh-CN", "zh"});
        return config;
    }

    // ==================== 随机化配置 ====================

    /**
     * 生成随机化配置（每次启动使用不同指纹）
     */
    public FingerprintConfig randomize() {
        // 随机屏幕分辨率
        int[] resolutions = {1920, 1366, 1440, 1536, 2560};
        int width = resolutions[RANDOM.nextInt(resolutions.length)];
        int height = (int) (width * (9.0 / 16.0)) + RANDOM.nextInt(100);
        this.screenWidth = width;
        this.screenHeight = Math.max(720, height);

        // 随机CPU核心数
        int[] cores = {4, 6, 8, 12, 16};
        this.hardwareConcurrency = cores[RANDOM.nextInt(cores.length)];

        // 随机内存
        float[] memories = {8.0f, 16.0f, 32.0f};
        this.deviceMemory = memories[RANDOM.nextInt(memories.length)];

        // 随机时区
        String[] timezones = {"Asia/Shanghai", "Asia/Tokyo", "America/New_York", "Europe/London"};
        this.timezone = timezones[RANDOM.nextInt(timezones.length)];

        // 随机硬件并发和内存
        this.canvasSeed = System.currentTimeMillis() + RANDOM.nextLong();

        return this;
    }

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCanvasSpoofEnabled() {
        return canvasSpoofEnabled;
    }

    public void setCanvasSpoofEnabled(boolean canvasSpoofEnabled) {
        this.canvasSpoofEnabled = canvasSpoofEnabled;
    }

    public boolean isWebglSpoofEnabled() {
        return webglSpoofEnabled;
    }

    public void setWebglSpoofEnabled(boolean webglSpoofEnabled) {
        this.webglSpoofEnabled = webglSpoofEnabled;
    }

    public boolean isAudioSpoofEnabled() {
        return audioSpoofEnabled;
    }

    public void setAudioSpoofEnabled(boolean audioSpoofEnabled) {
        this.audioSpoofEnabled = audioSpoofEnabled;
    }

    public boolean isNavigatorSpoofEnabled() {
        return navigatorSpoofEnabled;
    }

    public void setNavigatorSpoofEnabled(boolean navigatorSpoofEnabled) {
        this.navigatorSpoofEnabled = navigatorSpoofEnabled;
    }

    public boolean isScreenSpoofEnabled() {
        return screenSpoofEnabled;
    }

    public void setScreenSpoofEnabled(boolean screenSpoofEnabled) {
        this.screenSpoofEnabled = screenSpoofEnabled;
    }

    public boolean isTimezoneSpoofEnabled() {
        return timezoneSpoofEnabled;
    }

    public void setTimezoneSpoofEnabled(boolean timezoneSpoofEnabled) {
        this.timezoneSpoofEnabled = timezoneSpoofEnabled;
    }

    public boolean isWebrtcSpoofEnabled() {
        return webrtcSpoofEnabled;
    }

    public void setWebrtcSpoofEnabled(boolean webrtcSpoofEnabled) {
        this.webrtcSpoofEnabled = webrtcSpoofEnabled;
    }

    public boolean isLanguageSpoofEnabled() {
        return languageSpoofEnabled;
    }

    public void setLanguageSpoofEnabled(boolean languageSpoofEnabled) {
        this.languageSpoofEnabled = languageSpoofEnabled;
    }

    public float getCanvasNoiseLevel() {
        return canvasNoiseLevel;
    }

    public void setCanvasNoiseLevel(float canvasNoiseLevel) {
        this.canvasNoiseLevel = Math.max(0.0f, Math.min(1.0f, canvasNoiseLevel));
    }

    public Long getCanvasSeed() {
        return canvasSeed;
    }

    public void setCanvasSeed(Long canvasSeed) {
        this.canvasSeed = canvasSeed;
    }

    public String getWebglVendor() {
        return webglVendor;
    }

    public void setWebglVendor(String webglVendor) {
        this.webglVendor = webglVendor;
    }

    public String getWebglRenderer() {
        return webglRenderer;
    }

    public void setWebglRenderer(String webglRenderer) {
        this.webglRenderer = webglRenderer;
    }

    public String getWebglVersion() {
        return webglVersion;
    }

    public void setWebglVersion(String webglVersion) {
        this.webglVersion = webglVersion;
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public void setAudioSampleRate(int audioSampleRate) {
        this.audioSampleRate = audioSampleRate;
    }

    public int getAudioChannels() {
        return audioChannels;
    }

    public void setAudioChannels(int audioChannels) {
        this.audioChannels = audioChannels;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public int getHardwareConcurrency() {
        return hardwareConcurrency;
    }

    public void setHardwareConcurrency(int hardwareConcurrency) {
        this.hardwareConcurrency = hardwareConcurrency;
    }

    public float getDeviceMemory() {
        return deviceMemory;
    }

    public void setDeviceMemory(float deviceMemory) {
        this.deviceMemory = deviceMemory;
    }

    public int getMaxTouchPoints() {
        return maxTouchPoints;
    }

    public void setMaxTouchPoints(int maxTouchPoints) {
        this.maxTouchPoints = maxTouchPoints;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public int getAvailScreenWidth() {
        return availScreenWidth;
    }

    public void setAvailScreenWidth(int availScreenWidth) {
        this.availScreenWidth = availScreenWidth;
    }

    public int getAvailScreenHeight() {
        return availScreenHeight;
    }

    public void setAvailScreenHeight(int availScreenHeight) {
        this.availScreenHeight = availScreenHeight;
    }

    public int getColorDepth() {
        return colorDepth;
    }

    public void setColorDepth(int colorDepth) {
        this.colorDepth = colorDepth;
    }

    public int getPixelDepth() {
        return pixelDepth;
    }

    public void setPixelDepth(int pixelDepth) {
        this.pixelDepth = pixelDepth;
    }

    public double getScreenScale() {
        return screenScale;
    }

    public void setScreenScale(double screenScale) {
        this.screenScale = screenScale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getTimezoneOffset() {
        return timezoneOffset;
    }

    public void setTimezoneOffset(int timezoneOffset) {
        this.timezoneOffset = timezoneOffset;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String[] getLanguages() {
        return languages;
    }

    public void setLanguages(String[] languages) {
        this.languages = languages;
    }

    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    public void setAcceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage;
    }

    public String getFakeLocalIP() {
        return fakeLocalIP;
    }

    public void setFakeLocalIP(String fakeLocalIP) {
        this.fakeLocalIP = fakeLocalIP;
    }

    public boolean isSpoofIceCandidates() {
        return spoofIceCandidates;
    }

    public void setSpoofIceCandidates(boolean spoofIceCandidates) {
        this.spoofIceCandidates = spoofIceCandidates;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    public double getDownlink() {
        return downlink;
    }

    public void setDownlink(double downlink) {
        this.downlink = downlink;
    }

    public double getUplink() {
        return uplink;
    }

    public void setUplink(double uplink) {
        this.uplink = uplink;
    }

    public int getRtt() {
        return rtt;
    }

    public void setRtt(int rtt) {
        this.rtt = rtt;
    }

    public boolean isSaveData() {
        return saveData;
    }

    public void setSaveData(boolean saveData) {
        this.saveData = saveData;
    }

    public boolean isRemoveAutomationFlags() {
        return removeAutomationFlags;
    }

    public void setRemoveAutomationFlags(boolean removeAutomationFlags) {
        this.removeAutomationFlags = removeAutomationFlags;
    }

    public boolean isRandomizeClientRects() {
        return randomizeClientRects;
    }

    public void setRandomizeClientRects(boolean randomizeClientRects) {
        this.randomizeClientRects = randomizeClientRects;
    }

    public boolean isSpoofFontList() {
        return spoofFontList;
    }

    public void setSpoofFontList(boolean spoofFontList) {
        this.spoofFontList = spoofFontList;
    }

    public String[] getPlugins() {
        return plugins;
    }

    public void setPlugins(String[] plugins) {
        this.plugins = plugins;
    }

    public String[] getMimeTypes() {
        return mimeTypes;
    }

    public void setMimeTypes(String[] mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

    // ==================== 工具方法 ====================

    /**
     * 生成用于注入的JavaScript代码
     */
    public String generateJavascriptInjection() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");

        // Navigator伪装
        if (navigatorSpoofEnabled) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    userAgent: { get: () => '").append(escapeJs(userAgent)).append("', configurable: true },\n");
            sb.append("    platform: { get: () => '").append(escapeJs(platform)).append("', configurable: true },\n");
            sb.append("    appName: { get: () => '").append(escapeJs(appName)).append("', configurable: true },\n");
            sb.append("    appVersion: { get: () => '").append(escapeJs(appVersion)).append("', configurable: true },\n");
            sb.append("    product: { get: () => '").append(escapeJs(product)).append("', configurable: true },\n");
            sb.append("    vendor: { get: () => '").append(escapeJs(vendor)).append("', configurable: true },\n");
            sb.append("    hardwareConcurrency: { get: () => ").append(hardwareConcurrency).append(", configurable: true },\n");
            sb.append("    deviceMemory: { get: () => ").append((int) deviceMemory).append(", configurable: true },\n");
            sb.append("    maxTouchPoints: { get: () => ").append(maxTouchPoints).append(", configurable: true }\n");
            sb.append("  });\n");
        }

        // Screen伪装
        if (screenSpoofEnabled) {
            sb.append("  Object.defineProperties(screen, {\n");
            sb.append("    width: { get: () => ").append(screenWidth).append(", configurable: true },\n");
            sb.append("    height: { get: () => ").append(screenHeight).append(", configurable: true },\n");
            sb.append("    availWidth: { get: () => ").append(availScreenWidth).append(", configurable: true },\n");
            sb.append("    availHeight: { get: () => ").append(availScreenHeight).append(", configurable: true },\n");
            sb.append("    colorDepth: { get: () => ").append(colorDepth).append(", configurable: true },\n");
            sb.append("    pixelDepth: { get: () => ").append(pixelDepth).append(", configurable: true }\n");
            sb.append("  });\n");
        }

        // Language伪装
        if (languageSpoofEnabled) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    language: { get: () => '").append(escapeJs(language)).append("', configurable: true },\n");
            sb.append("    languages: { get: () => ").append(arrayToJs(languages)).append(", configurable: true }\n");
            sb.append("  });\n");
        }

        // 移除自动化特征
        if (removeAutomationFlags) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    webdriver: { get: () => false, configurable: true }\n");
            sb.append("  });\n");
            sb.append("  Object.defineProperties(window, {\n");
            sb.append("    chrome: { get: () => ({ runtime: {} }), configurable: true }\n");
            sb.append("  });\n");
        }

        // Timezone伪装
        if (timezoneSpoofEnabled) {
            sb.append("  Date.prototype.getTimezoneOffset = function() {\n");
            sb.append("    return ").append(timezoneOffset).append(";\n");
            sb.append("  };\n");
        }

        // Connection伪装
        if (connectionType != null) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    connection: { get: () => ({\n");
            sb.append("      type: '").append(escapeJs(connectionType)).append("',\n");
            sb.append("      downlink: ").append(downlink).append(",\n");
            sb.append("      uplink: ").append(uplink).append(",\n");
            sb.append("      rtt: ").append(rtt).append(",\n");
            sb.append("      saveData: ").append(saveData).append("\n");
            sb.append("    }), configurable: true }\n");
            sb.append("  });\n");
        }

        sb.append("})();\n");
        return sb.toString();
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    private String arrayToJs(String[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(escapeJs(arr[i])).append("'");
        }
        sb.append("]");
        return sb.toString();
    }
}
