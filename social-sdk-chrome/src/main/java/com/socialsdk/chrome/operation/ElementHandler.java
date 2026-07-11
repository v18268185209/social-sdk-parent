package com.socialsdk.chrome.operation;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 元素操作器
 * 提供灵活的Web元素定位和操作功能
 */
public class ElementHandler {

    private static final Logger logger = LoggerFactory.getLogger(ElementHandler.class);

    // ==================== 定位策略 ====================

    public enum LocatorType {
        ID,
        NAME,
        CLASS_NAME,
        TAG_NAME,
        LINK_TEXT,
        PARTIAL_LINK_TEXT,
        CSS_SELECTOR,
        XPATH,
        JQUERY,
        JS_PATH
    }

    // ==================== 操作配置 ====================

    public static class ElementConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 默认超时时间
         */
        private Duration defaultTimeout = Duration.ofSeconds(10);

        /**
         * 默认轮询间隔
         */
        private Duration pollInterval = Duration.ofMillis(500);

        /**
         * 点击前是否滚动到可见
         */
        private boolean scrollBeforeClick = true;

        /**
         * 点击前是否清空输入框
         */
        private boolean clearBeforeInput = true;

        /**
         * 输入后是否模拟Tab键
         */
        private boolean simulateTabAfterInput = true;

        /**
         * 是否高亮元素（调试用）
         */
        private boolean highlightOnFind = false;

        /**
         * 高亮持续时间（毫秒）
         */
        private int highlightDuration = 500;

        /**
         * 截图保存目录
         */
        private String screenshotDir;

        /**
         * 重试次数
         */
        private int retryCount = 3;

        /**
         * 重试间隔
         */
        private Duration retryInterval = Duration.ofMillis(1000);

