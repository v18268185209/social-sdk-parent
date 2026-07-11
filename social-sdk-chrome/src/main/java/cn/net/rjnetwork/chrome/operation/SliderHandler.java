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
import java.util.regex.Pattern;

/**
 * 滑块处理器
 * 提供滑块验证码、拼图验证等自动化操作
 */
public class SliderHandler {

    private static final Logger logger = LoggerFactory.getLogger(SliderHandler.class);

    // ==================== 滑块配置 ====================

    public static class SliderConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private int minDistance = 10;
        private int maxDistance = 500;
        private double defaultSpeed = 2.0;
        private boolean randomizeTrack = true;
        private double randomFactor = 0.3;
        private boolean simulateHuman = true;
        private Duration timeout = Duration.ofSeconds(30);
        private int retryCount = 3;
        private boolean waitForVerification = true;
        private Duration verificationWaitTime = Duration.ofSeconds(2);
        private String screenshotDir;
        private double gapRecognitionThreshold = 0.8;

        public int getMinDistance() { return minDistance; }
        public void setMinDistance(int minDistance) { this.minDistance = minDistance; }
        public int getMaxDistance() { return maxDistance; }
        public void setMaxDistance(int maxDistance) { this.maxDistance = maxDistance; }
        public double getDefaultSpeed() { return defaultSpeed; }
        public void setDefaultSpeed(double defaultSpeed) { this.defaultSpeed = defaultSpeed; }
        public boolean isRandomizeTrack() { return randomizeTrack; }
        public void setRandomizeTrack(boolean randomizeTrack) { this.randomizeTrack = randomizeTrack; }
        public double getRandomFactor() { return randomFactor; }
        public void setRandomFactor(double randomFactor) { this.randomFactor = randomFactor; }
        public boolean isSimulateHuman() { return simulateHuman; }
        public void setSimulateHuman(boolean simulateHuman) { this.simulateHuman = simulateHuman; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public boolean isWaitForVerification() { return waitForVerification; }
        public void setWaitForVerification(boolean waitForVerification) { this.waitForVerification = waitForVerification; }
        public Duration getVerificationWaitTime() { return verificationWaitTime; }
        public void setVerificationWaitTime(Duration verificationWaitTime) { this.verificationWaitTime = verificationWaitTime; }
        public String getScreenshotDir() { return screenshotDir; }
        public void setScreenshotDir(String screenshotDir) { this.screenshotDir = screenshotDir; }
        public double getGapRecognitionThreshold() { return gapRecognitionThreshold; }
        public void setGapRecognitionThreshold(double gapRecognitionThreshold) { this.gapRecognitionThreshold = gapRecognitionThreshold; }

        public static SliderConfig defaultConfig() { return new SliderConfig(); }

        public static SliderConfig fastConfig() {
            SliderConfig config = new SliderConfig();
            config.setDefaultSpeed(5.0);
            config.setRandomizeTrack(false);
            config.setSimulateHuman(false);
            return config;
        }

        public static SliderConfig humanLikeConfig() {
            SliderConfig config = new SliderConfig();
            config.setRandomizeTrack(true);
            config.setSimulateHuman(true);
            config.setRandomFactor(0.4);
            config.setDefaultSpeed(1.5);
            return config;
        }
    }

    // ==================== 滑块结果 ====================

    public static class SliderResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final boolean success;
        private final String message;
        private final int distance;
        private final long durationMs;
        private final List<int[]> track;
        private final Instant timestamp;

        public SliderResult(boolean success, String message, int distance, long durationMs, List<int[]> track) {
            this.success = success;
            this.message = message;
            this.distance = distance;
            this.durationMs = durationMs;
            this.track = track;
            this.timestamp = Instant.now();
        }

        public static SliderResult success(String message, int distance, long durationMs, List<int[]> track) {
            return new SliderResult(true, message, distance, durationMs, track);
        }
        public static SliderResult failure(String message) {
            return new SliderResult(false, message, 0, 0, new ArrayList<>());
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getDistance() { return distance; }
        public long getDurationMs() { return durationMs; }
        public List<int[]> getTrack() { return track; }
        public Instant getTimestamp() { return timestamp; }
    }

    // ==================== 轨迹点 ====================

