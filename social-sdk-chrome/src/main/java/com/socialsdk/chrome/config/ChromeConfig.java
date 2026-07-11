package com.socialsdk.chrome.config;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chrome浏览器配置
 */
public class ChromeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Chrome可执行文件路径（默认自动下载）
     */
    private String executablePath;

    /**
     * 浏览器窗口大小
     */
    private String windowSize = "1920,1080";

    /**
     * 是否无头模式运行
     */
    private boolean headless = false;

    /**
     * 页面加载策略
     */
    private PageLoadStrategy pageLoadStrategy = PageLoadStrategy.NORMAL;

    /**
     * 用户数据目录（用于保存cookies和session）
     */
    private String userDataDir;

    /**
     * 扩展程序路径列表
     */
    private List<String> extensionPaths = new ArrayList<>();

    /**
     * 浏览器启动参数
     */
    private List<String> arguments = new ArrayList<>();

    /**
     * 实验性选项
     */
    private Map<String, Object> experimentalOptions = new HashMap<>();

    /**
     * 下载目录
     */
    private String downloadDir;

    /**
     * 页面加载超时时间（毫秒）
     */
    private long pageLoadTimeout = 30000;

    /**
     * 隐式等待时间（毫秒）
     */
    private long implicitWaitTimeout = 5000;

    /**
     * 脚本超时时间（毫秒）
     */
    private long scriptTimeout = 30000;

    /**
     * 是否禁用GPU
     */
    private boolean disableGpu = true;

    /**
     * 是否禁用沙箱
     */
    private boolean noSandbox = true;

    /**
     * 是否禁用DevTools日志
     */
    private boolean disableDevShmUsage = true;

    // Getters and Setters
    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(String windowSize) {
        this.windowSize = windowSize;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public PageLoadStrategy getPageLoadStrategy() {
        return pageLoadStrategy;
    }

    public void setPageLoadStrategy(PageLoadStrategy pageLoadStrategy) {
        this.pageLoadStrategy = pageLoadStrategy;
    }

    public String getUserDataDir() {
        return userDataDir;
    }

    public void setUserDataDir(String userDataDir) {
        this.userDataDir = userDataDir;
    }

    public List<String> getExtensionPaths() {
        return extensionPaths;
    }

    public void setExtensionPaths(List<String> extensionPaths) {
        this.extensionPaths = extensionPaths;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public Map<String, Object> getExperimentalOptions() {
        return experimentalOptions;
    }

    public void setExperimentalOptions(Map<String, Object> experimentalOptions) {
        this.experimentalOptions = experimentalOptions;
    }

    public String getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    public long getPageLoadTimeout() {
        return pageLoadTimeout;
    }

    public void setPageLoadTimeout(long pageLoadTimeout) {
        this.pageLoadTimeout = pageLoadTimeout;
    }

    public long getImplicitWaitTimeout() {
        return implicitWaitTimeout;
    }

    public void setImplicitWaitTimeout(long implicitWaitTimeout) {
        this.implicitWaitTimeout = implicitWaitTimeout;
    }

    public long getScriptTimeout() {
        return scriptTimeout;
    }

    public void setScriptTimeout(long scriptTimeout) {
        this.scriptTimeout = scriptTimeout;
    }

    public boolean isDisableGpu() {
        return disableGpu;
    }

    public void setDisableGpu(boolean disableGpu) {
        this.disableGpu = disableGpu;
    }

    public boolean isNoSandbox() {
        return noSandbox;
    }

    public void setNoSandbox(boolean noSandbox) {
        this.noSandbox = noSandbox;
    }

    public boolean isDisableDevShmUsage() {
        return disableDevShmUsage;
    }

    public void setDisableDevShmUsage(boolean disableDevShmUsage) {
        this.disableDevShmUsage = disableDevShmUsage;
    }

    public void addArgument(String arg) {
        this.arguments.add(arg);
    }

    public void addExtension(String path) {
        this.extensionPaths.add(path);
    }

    public void addExperimentalOption(String key, Object value) {
        this.experimentalOptions.put(key, value);
    }

    /**
     * 构建ChromeOptions
     */
    public ChromeOptions toChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Set basic arguments
        if (headless) {
            options.addArguments("--headless");
        }
        options.addArguments("--window-size=" + windowSize);
        options.addArguments("--disable-gpu=" + disableGpu);
        options.addArguments("--no-sandbox=" + noSandbox);
        options.addArguments("--disable-dev-shm-usage=" + disableDevShmUsage);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Page load strategy
        options.setPageLoadStrategy(pageLoadStrategy);

        // Custom arguments
        arguments.forEach(options::addArguments);

        // Extensions
        if (!extensionPaths.isEmpty()) {
            List<File> extensionFiles = new ArrayList<>();
            for (String path : extensionPaths) {
                extensionFiles.add(new File(path));
            }
            options.addExtensions(extensionFiles);
        }

        // Experimental options
        experimentalOptions.forEach(options::setExperimentalOption);

        // User data directory
        if (userDataDir != null && !userDataDir.isEmpty()) {
            options.addArguments("--user-data-dir=" + userDataDir);
        }

        // Download directory
        if (downloadDir != null && !downloadDir.isEmpty()) {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadDir);
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            prefs.put("safebrowsing.enabled", true);
            options.setExperimentalOption("prefs", prefs);
        }

        // Executable path
        if (executablePath != null && !executablePath.isEmpty()) {
            options.setBinary(executablePath);
        }

        return options;
    }
}
