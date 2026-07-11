package com.socialsdk.chrome.operation;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一操作管理器
 * 整合Cookie、Cache、Element、Slider、Captcha等所有操作功能
 */
public class OperationManager {

    private static final Logger logger = LoggerFactory.getLogger(OperationManager.class);

    // ==================== 操作配置 ====================

    public static class OperationConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private CookieManager.CookieConfig cookieConfig = CookieManager.CookieConfig.defaultConfig();
        private CacheManager.CacheConfig cacheConfig = CacheManager.CacheConfig.defaultConfig();
        private ElementHandler.ElementConfig elementConfig = ElementHandler.ElementConfig.defaultConfig();
        private SliderHandler.SliderConfig sliderConfig = SliderHandler.SliderConfig.defaultConfig();
        private CaptchaHandler.CaptchaConfig captchaConfig = CaptchaHandler.CaptchaConfig.defaultConfig();

        // Getters and Setters
        public CookieManager.CookieConfig getCookieConfig() { return cookieConfig; }
        public void setCookieConfig(CookieManager.CookieConfig cookieConfig) { this.cookieConfig = cookieConfig; }
        public CacheManager.CacheConfig getCacheConfig() { return cacheConfig; }
        public void setCacheConfig(CacheManager.CacheConfig cacheConfig) { this.cacheConfig = cacheConfig; }
        public ElementHandler.ElementConfig getElementConfig() { return elementConfig; }
        public void setElementConfig(ElementHandler.ElementConfig elementConfig) { this.elementConfig = elementConfig; }
        public SliderHandler.SliderConfig getSliderConfig() { return sliderConfig; }
        public void setSliderConfig(SliderHandler.SliderConfig sliderConfig) { this.sliderConfig = sliderConfig; }
        public CaptchaHandler.CaptchaConfig getCaptchaConfig() { return captchaConfig; }
        public void setCaptchaConfig(CaptchaHandler.CaptchaConfig captchaConfig) { this.captchaConfig = captchaConfig; }

        /**
         * 默认配置
         */
        public static OperationConfig defaultConfig() {
            return new OperationConfig();
        }

        /**
         * 快速操作配置
         */
        public static OperationConfig quickConfig() {
            OperationConfig config = new OperationConfig();
            config.setElementConfig(ElementHandler.ElementConfig.quickConfig());
            return config;
        }

        /**
         * 精准操作配置
         */
        public static OperationConfig preciseConfig() {
            OperationConfig config = new OperationConfig();
            config.setElementConfig(ElementHandler.ElementConfig.preciseConfig());
            config.setSliderConfig(SliderHandler.SliderConfig.humanLikeConfig());
            return config;
        }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final OperationConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private CookieManager cookieManager;
    private CacheManager cacheManager;
    private ElementHandler elementHandler;
    private SliderHandler sliderHandler;
    private CaptchaHandler captchaHandler;

    // ==================== 构造函数 ====================

    public OperationManager(WebDriver driver) {
        this(driver, OperationConfig.defaultConfig());
    }

    public OperationManager(WebDriver driver, OperationConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        initialize();
    }

    private synchronized void initialize() {
        if (initialized.compareAndSet(false, true)) {
            // 初始化所有管理器
            this.cookieManager = new CookieManager(driver, config.getCookieConfig());
            this.cacheManager = new CacheManager(driver, config.getCacheConfig());
            this.elementHandler = new ElementHandler(driver, config.getElementConfig());
            this.sliderHandler = new SliderHandler(driver, config.getSliderConfig());
            this.captchaHandler = new CaptchaHandler(driver, config.getCaptchaConfig());

            logger.debug("OperationManager initialized");
        }
    }

    // ==================== 获取各管理器 ====================

    /**
     * 获取Cookie管理器
     */
    public CookieManager cookies() {
        return cookieManager;
    }

    /**
     * 获取缓存管理器
     */
    public CacheManager cache() {
        return cacheManager;
    }

    /**
     * 获取元素操作器
     */
    public ElementHandler elements() {
        return elementHandler;
    }

    /**
     * 获取滑块处理器
     */
    public SliderHandler slider() {
        return sliderHandler;
    }

    /**
     * 获取验证码处理器
     */
    public CaptchaHandler captcha() {
        return captchaHandler;
    }

    // ==================== 便捷方法 - Cookie ====================

    public OperationManager addCookie(String name, String value) {
        cookieManager.addCookie(name, value);
        return this;
    }

    public OperationManager addCookies(java.util.Map<String, String> cookies) {
        cookieManager.addCookies(cookies);
        return this;
    }

    public java.util.Optional<String> getCookie(String name) {
        return cookieManager.getCookie(name);
    }

    public java.util.Map<String, String> getAllCookies() {
        return cookieManager.getAllCookies();
    }

    public OperationManager deleteCookie(String name) {
        cookieManager.deleteCookie(name);
        return this;
    }

    public OperationManager deleteAllCookies() {
        cookieManager.deleteAllCookies();
        return this;
    }

    public void saveCookies(String filePath) {
        cookieManager.saveToFile(filePath);
    }

    public void loadCookies(String filePath) {
        cookieManager.loadFromFile(filePath);
    }

    // ==================== 便捷方法 - Cache ====================

    public OperationManager setCache(String key, String value) {
        cacheManager.set(key, value);
        return this;
    }

    public java.util.Optional<String> getCache(String key) {
        return cacheManager.get(key);
    }

    public OperationManager deleteCache(String key) {
        cacheManager.delete(key);
        return this;
    }

    public OperationManager clearCache() {
        cacheManager.clear();
        return this;
    }

    public void saveCache(String filePath) {
        cacheManager.saveToFile(filePath);
    }