    public static class TrackPoint {
        public final int x;
        public final int y;
        public final long time;

        public TrackPoint(int x, int y) {
            this(x, y, System.currentTimeMillis());
        }

        public TrackPoint(int x, int y, long time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final SliderConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // ==================== 构造函数 ====================

    public SliderHandler(WebDriver driver) {
        this(driver, SliderConfig.defaultConfig());
    }

    public SliderHandler(WebDriver driver, SliderConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.debug("SliderHandler initialized");
        }
    }

    // ==================== 滑块操作 ====================

    /**
     * 滑动到指定距离
     */
    public SliderResult slideTo(int distance) {
        return slideTo(distance, null);
    }

    /**
     * 滑动到指定距离（使用指定滑块元素）
     */
    public SliderResult slideTo(int distance, WebElement sliderElement) {
        if (distance < config.getMinDistance()) {
            return SliderResult.failure("Distance too small: " + distance);
        }
        if (distance > config.getMaxDistance()) {
            return SliderResult.failure("Distance too large: " + distance);
        }

        long startTime = System.currentTimeMillis();
        List<int[]> track = new ArrayList<>();

        try {
            // 获取滑块元素
            if (sliderElement == null) {
                sliderElement = findSliderElement();
            }

            if (sliderElement == null) {
                return SliderResult.failure("Slider element not found");
            }

            // 生成轨迹
            List<TrackPoint> moveTrack = generateTrack(distance);

            // 执行滑动
            Actions actions = new Actions(driver);
            actions.clickAndHold(sliderElement);

            for (TrackPoint point : moveTrack) {
                actions.moveByOffset(point.x, point.y);
                track.add(new int[]{point.x, point.y});

                // 添加短暂延迟模拟人类行为
                if (config.isSimulateHuman() && point.time > 0) {
                    try {
                        Thread.sleep((long) (Math.random() * 10 + 5));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            actions.release();
            actions.perform();

            long duration = System.currentTimeMillis() - startTime;

            // 等待验证
            if (config.isWaitForVerification()) {
                try {
                    Thread.sleep(config.getVerificationWaitTime().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            logger.info("Slid distance: {} in {}ms", distance, duration);
            return SliderResult.success("Slide successful", distance, duration, track);

        } catch (Exception e) {
            logger.error("Slider operation failed: {}", e.getMessage(), e);
            return SliderResult.failure("Slider failed: " + e.getMessage());
        }
    }

    /**
     * 执行随机滑动（用于触发验证）
     */
    public SliderResult slideRandom() {
        Random random = new Random();
        int distance = config.getMinDistance() +
            random.nextInt(config.getMaxDistance() - config.getMinDistance());
        return slideTo(distance);
    }

    /**
     * 多次随机滑动
     */
    public void slideMultiple(int count) {
        for (int i = 0; i < count; i++) {
            slideRandom();
            try {
                Thread.sleep(500 + new Random().nextInt(1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ==================== 轨迹生成 ====================

    /**
     * 生成滑动轨迹
     */
    public List<TrackPoint> generateTrack(int totalDistance) {
        List<TrackPoint> track = new ArrayList<>();
        int remainingDistance = totalDistance;
        long currentTime = System.currentTimeMillis();

        // 初始位置
        track.add(new TrackPoint(0, 0, currentTime));

        // 加速阶段
        int accelerateCount = (int) (totalDistance * 0.3);
        for (int i = 0; i < accelerateCount && remainingDistance > 0; i++) {
            int step = Math.min(5 + new Random().nextInt(10), remainingDistance);
            remainingDistance -= step;
            currentTime += 5 + (long) (Math.random() * 10);
            track.add(new TrackPoint(totalDistance - remainingDistance, (int) (Math.random() * 3 - 1), currentTime));
        }

        // 匀速阶段
        int steadyCount = (int) (totalDistance * 0.4);
        for (int i = 0; i < steadyCount && remainingDistance > 0; i++) {
            int step = Math.min(3 + new Random().nextInt(5), remainingDistance);
            remainingDistance -= step;
            currentTime += 8 + (long) (Math.random() * 15);
            int yOffset = (int) (Math.sin(i * 0.5) * 2);
            track.add(new TrackPoint(totalDistance - remainingDistance, yOffset, currentTime));
        }

        // 减速阶段
        int decelerateCount = (int) (totalDistance * 0.3);
        for (int i = 0; i < decelerateCount && remainingDistance > 0; i++) {
            int step = Math.min(2 + new Random().nextInt(4), remainingDistance);
            remainingDistance -= step;
            currentTime += 15 + (long) (Math.random() * 20);
            track.add(new TrackPoint(totalDistance - remainingDistance, (int) (Math.random() * 3 - 1), currentTime));
        }

        // 确保到达终点
        if (remainingDistance > 0) {
            currentTime += 10;
            track.add(new TrackPoint(totalDistance, 0, currentTime));
        }

        // 添加终点
        currentTime += 100;
        track.add(new TrackPoint(totalDistance, 0, currentTime));

        return track;
    }

    /**
     * 生成人类般的滑动轨迹
     */
    public List<TrackPoint> generateHumanLikeTrack(int totalDistance) {
        List<TrackPoint> track = new ArrayList<>();
        Random random = new Random();

        // 加入初始抖动
        int jitter = random.nextInt(5);
        track.add(new TrackPoint(jitter, random.nextInt(3)));

        int currentX = jitter;
        long startTime = System.currentTimeMillis();

        while (currentX < totalDistance) {
            // 变速移动
            double speedMultiplier = 0.5 + random.nextDouble() * 1.5;
            int baseStep = (int) (config.getDefaultSpeed() * speedMultiplier);

            // 添加随机后退
            if (random.nextDouble() < 0.1 && currentX > 20) {
                int backStep = Math.min(10 + random.nextInt(20), currentX);
                currentX -= backStep;
                track.add(new TrackPoint(currentX, random.nextInt(5) - 2));
            }

            // 前进
            int step = Math.min(baseStep, totalDistance - currentX);
            step = Math.max(1, step);
            currentX += step;

            // 添加Y轴抖动
            int yOffset = (int) (Math.sin(currentX * 0.1) * 3 + (random.nextDouble() - 0.5) * 4);

            long elapsed = System.currentTimeMillis() - startTime;
            track.add(new TrackPoint(currentX, yOffset, elapsed));

            // 随机暂停
            if (random.nextDouble() < 0.05) {
                try {
                    Thread.sleep(50 + random.nextInt(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 确保到达终点
        if (track.isEmpty() || track.get(track.size() - 1).x < totalDistance) {
            track.add(new TrackPoint(totalDistance, 0));
        }

        return track;
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找滑块元素
     */
    private WebElement findSliderElement() {
        String[] selectors = {
            ".nc_wrapper .nc_iconfont.btn_slide",
            ".slider-thumb",
            ".slide-to-unlock .handle",
            "[class*='slider'] [class*='handle']",
            ".geetest_slider_button",
            ".tcaptcha-slide-button"
        };

        for (String selector : selectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (isVisible(element)) {
                        return element;
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        }

        return null;
    }

    /**
     * 元素是否可见
     */
    private boolean isVisible(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 处理极验滑动验证码
     */
    public SliderResult handleGeetest() {
        try {
            WebElement slider = driver.findElement(By.cssSelector(".geetest_slider_button"));
            if (slider == null) {
                return SliderResult.failure("Geetest slider not found");
            }

            int distance = (int) (slider.getSize().getWidth() * 0.65);
            return slideTo(distance, slider);

        } catch (Exception e) {
            logger.error("Geetest handling failed: {}", e.getMessage());
            return SliderResult.failure("Geetest failed: " + e.getMessage());
        }
    }

    /**
     * 处理阿里云滑动验证码
     */
    public SliderResult handleAliSlider() {
        try {
            WebElement slider = driver.findElement(By.cssSelector(".nc_iconfont.btn_slide"));
            int distance = (int) (slider.getSize().getWidth() * 0.65);

            return slideTo(distance, slider);

        } catch (Exception e) {
            logger.error("Ali slider handling failed: {}", e.getMessage());
            return SliderResult.failure("Ali slider failed: " + e.getMessage());
        }
    }

    // ==================== 静态方法 ====================

    public static SliderHandler create(WebDriver driver) {
        return new SliderHandler(driver);
    }

    public static SliderHandler create(WebDriver driver, SliderConfig config) {
        return new SliderHandler(driver, config);
    }
}