        // Getters and Setters
        public Duration getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(Duration defaultTimeout) { this.defaultTimeout = defaultTimeout; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
        public boolean isScrollBeforeClick() { return scrollBeforeClick; }
        public void setScrollBeforeClick(boolean scrollBeforeClick) { this.scrollBeforeClick = scrollBeforeClick; }
        public boolean isClearBeforeInput() { return clearBeforeInput; }
        public void setClearBeforeInput(boolean clearBeforeInput) { this.clearBeforeInput = clearBeforeInput; }
        public boolean isSimulateTabAfterInput() { return simulateTabAfterInput; }
        public void setSimulateTabAfterInput(boolean simulateTabAfterInput) { this.simulateTabAfterInput = simulateTabAfterInput; }
        public boolean isHighlightOnFind() { return highlightOnFind; }
        public void setHighlightOnFind(boolean highlightOnFind) { this.highlightOnFind = highlightOnFind; }
        public int getHighlightDuration() { return highlightDuration; }
        public void setHighlightDuration(int highlightDuration) { this.highlightDuration = highlightDuration; }
        public String getScreenshotDir() { return screenshotDir; }
        public void setScreenshotDir(String screenshotDir) { this.screenshotDir = screenshotDir; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public Duration getRetryInterval() { return retryInterval; }
        public void setRetryInterval(Duration retryInterval) { this.retryInterval = retryInterval; }

        /**
         * 默认配置
         */
        public static ElementConfig defaultConfig() {
            return new ElementConfig();
        }

        /**
         * 精准配置（更长的超时）
         */
        public static ElementConfig preciseConfig() {
            ElementConfig config = new ElementConfig();
            config.setDefaultTimeout(Duration.ofSeconds(30));
            config.setRetryCount(5);
            return config;
        }

        /**
         * 快速配置（更短的超时）
         */
        public static ElementConfig quickConfig() {
            ElementConfig config = new ElementConfig();
            config.setDefaultTimeout(Duration.ofSeconds(3));
            config.setRetryCount(1);
            return config;
        }
    }

    // ==================== 元素定位器 ====================

    public static class ElementLocator implements Serializable {
        private static final long serialVersionUID = 1L;

        private final LocatorType type;
        private final String value;
        private final String description;

        public ElementLocator(LocatorType type, String value) {
            this(type, value, null);
        }

        public ElementLocator(LocatorType type, String value, String description) {
            this.type = type;
            this.value = value;
            this.description = description != null ? description : type.name() + ": " + value;
        }

        public By toBy() {
            switch (type) {
                case ID:
                    return By.id(value);
                case NAME:
                    return By.name(value);
                case CLASS_NAME:
                    return By.className(value);
                case TAG_NAME:
                    return By.tagName(value);
                case LINK_TEXT:
                    return By.linkText(value);
                case PARTIAL_LINK_TEXT:
                    return By.partialLinkText(value);
                case CSS_SELECTOR:
                    return By.cssSelector(value);
                case XPATH:
                    return By.xpath(value);
                default:
                    throw new IllegalArgumentException("Unsupported locator type: " + type);
            }
        }

        public LocatorType getType() { return type; }
        public String getValue() { return value; }
        public String getDescription() { return description; }

        // 静态工厂方法
        public static ElementLocator byId(String value) {
            return new ElementLocator(LocatorType.ID, value);
        }
        public static ElementLocator byName(String value) {
            return new ElementLocator(LocatorType.NAME, value);
        }
        public static ElementLocator byClassName(String value) {
            return new ElementLocator(LocatorType.CLASS_NAME, value);
        }
        public static ElementLocator byTagName(String value) {
            return new ElementLocator(LocatorType.TAG_NAME, value);
        }
        public static ElementLocator byLinkText(String value) {
            return new ElementLocator(LocatorType.LINK_TEXT, value);
        }
        public static ElementLocator byPartialLinkText(String value) {
            return new ElementLocator(LocatorType.PARTIAL_LINK_TEXT, value);
        }
        public static ElementLocator byCssSelector(String value) {
            return new ElementLocator(LocatorType.CSS_SELECTOR, value);
        }
        public static ElementLocator byXPath(String value) {
            return new ElementLocator(LocatorType.XPATH, value);
        }
    }

    // ==================== 元素操作结果 ====================

    public static class ElementResult<T> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final boolean success;
        private final T element;
        private final String message;
        private final Instant timestamp;

        public ElementResult(boolean success, T element, String message) {
            this.success = success;
            this.element = element;
            this.message = message;
            this.timestamp = Instant.now();
        }

        public static <T> ElementResult<T> success(T element, String message) {
            return new ElementResult<>(true, element, message);
        }
        public static <T> ElementResult<T> failure(String message) {
            return new ElementResult<>(false, null, message);
        }

        public boolean isSuccess() { return success; }
        public T getElement() { return element; }
        public String getMessage() { return message; }
        public Instant getTimestamp() { return timestamp; }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final ElementConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // ==================== 构造函数 ====================

    public ElementHandler(WebDriver driver) {
        this(driver, ElementConfig.defaultConfig());
    }

    public ElementHandler(WebDriver driver, ElementConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        this.wait = new WebDriverWait(driver, config.getDefaultTimeout());
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.debug("ElementHandler initialized");
        }
    }

    // ==================== 元素定位 ====================

    /**
     * 查找单个元素
     */
    public Optional<WebElement> findElement(ElementLocator locator) {
        return findElement(locator, config.getDefaultTimeout());
    }