    public void loadCache(String filePath) {
        cacheManager.loadFromFile(filePath);
    }

    // ==================== 便捷方法 - Element ====================

    public java.util.Optional<org.openqa.selenium.WebElement> find(
        ElementHandler.ElementLocator locator) {
        return elementHandler.findElement(locator);
    }

    public java.util.Optional<org.openqa.selenium.WebElement> findClickable(
        ElementHandler.ElementLocator locator) {
        return elementHandler.findClickableElement(locator);
    }

    public java.util.List<org.openqa.selenium.WebElement> findAll(
        ElementHandler.ElementLocator locator) {
        return elementHandler.findElements(locator);
    }

    public ElementHandler.ElementResult<org.openqa.selenium.WebElement> click(
        ElementHandler.ElementLocator locator) {
        return elementHandler.click(locator);
    }

    public ElementHandler.ElementResult<org.openqa.selenium.WebElement> doubleClick(
        ElementHandler.ElementLocator locator) {
        return elementHandler.doubleClick(locator);
    }

    public ElementHandler.ElementResult<org.openqa.selenium.WebElement> hover(
        ElementHandler.ElementLocator locator) {
        return elementHandler.hover(locator);
    }

    public ElementHandler.ElementResult<org.openqa.selenium.WebElement> input(
        ElementHandler.ElementLocator locator, String text) {
        return elementHandler.input(locator, text);
    }

    public java.util.Optional<String> getText(ElementHandler.ElementLocator locator) {
        return elementHandler.getText(locator);
    }

    public java.util.Optional<String> getAttribute(
        ElementHandler.ElementLocator locator, String attribute) {
        return elementHandler.getAttribute(locator, attribute);
    }

    public boolean exists(ElementHandler.ElementLocator locator) {
        return elementHandler.exists(locator);
    }

    public boolean isVisible(ElementHandler.ElementLocator locator) {
        return elementHandler.isVisible(locator);
    }

    public OperationManager scrollToTop() {
        elementHandler.scrollToTop();
        return this;
    }

    public OperationManager scrollToBottom() {
        elementHandler.scrollToBottom();
        return this;
    }

    // ==================== 便捷方法 - Slider ====================

    public SliderHandler.SliderResult slideTo(int distance) {
        return sliderHandler.slideTo(distance);
    }

    public SliderHandler.SliderResult slideRandom() {
        return sliderHandler.slideRandom();
    }

    public void slideMultiple(int count) {
        sliderHandler.slideMultiple(count);
    }

    public SliderHandler.SliderResult handleGeetest() {
        return sliderHandler.handleGeetest();
    }

    public SliderHandler.SliderResult handleAliSlider() {
        return sliderHandler.handleAliSlider();
    }

    // ==================== 便捷方法 - Captcha ====================

    public java.util.Optional<CaptchaHandler.CaptchaType> detectCaptcha() {
        return captchaHandler.detectCaptcha();
    }

    public CaptchaHandler.CaptchaResult handleCaptcha() {
        return captchaHandler.handle();
    }

    public CaptchaHandler.CaptchaResult handleCaptcha(CaptchaHandler.CaptchaType type) {
        return captchaHandler.handle(type);
    }

    public CaptchaHandler.CaptchaResult handleSliderCaptcha() {
        return captchaHandler.handle(CaptchaHandler.CaptchaType.SLIDER);
    }

    public CaptchaHandler.CaptchaResult handleImageCaptcha() {
        return captchaHandler.handle(CaptchaHandler.CaptchaType.IMAGE);
    }

    public CaptchaHandler.CaptchaResult handleClickCaptcha() {
        return captchaHandler.handle(CaptchaHandler.CaptchaType.CLICK);
    }

    // ==================== 组合操作 ====================

    /**
     * 清除所有状态（Cookie、Cache等）
     */
    public OperationManager clearAllState() {
        cookieManager.deleteAllCookies();
        cacheManager.clear();
        logger.info("Cleared all state");
        return this;
    }

    /**
     * 保存完整会话状态
     */
    public void saveSession(String basePath) {
        String cookiePath = basePath.replace(".json", "-cookies.json");
        String cachePath = basePath.replace(".json", "-cache.json");

        cookieManager.saveToFile(cookiePath);
        cacheManager.saveToFile(cachePath);

        logger.info("Session saved to {} and {}", cookiePath, cachePath);
    }

    /**
     * 加载完整会话状态
     */
    public void loadSession(String basePath) {
        String cookiePath = basePath.replace(".json", "-cookies.json");
        String cachePath = basePath.replace(".json", "-cache.json");

        cookieManager.loadFromFile(cookiePath);
        cacheManager.loadFromFile(cachePath);

        logger.info("Session loaded from {} and {}", cookiePath, cachePath);
    }

    /**
     * 执行登录流程（Cookie+缓存恢复）
     */
    public OperationManager login(String sessionFile) {
        loadSession(sessionFile);
        return this;
    }

    /**
     * 执行登出流程（清除所有状态）
     */
    public OperationManager logout() {
        clearAllState();
        return this;
    }

    /**
     * 等待页面稳定
     */
    public void waitForStable() {
        elementHandler.waitForPageLoad();
        elementHandler.waitForPageIdle();
    }

    /**
     * 获取完整统计信息
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("cookies", cookieManager.getCookieCount());
        stats.put("cache", cacheManager.getStatistics());
        return stats;
    }

    // ==================== 静态方法 ====================

    public static OperationManager create(WebDriver driver) {
        return new OperationManager(driver);
    }

    public static OperationManager create(WebDriver driver, OperationConfig config) {
        return new OperationManager(driver, config);
    }
}
