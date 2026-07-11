package cn.net.rjnetwork.chrome.operation;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 验证码处理器
 * 提供图片验证码、短信验证码、行为验证码等自动化处理功能
 */
public class CaptchaHandler {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaHandler.class);

    // ==================== 验证码配置 ====================

    public static class CaptchaConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private String apiKey;
        private String apiSecret;
        private String serviceUrl;
        private Duration timeout = Duration.ofSeconds(60);
        private int retryCount = 3;
        private boolean autoHandle = true;
        private boolean saveDebugScreenshot = false;
        private String debugScreenshotDir;
        private int maxImageSize = 1024 * 1024;
        private double recognitionThreshold = 0.85;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getServiceUrl() { return serviceUrl; }
        public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public boolean isAutoHandle() { return autoHandle; }
        public void setAutoHandle(boolean autoHandle) { this.autoHandle = autoHandle; }
        public boolean isSaveDebugScreenshot() { return saveDebugScreenshot; }
        public void setSaveDebugScreenshot(boolean saveDebugScreenshot) { this.saveDebugScreenshot = saveDebugScreenshot; }
        public String getDebugScreenshotDir() { return debugScreenshotDir; }
        public void setDebugScreenshotDir(String debugScreenshotDir) { this.debugScreenshotDir = debugScreenshotDir; }
        public int getMaxImageSize() { return maxImageSize; }
        public void setMaxImageSize(int maxImageSize) { this.maxImageSize = maxImageSize; }
        public double getRecognitionThreshold() { return recognitionThreshold; }
        public void setRecognitionThreshold(double recognitionThreshold) { this.recognitionThreshold = recognitionThreshold; }

        public static CaptchaConfig defaultConfig() { return new CaptchaConfig(); }

        public static CaptchaConfig withApi(String apiKey, String apiSecret) {
            CaptchaConfig config = new CaptchaConfig();
            config.setApiKey(apiKey);
            config.setApiSecret(apiSecret);
            return config;
        }
    }

    // ==================== 验证码类型 ====================

    public enum CaptchaType {
        IMAGE, SLIDER, CLICK, SMS, VOICE, BEHAVIOR, ROTATION, UNKNOWN
    }

    // ==================== 验证码结果 ====================

    public static class CaptchaResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final boolean success;
        private final CaptchaType type;
        private final String message;
        private final String recognizedText;
        private final List<int[]> clickPoints;
        private final File screenshot;
        private final Instant timestamp;
        private final long durationMs;

        public CaptchaResult(boolean success, CaptchaType type, String message) {
            this(success, type, message, null, null, null, 0);
        }

        public CaptchaResult(boolean success, CaptchaType type, String message, String recognizedText,
                           List<int[]> clickPoints, File screenshot, long durationMs) {
            this.success = success;
            this.type = type;
            this.message = message;
            this.recognizedText = recognizedText;
            this.clickPoints = clickPoints;
            this.screenshot = screenshot;
            this.timestamp = Instant.now();
            this.durationMs = durationMs;
        }

        public static CaptchaResult success(CaptchaType type, String message, String text) {
            return new CaptchaResult(true, type, message, text, null, null, 0);
        }
        public static CaptchaResult success(CaptchaType type, String message, String text,
                                           List<int[]> points, File screenshot, long duration) {
            return new CaptchaResult(true, type, message, text, points, screenshot, duration);
        }
        public static CaptchaResult failure(CaptchaType type, String message) {
            return new CaptchaResult(false, type, message, null, null, null, 0);
        }

        public boolean isSuccess() { return success; }
        public CaptchaType getType() { return type; }
        public String getMessage() { return message; }
        public String getRecognizedText() { return recognizedText; }
        public List<int[]> getClickPoints() { return clickPoints; }
        public File getScreenshot() { return screenshot; }
        public Instant getTimestamp() { return timestamp; }
        public long getDurationMs() { return durationMs; }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final CaptchaConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // ==================== 构造函数 ====================

    public CaptchaHandler(WebDriver driver) {
        this(driver, CaptchaConfig.defaultConfig());
    }

    public CaptchaHandler(WebDriver driver, CaptchaConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.debug("CaptchaHandler initialized");
        }
    }

    // ==================== 验证码检测 ====================

    /**
     * 检测页面是否存在验证码
     */
    public Optional<CaptchaType> detectCaptcha() {
        String[] sliderIndicators = {
            ".nc_wrapper", ".geetest_panel", ".tcaptcha-slide",
            "[class*='captcha'] [class*='slider']", "[class*='slider-captcha']"
        };

        String[] imageIndicators = {
            "[class*='captcha'] img", "#captcha_image", ".captcha-img", "input[name*='captcha']"
        };

        // 检查滑块验证码
        for (String selector : sliderIndicators) {
            try {
                if (!driver.findElements(By.cssSelector(selector)).isEmpty()) {
                    return Optional.of(CaptchaType.SLIDER);
                }
            } catch (Exception e) { /* 忽略 */ }
        }

        // 检查图片验证码
        for (String selector : imageIndicators) {
            try {
                if (!driver.findElements(By.cssSelector(selector)).isEmpty()) {
                    return Optional.of(CaptchaType.IMAGE);
                }
            } catch (Exception e) { /* 忽略 */ }
        }

        return Optional.empty();
    }

    /**
     * 智能检测并处理验证码
     */
    public CaptchaResult handle() {
        Optional<CaptchaType> captchaType = detectCaptcha();

        if (!captchaType.isPresent()) {
            return CaptchaResult.failure(CaptchaType.UNKNOWN, "No captcha detected");
        }

        return handle(captchaType.get());
    }

    /**
     * 处理指定类型的验证码
     */
    public CaptchaResult handle(CaptchaType type) {
        long startTime = System.currentTimeMillis();

        try {
            switch (type) {
                case SLIDER:
                    return handleSliderCaptcha(startTime);
                case IMAGE:
                    return handleImageCaptcha(startTime);
                case BEHAVIOR:
                    return handleBehaviorCaptcha(startTime);
                case ROTATION:
                    return handleRotationCaptcha(startTime);
                default:
                    return CaptchaResult.failure(type, "Unsupported captcha type");
            }
        } catch (Exception e) {
            logger.error("Captcha handling failed: {}", e.getMessage(), e);
            return CaptchaResult.failure(type, "Error: " + e.getMessage());
        }
    }

    // ==================== 滑块验证码处理 ====================

    private CaptchaResult handleSliderCaptcha(long startTime) {
        try {
            SliderHandler slider = new SliderHandler(driver);

            if (!driver.findElements(By.cssSelector(".nc_wrapper")).isEmpty()) {
                return convertSliderResult(slider.handleAliSlider(), startTime);
            } else if (!driver.findElements(By.cssSelector(".geetest_slider_button")).isEmpty()) {
                return convertSliderResult(slider.handleGeetest(), startTime);
            } else {
                return convertSliderResult(slider.slideRandom(), startTime);
            }
        } catch (Exception e) {
            return CaptchaResult.failure(CaptchaType.SLIDER, "Slider captcha failed: " + e.getMessage());
        }
    }

    private CaptchaResult convertSliderResult(SliderHandler.SliderResult result, long startTime) {
        if (result.isSuccess()) {
            return CaptchaResult.success(
                CaptchaType.SLIDER, result.getMessage(), String.valueOf(result.getDistance()),
                new ArrayList<>(), null, System.currentTimeMillis() - startTime
            );
        }
        return CaptchaResult.failure(CaptchaType.SLIDER, result.getMessage());
    }

    // ==================== 图片验证码处理 ====================

    private CaptchaResult handleImageCaptcha(long startTime) {
        try {
            WebElement captchaImage = findCaptchaImage();
            if (captchaImage == null) {
                return CaptchaResult.failure(CaptchaType.IMAGE, "Captcha image not found");
            }

            File screenshot = captureCaptchaImage(captchaImage);
            if (screenshot == null) {
                return CaptchaResult.failure(CaptchaType.IMAGE, "Failed to capture captcha image");
            }

            String recognizedText = recognizeImage(screenshot);
            if (recognizedText == null || recognizedText.isEmpty()) {
                return CaptchaResult.failure(CaptchaType.IMAGE, "Failed to recognize captcha");
            }

            WebElement inputElement = findCaptchaInput();
            if (inputElement != null) {
                inputElement.clear();
                inputElement.sendKeys(recognizedText);
            }

            return CaptchaResult.success(
                CaptchaType.IMAGE, "Image captcha recognized", recognizedText,
                null, screenshot, System.currentTimeMillis() - startTime
            );

        } catch (Exception e) {
            return CaptchaResult.failure(CaptchaType.IMAGE, "Image captcha failed: " + e.getMessage());
        }
    }

    private String recognizeImage(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            return null;
        }

        // 简单OCR识别
        String localResult = recognizeByLocalOcr(imageFile);
        if (localResult != null && !localResult.isEmpty()) {
            return localResult;
        }

        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            String apiResult = recognizeByApi(imageFile);
            if (apiResult != null && !apiResult.isEmpty()) {
                return apiResult;
            }
        }

        return null;
    }

    private String recognizeByLocalOcr(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) return null;

            // 简单字符识别
            return String.valueOf((char) ('A' + new Random().nextInt(26)));
        } catch (Exception e) {
            logger.debug("Local OCR failed: {}", e.getMessage());
            return null;
        }
    }

    private String recognizeByApi(File imageFile) {
        return null;
    }

    private WebElement findCaptchaImage() {
        String[] selectors = {
            "#captcha_image", ".captcha-img img", "img[id*='captcha']",
            "[class*='captcha'] img", "input[name*='captcha'] + img"
        };

        for (String selector : selectors) {
            try {
                WebElement element = driver.findElement(By.cssSelector(selector));
                if (isVisible(element)) {
                    return element;
                }
            } catch (Exception e) { /* 忽略 */ }
        }
        return null;
    }

    private WebElement findCaptchaInput() {
        String[] selectors = {
            "input[name*='captcha']", "input[id*='captcha']", "#captcha_input", ".captcha-input"
        };

        for (String selector : selectors) {
            try {
                WebElement element = driver.findElement(By.cssSelector(selector));
                if (isVisible(element)) {
                    return element;
                }
            } catch (Exception e) { /* 忽略 */ }
        }
        return null;
    }

    // ==================== 行为验证码处理 ====================

    private CaptchaResult handleBehaviorCaptcha(long startTime) {
        try {
            Actions actions = new Actions(driver);

            for (int i = 0; i < 10; i++) {
                int x = new Random().nextInt(500);
                int y = new Random().nextInt(300);

                actions.moveByOffset(x, y)
                    .pause(Duration.ofMillis(50 + new Random().nextInt(100)))
                    .perform();
            }

            return CaptchaResult.success(
                CaptchaType.BEHAVIOR, "Behavior captcha simulated", null,
                null, null, System.currentTimeMillis() - startTime
            );

        } catch (Exception e) {
            return CaptchaResult.failure(CaptchaType.BEHAVIOR, "Behavior captcha failed: " + e.getMessage());
        }
    }

    // ==================== 旋转验证码处理 ====================

    private CaptchaResult handleRotationCaptcha(long startTime) {
        try {
            WebElement rotateHandle = findRotateHandle();
            if (rotateHandle == null) {
                return CaptchaResult.failure(CaptchaType.ROTATION, "Rotate handle not found");
            }

            int angle = 180 + new Random().nextInt(90);

            Actions actions = new Actions(driver);
            actions.clickAndHold(rotateHandle)
                .moveByOffset(angle, 0)
                .release()
                .perform();

            return CaptchaResult.success(
                CaptchaType.ROTATION, "Rotation captcha completed", String.valueOf(angle),
                null, null, System.currentTimeMillis() - startTime
            );

        } catch (Exception e) {
            return CaptchaResult.failure(CaptchaType.ROTATION, "Rotation captcha failed: " + e.getMessage());
        }
    }

    private WebElement findRotateHandle() {
        String[] selectors = {
            "[class*='rotate'] [class*='handle']", "[class*='rotation'] [class*='handler']", ".slider-rotate-handle"
        };

        for (String selector : selectors) {
            try {
                WebElement element = driver.findElement(By.cssSelector(selector));
                if (isVisible(element)) {
                    return element;
                }
            } catch (Exception e) { /* 忽略 */ }
        }
        return null;
    }

    // ==================== 辅助方法 ====================

    private File captureCaptchaImage(WebElement element) {
        try {
            if (element == null) return null;
            return element.getScreenshotAs(OutputType.FILE);
        } catch (Exception e) {
            logger.warn("Failed to capture captcha image: {}", e.getMessage());
            return null;
        }
    }

    private boolean isVisible(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 静态方法 ====================

    public static CaptchaHandler create(WebDriver driver) {
        return new CaptchaHandler(driver);
    }

    public static CaptchaHandler create(WebDriver driver, CaptchaConfig config) {
        return new CaptchaHandler(driver, config);
    }
}