    /**
     * 查找单个元素（指定超时）
     */
    public Optional<WebElement> findElement(ElementLocator locator, Duration timeout) {
        try {
            WebDriverWait customWait = new WebDriverWait(driver, timeout);
            customWait.pollingEvery(config.getPollInterval());
            WebElement element = customWait.until(
                ExpectedConditions.presenceOfElementLocated(locator.toBy())
            );

            if (config.isHighlightOnFind()) {
                highlight(element);
            }

            logger.debug("Found element: {}", locator.getDescription());
            return Optional.ofNullable(element);
        } catch (TimeoutException e) {
            logger.debug("Element not found within timeout: {}", locator.getDescription());
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Error finding element: {} - {}", locator.getDescription(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查找单个元素（可点击状态）
     */
    public Optional<WebElement> findClickableElement(ElementLocator locator) {
        return findClickableElement(locator, config.getDefaultTimeout());
    }

    /**
     * 查找单个元素（可点击状态，指定超时）
     */
    public Optional<WebElement> findClickableElement(ElementLocator locator, Duration timeout) {
        try {
            WebDriverWait customWait = new WebDriverWait(driver, timeout);
            WebElement element = customWait.until(
                ExpectedConditions.elementToBeClickable(locator.toBy())
            );

            if (config.isHighlightOnFind()) {
                highlight(element);
            }

            return Optional.ofNullable(element);
        } catch (TimeoutException e) {
            logger.debug("Clickable element not found: {}", locator.getDescription());
            return Optional.empty();
        }
    }

    /**
     * 查找多个元素
     */
    public List<WebElement> findElements(ElementLocator locator) {
        try {
            List<WebElement> elements = driver.findElements(locator.toBy());
            logger.debug("Found {} elements: {}", elements.size(), locator.getDescription());
            return elements;
        } catch (Exception e) {
            logger.warn("Error finding elements: {} - {}", locator.getDescription(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查找可见元素
     */
    public List<WebElement> findVisibleElements(ElementLocator locator) {
        try {
            List<WebElement> allElements = driver.findElements(locator.toBy());
            List<WebElement> visibleElements = new ArrayList<>();

            for (WebElement element : allElements) {
                if (isVisible(element)) {
                    visibleElements.add(element);
                }
            }

            return visibleElements;
        } catch (Exception e) {
            logger.warn("Error finding visible elements: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查找嵌套元素
     */
    public Optional<WebElement> findChildElement(WebElement parent, ElementLocator locator) {
        try {
            WebElement child = parent.findElement(locator.toBy());
            if (config.isHighlightOnFind()) {
                highlight(child);
            }
            return Optional.ofNullable(child);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 等待元素出现
     */
    public boolean waitForPresence(ElementLocator locator) {
        return waitForPresence(locator, config.getDefaultTimeout());
    }

    /**
     * 等待元素出现（指定超时）
     */
    public boolean waitForPresence(ElementLocator locator, Duration timeout) {
        try {
            WebDriverWait customWait = new WebDriverWait(driver, timeout);
            customWait.until(ExpectedConditions.presenceOfElementLocated(locator.toBy()));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * 等待元素消失
     */
    public boolean waitForAbsence(ElementLocator locator) {
        return waitForAbsence(locator, config.getDefaultTimeout());
    }

    /**
     * 等待元素消失（指定超时）
     */
    public boolean waitForAbsence(ElementLocator locator, Duration timeout) {
        try {
            WebDriverWait customWait = new WebDriverWait(driver, timeout);
            customWait.until(ExpectedConditions.invisibilityOfElementLocated(locator.toBy()));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ==================== 元素点击 ====================

    /**
     * 点击元素
     */
    public ElementResult<WebElement> click(ElementLocator locator) {
        return click(locator, false);
    }

    /**
     * 点击元素（强制点击）
     */
    public ElementResult<WebElement> click(ElementLocator locator, boolean force) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = force
                ? findElement(locator)
                : findClickableElement(locator);

            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found or not clickable: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();

            if (config.isScrollBeforeClick()) {
                scrollToElement(element);
            }

            try {
                element.click();
                logger.debug("Clicked element: {}", locator.getDescription());
                return ElementResult.success(element, "Clicked successfully");
            } catch (Exception e) {
                // 备选：使用JavaScript点击
                return jsClick(element, locator.getDescription());
            }
        });
    }

    /**
     * 双击元素
     */
    public ElementResult<WebElement> doubleClick(ElementLocator locator) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            scrollToElement(element);

            new Actions(driver)
                .doubleClick(element)
                .perform();

            logger.debug("Double-clicked element: {}", locator.getDescription());
            return ElementResult.success(element, "Double-clicked successfully");
        });
    }

    /**
     * 右键点击元素
     */
    public ElementResult<WebElement> rightClick(ElementLocator locator) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            scrollToElement(element);

            new Actions(driver)
                .contextClick(element)
                .perform();

            logger.debug("Right-clicked element: {}", locator.getDescription());
            return ElementResult.success(element, "Right-clicked successfully");
        });
    }

    /**
     * 悬停元素
     */
    public ElementResult<WebElement> hover(ElementLocator locator) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            scrollToElement(element);

            new Actions(driver)
                .moveToElement(element)
                .perform();

            logger.debug("Hovered element: {}", locator.getDescription());
            return ElementResult.success(element, "Hovered successfully");
        });
    }

    // ==================== 元素输入 ====================

    /**
     * 输入文本
     */
    public ElementResult<WebElement> input(ElementLocator locator, String text) {
        return input(locator, text, true);
    }

    /**
     * 输入文本（可选择是否清空）
     */
    public ElementResult<WebElement> input(ElementLocator locator, String text, boolean clear) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            scrollToElement(element);

            if (clear && config.isClearBeforeInput()) {
                element.clear();
            }

            try {
                element.sendKeys(text);

                if (config.isSimulateTabAfterInput()) {
                    element.sendKeys(Keys.TAB);
                }

                logger.debug("Input text into element: {}", locator.getDescription());
                return ElementResult.success(element, "Input successful");
            } catch (Exception e) {
                // 备选：使用JavaScript输入
                return jsInput(element, text, locator.getDescription());
            }
        });
    }

