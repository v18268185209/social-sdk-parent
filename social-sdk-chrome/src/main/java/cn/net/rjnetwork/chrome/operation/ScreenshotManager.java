package cn.net.rjnetwork.chrome.operation;

import org.openqa.selenium.*;
import org.openqa.selenium.io.FileHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 截图管理器
 * 
 * 功能:
 * - 全屏截图
 * - 元素截图
 - 页面滚动截图
 * - 完整页面截图
 - 截图注释和标记
 * - 截图历史管理
 * 
 * @author Social SDK
 */
public class ScreenshotManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotManager.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    // ==================== 截图配置 ====================

    public static class ScreenshotConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 截图保存目录
         */
        private String saveDirectory = "./screenshots";

        /**
         * 截图格式
         */
        private ImageFormat format = ImageFormat.PNG;

        /**
         * 截图质量 (1-100)
         */
        private int quality = 90;

        /**
         * 是否包含时间戳
         */
        private boolean includeTimestamp = true;

        /**
         * 是否自动创建目录
         */
        private boolean autoCreateDirectory = true;

        /**
         * 最大截图历史数
         */
        private int maxHistory = 100;

        /**
         * 是否在截图前等待页面加载
         */
        private boolean waitForPageLoad = true;

        /**
         * 等待时间（毫秒）
         */
        private int waitBeforeScreenshot = 500;

        /**
         * 是否启用Retina屏幕支持
         */
        private boolean retinaSupport = false;

        /**
         * 截图文件名模板
         */
        private String filenameTemplate = "{timestamp}_{name}";

        // Getters and Setters
        public String getSaveDirectory() { return saveDirectory; }
        public void setSaveDirectory(String s) { this.saveDirectory = s; }
        public ImageFormat getFormat() { return format; }
        public void setFormat(ImageFormat f) { this.format = f; }
        public int getQuality() { return quality; }
        public void setQuality(int q) { this.quality = Math.max(1, Math.min(100, q)); }
        public boolean isIncludeTimestamp() { return includeTimestamp; }
        public void setIncludeTimestamp(boolean b) { this.includeTimestamp = b; }
        public boolean isAutoCreateDirectory() { return autoCreateDirectory; }
        public void setAutoCreateDirectory(boolean b) { this.autoCreateDirectory = b; }
        public int getMaxHistory() { return maxHistory; }
        public void setMaxHistory(int i) { this.maxHistory = i; }
        public boolean isWaitForPageLoad() { return waitForPageLoad; }
        public void setWaitForPageLoad(boolean b) { this.waitForPageLoad = b; }
        public int getWaitBeforeScreenshot() { return waitBeforeScreenshot; }
        public void setWaitBeforeScreenshot(int i) { this.waitBeforeScreenshot = i; }
        public boolean isRetinaSupport() { return retinaSupport; }
        public void setRetinaSupport(boolean b) { this.retinaSupport = b; }
        public String getFilenameTemplate() { return filenameTemplate; }
        public void setFilenameTemplate(String s) { this.filenameTemplate = s; }

        public static ScreenshotConfig defaultConfig() {
            return new ScreenshotConfig();
        }

        public static ScreenshotConfig highQualityConfig() {
            ScreenshotConfig c = new ScreenshotConfig();
            c.setFormat(ImageFormat.PNG);
            c.setQuality(100);
            return c;
        }
    }

    // ==================== 截图格式 ====================

    public enum ImageFormat {
        PNG("png", "image/png"),
        JPG("jpg", "image/jpeg"),
        JPEG("jpeg", "image/jpeg"),
        GIF("gif", "image/gif"),
        BMP("bmp", "image/bmp"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String mimeType;

        ImageFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() {
            return extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public static ImageFormat fromExtension(String ext) {
            for (ImageFormat format : values()) {
                if (format.extension.equalsIgnoreCase(ext)) {
                    return format;
                }
            }
            return PNG;
        }
    }

    // ==================== 截图记录 ====================

    public static class ScreenshotRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String filename;
        private String fullPath;
        private String name;
        private org.openqa.selenium.Dimension viewportSize;
        private org.openqa.selenium.Dimension pageSize;
        private Instant timestamp;
        private String url;
        private String title;
        private long fileSize;
        private String format;
        private Map<String, String> metadata;

        public ScreenshotRecord() {
            this.id = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>();
        }

        // Getters and Setters
        public String getId() { return id; }
        public String getFilename() { return filename; }
        public void setFilename(String f) { this.filename = f; }
        public String getFullPath() { return fullPath; }
        public void setFullPath(String p) { this.fullPath = p; }
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
        public org.openqa.selenium.Dimension getViewportSize() { return viewportSize; }
        public void setViewportSize(org.openqa.selenium.Dimension d) { this.viewportSize = d; }
        public org.openqa.selenium.Dimension getPageSize() { return pageSize; }
        public void setPageSize(org.openqa.selenium.Dimension d) { this.pageSize = d; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant t) { this.timestamp = t; }
        public String getUrl() { return url; }
        public void setUrl(String u) { this.url = u; }
        public String getTitle() { return title; }
        public void setTitle(String t) { this.title = t; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long s) { this.fileSize = s; }
        public String getFormat() { return format; }
        public void setFormat(String f) { this.format = f; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> m) { this.metadata = m; }

        public String toJson() {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface ScreenshotCallback {
        void onScreenshot(ScreenshotRecord record, byte[] data);
    }

    @FunctionalInterface
    public interface ErrorCallback {
        void onError(Exception e);
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final ScreenshotConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Path savePath;

    private final List<ScreenshotRecord> history = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, ScreenshotRecord> historyIndex = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<ScreenshotCallback> callbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ErrorCallback> errorCallbacks = new ConcurrentLinkedQueue<>();

    // ==================== 构造函数 ====================

    public ScreenshotManager(WebDriver driver) {
        this(driver, ScreenshotConfig.defaultConfig());
    }

    public ScreenshotManager(WebDriver driver, ScreenshotConfig config) {
        this.driver = driver;
        this.config = config;
        this.savePath = Paths.get(config.getSaveDirectory()).toAbsolutePath();

        // 创建保存目录
        if (config.isAutoCreateDirectory()) {
            try {
                Files.createDirectories(savePath);
                logger.info("Screenshot directory: {}", savePath);
            } catch (IOException e) {
                logger.warn("Failed to create screenshot directory: {}", e.getMessage());
            }
        }

        logger.info("ScreenshotManager initialized");
    }

    // ==================== 基础截图 ====================

    /**
     * 截取全屏截图
     */
    public ScreenshotRecord captureFullScreenshot() {
        return captureFullScreenshot("full");
    }

    /**
     * 截取全屏截图（指定名称）
     */
    public ScreenshotRecord captureFullScreenshot(String name) {
        waitBeforeScreenshot();

        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            byte[] data = ts.getScreenshotAs(OutputType.BYTES);
            return saveScreenshot(data, name);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    /**
     * 截取元素截图
     */
    public ScreenshotRecord captureElement(WebElement element) {
        return captureElement(element, "element");
    }

    /**
     * 截取元素截图（指定名称）
     */
    public ScreenshotRecord captureElement(WebElement element, String name) {
        waitBeforeScreenshot();

        try {
            byte[] data = element.getScreenshotAs(OutputType.BYTES);
            return saveScreenshot(data, name);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    /**
     * 截取元素截图（按选择器）
     */
    public ScreenshotRecord captureBySelector(String selector) {
        try {
            WebElement element = driver.findElement(By.cssSelector(selector));
            return captureElement(element, selector);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    // ==================== 高级截图 ====================

    /**
     * 截取完整页面（滚动截图）
     */
    public ScreenshotRecord captureFullPage() {
        return captureFullPage("fullpage");
    }

    /**
     * 截取完整页面（滚动截图，指定名称）
     */
    public ScreenshotRecord captureFullPage(String name) {
        waitBeforeScreenshot();

        try {
            // 获取页面总高度
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long totalHeight = (long) js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.body.offsetHeight, document.documentElement.offsetHeight);");
            long viewportHeight = (long) js.executeScript("return window.innerHeight || document.documentElement.clientHeight;");
            long totalWidth = (long) js.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, document.body.offsetWidth, document.documentElement.offsetWidth);");

            // 计算需要滚动的次数
            int scrollCount = (int) Math.ceil((double) totalHeight / viewportHeight);

            List<BufferedImage> images = new ArrayList<>();
            int currentY = 0;
            int scrollStep = (int) Math.max(Math.max(viewportHeight - 100, 100), 1); // 保留重叠区域

            for (int i = 0; i < scrollCount; i++) {
                // 滚动到当前位置
                js.executeScript("window.scrollTo(0, " + currentY + ");");
                Thread.sleep(100); // 等待滚动完成

                // 截图
                TakesScreenshot ts = (TakesScreenshot) driver;
                byte[] data = ts.getScreenshotAs(OutputType.BYTES);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));

                images.add(img);

                // 移动到下一个位置
                currentY += scrollStep;
                int totalHeightInt = (int) Math.min(totalHeight, Integer.MAX_VALUE);
                int viewportHeightInt = (int) Math.min(viewportHeight, Integer.MAX_VALUE);
                if (currentY > totalHeightInt - viewportHeightInt) {
                    currentY = Math.max(0, totalHeightInt - viewportHeightInt);
                }
            }

            // 滚动回顶部
            js.executeScript("window.scrollTo(0, 0);");

            // 拼接图片
            int width = images.get(0).getWidth();
            int totalHeightInt = (int) Math.min(totalHeight, Integer.MAX_VALUE);
            BufferedImage fullPage = new BufferedImage(width, totalHeightInt, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = fullPage.createGraphics();

            currentY = 0;
            for (BufferedImage img : images) {
                int partHeight = img.getHeight();
                int currentYInt = Math.min(currentY, totalHeightInt);
                if (currentYInt + partHeight > totalHeightInt) {
                    partHeight = totalHeightInt - currentYInt;
                }
                g.drawImage(img, 0, currentYInt, width, partHeight, null);
                currentY += scrollStep;
            }
            g.dispose();

            // 转换为字节
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(fullPage, config.getFormat().getExtension(), baos);
            byte[] data = baos.toByteArray();

            return saveScreenshot(data, name);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    /**
     * 截取指定区域
     */
    public ScreenshotRecord captureRegion(int x, int y, int width, int height) {
        return captureRegion(x, y, width, height, "region");
    }

    /**
     * 截取指定区域（指定名称）
     */
    public ScreenshotRecord captureRegion(int x, int y, int width, int height, String name) {
        waitBeforeScreenshot();

        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            byte[] data = ts.getScreenshotAs(OutputType.BYTES);
            BufferedImage fullImage = ImageIO.read(new ByteArrayInputStream(data));

            // 裁剪指定区域
            BufferedImage region = fullImage.getSubimage(x, y, width, height);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(region, config.getFormat().getExtension(), baos);
            byte[] croppedData = baos.toByteArray();

            return saveScreenshot(croppedData, name);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    /**
     * 截取视口截图（不含滚动条区域）
     */
    public ScreenshotRecord captureViewport() {
        return captureViewport("viewport");
    }

    /**
     * 截取视口截图（指定名称）
     */
    public ScreenshotRecord captureViewport(String name) {
        waitBeforeScreenshot();

        try {
            // 使用JavaScript获取正确的视口大小
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long width = (Long) js.executeScript("return window.innerWidth || document.documentElement.clientWidth || document.body.clientWidth;");
            Long height = (Long) js.executeScript("return window.innerHeight || document.documentElement.clientHeight || document.body.clientHeight;");

            TakesScreenshot ts = (TakesScreenshot) driver;
            byte[] data = ts.getScreenshotAs(OutputType.BYTES);
            BufferedImage fullImage = ImageIO.read(new ByteArrayInputStream(data));

            // 裁剪到视口大小
            BufferedImage viewport;
            if (width != null && height != null) {
                viewport = fullImage.getSubimage(0, 0, Math.min(width.intValue(), fullImage.getWidth()), 
                                                  Math.min(height.intValue(), fullImage.getHeight()));
            } else {
                viewport = fullImage;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(viewport, config.getFormat().getExtension(), baos);

            return saveScreenshot(baos.toByteArray(), name);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    // ==================== 截图标注 ====================

    /**
     * 在截图上绘制矩形框
     */
    public void drawRectangle(byte[] screenshotData, int x, int y, int width, int height) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(screenshotData));
            Graphics2D g = img.createGraphics();

            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(3));
            g.drawRect(x, y, width, height);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, config.getFormat().getExtension(), baos);
        } catch (Exception e) {
            handleError(e);
        }
    }

    /**
     * 在截图上添加文字标注
     */
    public void drawText(byte[] screenshotData, String text, int x, int y) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(screenshotData));
            Graphics2D g = img.createGraphics();

            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.setColor(Color.RED);
            g.drawString(text, x, y);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, config.getFormat().getExtension(), baos);
        } catch (Exception e) {
            handleError(e);
        }
    }

    /**
     * 高亮元素
     */
    public void highlightElement(WebElement element, byte[] screenshotData) {
        try {
            org.openqa.selenium.Point location = element.getLocation();
            org.openqa.selenium.Dimension size = element.getSize();

            drawRectangle(screenshotData, location.getX(), location.getY(), size.getWidth(), size.getHeight());
        } catch (Exception e) {
            handleError(e);
        }
    }

    // ==================== 保存和加载 ====================

    /**
     * 保存截图
     */
    private ScreenshotRecord saveScreenshot(byte[] data, String name) {
        try {
            // 生成文件名
            String timestamp = config.isIncludeTimestamp() ? 
                LocalDateTime.now().format(FORMATTER) : "";
            String filename = config.getFilenameTemplate()
                .replace("{timestamp}", timestamp)
                .replace("{name}", name != null ? name : "screenshot")
                .replace("{date}", LocalDate.now().toString())
                + "." + config.getFormat().getExtension();

            Path filePath = savePath.resolve(filename);

            // 保存文件
            Files.write(filePath, data);

            // 创建记录
            ScreenshotRecord record = new ScreenshotRecord();
            record.setFilename(filename);
            record.setFullPath(filePath.toString());
            record.setName(name);
            record.setFileSize(data.length);
            record.setFormat(config.getFormat().getExtension());

            // 获取页面信息
            try {
                record.setUrl(driver.getCurrentUrl());
                record.setTitle(driver.getTitle());
            } catch (Exception e) {
                // ignore
            }

            // 获取视口大小
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Long width = (Long) js.executeScript("return window.innerWidth || document.documentElement.clientWidth;");
                Long height = (Long) js.executeScript("return window.innerHeight || document.documentElement.clientHeight;");
                if (width != null && height != null) {
                    record.setViewportSize(new org.openqa.selenium.Dimension(width.intValue(), height.intValue()));
                }
            } catch (Exception e) {
                // ignore
            }

            // 添加到历史
            history.add(record);
            historyIndex.put(record.getId(), record);

            // 清理旧记录
            while (history.size() > config.getMaxHistory()) {
                ScreenshotRecord old = history.remove(0);
                historyIndex.remove(old.getId());
            }

            // 执行回调
            callbacks.forEach(cb -> {
                try { cb.onScreenshot(record, data); }
                catch (Exception e) { logger.warn("Screenshot callback: {}", e.getMessage()); }
            });

            logger.debug("Screenshot saved: {}", filePath);
            return record;
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    /**
     * 获取截图数据
     */
    public byte[] getScreenshotData() {
        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            return ts.getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            handleError(e);
            return null;
        }
    }

    // ==================== 回调注册 ====================

    public ScreenshotManager onScreenshot(ScreenshotCallback callback) {
        callbacks.offer(callback);
        return this;
    }

    public ScreenshotManager onError(ErrorCallback callback) {
        errorCallbacks.offer(callback);
        return this;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取截图历史
     */
    public List<ScreenshotRecord> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 获取指定ID的截图记录
     */
    public Optional<ScreenshotRecord> getById(String id) {
        return Optional.ofNullable(historyIndex.get(id));
    }

    /**
     * 按名称搜索截图
     */
    public List<ScreenshotRecord> searchByName(String keyword) {
        List<ScreenshotRecord> results = new ArrayList<>();
        for (ScreenshotRecord record : history) {
            if (record.getName() != null && record.getName().contains(keyword)) {
                results.add(record);
            }
        }
        return results;
    }

    /**
     * 获取最新截图
     */
    public Optional<ScreenshotRecord> getLatest() {
        synchronized (history) {
            if (history.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(history.get(history.size() - 1));
        }
    }

    /**
     * 清空历史
     */
    public void clearHistory() {
        history.clear();
        historyIndex.clear();
        logger.info("Screenshot history cleared");
    }

    // ==================== 辅助方法 ====================

    private void waitBeforeScreenshot() {
        if (config.isWaitForPageLoad()) {
            try {
                Thread.sleep(config.getWaitBeforeScreenshot());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleError(Exception e) {
        errorCallbacks.forEach(cb -> {
            try { cb.onError(e); }
            catch (Exception ex) { logger.warn("Error callback: {}", ex.getMessage()); }
        });
        logger.warn("Screenshot error: {}", e.getMessage());
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScreenshots", history.size());
        
        long totalSize = 0;
        for (ScreenshotRecord record : history) {
            totalSize += record.getFileSize();
        }
        stats.put("totalSizeBytes", totalSize);
        stats.put("totalSizeMB", String.format("%.2f", totalSize / (1024.0 * 1024.0)));
        stats.put("saveDirectory", savePath.toString());
        
        return stats;
    }

    public void printStatistics() {
        Map<String, Object> stats = getStatistics();
        logger.info("=== Screenshot Statistics ===");
        logger.info("Total: {}, Total Size: {}", stats.get("totalScreenshots"), stats.get("totalSizeMB"));
        logger.info("Save Directory: {}", stats.get("saveDirectory"));
    }

    // ==================== 静态工厂方法 ====================

    public static ScreenshotManager create(WebDriver driver) {
        return new ScreenshotManager(driver);
    }

    public static ScreenshotManager create(WebDriver driver, ScreenshotConfig config) {
        return new ScreenshotManager(driver, config);
    }

    public static ScreenshotManager createHighQuality(WebDriver driver) {
        return new ScreenshotManager(driver, ScreenshotConfig.highQualityConfig());
    }

    // ==================== AutoCloseable ====================

    @Override
    public void close() {
        callbacks.clear();
        errorCallbacks.clear();
        logger.info("ScreenshotManager closed");
    }
}