    /**
     * 追加输入文本
     */
    public ElementResult<WebElement> appendInput(ElementLocator locator, String text) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            scrollToElement(element);

            element.sendKeys(text);
            logger.debug("Appended text to element: {}", locator.getDescription());
            return ElementResult.success(element, "Appended successfully");
        });
    }

    /**
     * 清空输入框
     */
    public ElementResult<WebElement> clear(ElementLocator locator) {
        return executeWithRetry(() -> {
            Optional<WebElement> elementOpt = findElement(locator);
            if (!elementOpt.isPresent()) {
                return ElementResult.failure("Element not found: " + locator.getDescription());
            }

            WebElement element = elementOpt.get();
            element.clear();
            logger.debug("Cleared element: {}", locator.getDescription());
            return ElementResult.success(element, "Cleared successfully");
        });
    }

    /**
     * 输入特殊键
     */
    public void sendKeys(ElementLocator locator, Keys keys) {
        findElement(locator).ifPresent(element -> {
            try {
                element.sendKeys(keys);
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new KeyboardEvent('keydown', {key: arguments[1], code: arguments[1]}));",
                    element, keys.name()
                );
            }
        });
    }

    // ==================== 元素信息获取 ====================

    /**
     * 获取元素文本
     */
    public Optional<String> getText(ElementLocator locator) {
        return findElement(locator)
            .map(element -> {
                try {
                    return element.getText();
                } catch (Exception e) {
                    return (String) ((JavascriptExecutor) driver).executeScript(
                        "return arguments[0].textContent || arguments[0].innerText;", element
                    );
                }
            });
    }

    /**
     * 获取元素属性
     */
    public Optional<String> getAttribute(ElementLocator locator, String attribute) {
        return findElement(locator)
            .map(element -> {
                try {
                    return element.getAttribute(attribute);
                } catch (Exception e) {
                    return null;
                }
            });
    }

    /**
     * 获取元素CSS值
     */
    public Optional<String> getCssValue(ElementLocator locator, String cssProperty) {
        return findElement(locator)
            .map(element -> element.getCssValue(cssProperty));
    }

    /**
     * 获取元素标签名
     */
    public Optional<String> getTagName(ElementLocator locator) {
        return findElement(locator).map(WebElement::getTagName);
    }

    /**
     * 获取元素位置
     */
    public Optional<Point> getLocation(ElementLocator locator) {
        return findElement(locator).map(WebElement::getLocation);
    }

    /**
     * 获取元素大小
     */
    public Optional<Dimension> getSize(ElementLocator locator) {
        return findElement(locator).map(WebElement::getSize);
    }

    /**
     * 获取元素矩形信息
     */
    public Optional<Rectangle> getRect(ElementLocator locator) {
        return findElement(locator).map(element -> {
            Point p = element.getLocation();
            Dimension d = element.getSize();
            return new Rectangle(p, d);
        });
    }

    // ==================== 元素状态检查 ====================

    /**
     * 元素是否可见
     */
    public boolean isVisible(ElementLocator locator) {
        return findElement(locator)
            .map(this::isVisible)
            .orElse(false);
    }

    /**
     * 元素是否可见（内部方法）
     */
    private boolean isVisible(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 元素是否可用
     */
    public boolean isEnabled(ElementLocator locator) {
        return findElement(locator)
            .map(element -> {
                try {
                    return element.isEnabled();
                } catch (Exception e) {
                    return false;
                }
            })
            .orElse(false);
    }

    /**
     * 元素是否被选中
     */
    public boolean isSelected(ElementLocator locator) {
        return findElement(locator)
            .map(element -> {
                try {
                    return element.isSelected();
                } catch (Exception e) {
                    return false;
                }
            })
            .orElse(false);
    }

    /**
     * 元素是否存在
     */
    public boolean exists(ElementLocator locator) {
        return !findElements(locator).isEmpty();
    }

    // ==================== 元素滚动 ====================

    /**
     * 滚动到元素可见
     */
    public void scrollToElement(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                element
            );
            // 等待滚动完成
            Thread.sleep(300);
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView(true);",
                element
            );
        }
    }

    /**
     * 滚动到元素可见（指定位置）
     */
    public void scrollToElement(WebElement element, String block) {
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].scrollIntoView({behavior: 'smooth', block: arguments[1]});",
            element, block
        );
    }

    /**
     * 滚动到指定位置
     */
    public void scrollTo(int x, int y) {
        ((JavascriptExecutor) driver).executeScript(
            "window.scrollTo(arguments[0], arguments[1]);", x, y
        );
    }

    /**
     * 滚动到顶部
     */
    public void scrollToTop() {
        ((JavascriptExecutor) driver).executeScript(
            "window.scrollTo(0, 0);"
        );
    }

    /**
     * 滚动到底部
     */
    public void scrollToBottom() {
        ((JavascriptExecutor) driver).executeScript(
            "window.scrollTo(0, document.body.scrollHeight);"
        );
    }

    // ==================== 拖拽操作 ====================

    /**
     * 拖拽元素到目标位置
     */
    public ElementResult<WebElement> dragAndDrop(ElementLocator source, ElementLocator target) {
        return executeWithRetry(() -> {
            Optional<WebElement> sourceOpt = findElement(source);
            Optional<WebElement> targetOpt = findElement(target);

            if (!sourceOpt.isPresent()) {
                return ElementResult.failure("Source element not found: " + source.getDescription());
            }
            if (!targetOpt.isPresent()) {
                return ElementResult.failure("Target element not found: " + target.getDescription());
            }

            scrollToElement(sourceOpt.get());

            new Actions(driver)
                .dragAndDrop(sourceOpt.get(), targetOpt.get())
                .perform();

            logger.debug("Dragged element from {} to {}", source.getDescription(), target.getDescription());
            return ElementResult.success(sourceOpt.get(), "Drag and drop successful");
        });
    }

    /**
     * 拖拽元素指定距离
     */
    public ElementResult<WebElement> dragAndDropBy(ElementLocator source, int offsetX, int offsetY) {
        return executeWithRetry(() -> {
            Optional<WebElement> sourceOpt = findElement(source);
            if (!sourceOpt.isPresent()) {
                return ElementResult.failure("Source element not found: " + source.getDescription());
            }

            WebElement element = sourceOpt.get();
            scrollToElement(element);

            new Actions(driver)
                .dragAndDropBy(element, offsetX, offsetY)
                .perform();

            logger.debug("Dragged element by offset ({}, {})", offsetX, offsetY);
            return ElementResult.success(element, "Drag by offset successful");
        });
    }

    // ==================== 截图操作 ====================

    /**
     * 截图元素
     */
    public File screenshotElement(ElementLocator locator) {
        return findElement(locator)
            .map(element -> {
                try {
                    return element.getScreenshotAs(OutputType.FILE);
                } catch (Exception e) {
                    logger.warn("Failed to screenshot element: {}", e.getMessage());
                    return null;
                }
            })
            .orElse(null);
    }

    /**
     * 截图并保存
     */
    public String screenshotElementToFile(ElementLocator locator, String filename) {
        File screenshot = screenshotElement(locator);
        if (screenshot == null) {
            return null;
        }

        try {
            if (config.getScreenshotDir() != null) {
                File dir = new File(config.getScreenshotDir());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File dest = new File(dir, filename);
                java.nio.file.Files.copy(
                    screenshot.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                return dest.getAbsolutePath();
            }
        } catch (Exception e) {
            logger.warn("Failed to save element screenshot: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 辅助方法 ====================

    /**
     * 高亮元素
     */
    private void highlight(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].style.border='3px solid red';" +
                "arguments[0].style.boxShadow='0 0 10px rgba(255,0,0,0.5)';" +
                "setTimeout(function() {" +
                "  arguments[0].style.border='';" +
                "  arguments[0].style.boxShadow='';" +
                "}, arguments[1]);",
                element, config.getHighlightDuration()
            );
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * JavaScript点击
     */
    private ElementResult<WebElement> jsClick(WebElement element, String description) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].click();", element
            );
            logger.debug("JS clicked element: {}", description);
            return ElementResult.success(element, "JS click successful");
        } catch (Exception e) {
            return ElementResult.failure("JS click failed: " + e.getMessage());
        }
    }

    /**
     * JavaScript输入
     */
    private ElementResult<WebElement> jsInput(WebElement element, String text, String description) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];", element, text
            );
            logger.debug("JS input to element: {}", description);
            return ElementResult.success(element, "JS input successful");
        } catch (Exception e) {
            return ElementResult.failure("JS input failed: " + e.getMessage());
        }
    }

    /**
     * 带重试的执行
     */
    private <T> ElementResult<T> executeWithRetry(java.util.function.Supplier<ElementResult<T>> action) {
        Exception lastException = null;

        for (int i = 0; i <= config.getRetryCount(); i++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                logger.debug("Retry {} failed: {}", i + 1, e.getMessage());

                if (i < config.getRetryCount()) {
                    try {
                        Thread.sleep(config.getRetryInterval().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return ElementResult.failure("Interrupted during retry");
                    }
                }
            }
        }

        return ElementResult.failure("Operation failed after " + config.getRetryCount() + " retries: " +
            (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    /**
     * 等待页面加载
     */
    public void waitForPageLoad() {
        wait.until(driver -> {
            Object result = ((JavascriptExecutor) driver)
                .executeScript("return document.readyState");
            String state = result != null ? result.toString() : "";
            return "complete".equals(state);
        });
    }

    /**
     * 等待页面完全空闲
     */
    public void waitForPageIdle() {
        try {
            Thread.sleep(500);
            ((JavascriptExecutor) driver).executeScript(
                "var callback = arguments[0];" +
                "if (document.readyState !== 'complete') {" +
                "  callback(false);" +
                "  return;" +
                "}" +
                "var interval = setInterval(function() {" +
                "  if (document.readyState === 'complete' &&" +
                "      typeof window.jQuery === 'undefined' ||" +
                "      window.jQuery.active === 0) {" +
                "    clearInterval(interval);" +
                "    callback(true);" +
                "  }" +
                "}, 100);" +
                "setTimeout(function() { clearInterval(interval); callback(false); }, arguments[1]);",
                Duration.ofSeconds(30).toMillis()
            );
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 静态方法 ====================

    public static ElementHandler create(WebDriver driver) {
        return new ElementHandler(driver);
    }

    public static ElementHandler create(WebDriver driver, ElementConfig config) {
        return new ElementHandler(driver, config);
    }
}
