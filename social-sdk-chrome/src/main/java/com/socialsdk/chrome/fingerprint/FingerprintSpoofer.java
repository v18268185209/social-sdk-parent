package com.socialsdk.chrome.fingerprint;

import com.socialsdk.chrome.config.FingerprintConfig;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指纹伪装器
 * 提供全面的浏览器指纹伪装功能，包括Canvas、WebGL、Audio、Navigator等
 */
public class FingerprintSpoofer {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintSpoofer.class);

    private final FingerprintConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Object> spoofedValues = new ConcurrentHashMap<>();

    // JavaScript注入代码缓存
    private String cachedJavascript;

    public FingerprintSpoofer() {
        this(new FingerprintConfig());
    }

    public FingerprintSpoofer(FingerprintConfig config) {
        this.config = config;
        this.cachedJavascript = config.generateJavascriptInjection();
    }

    /**
     * 应用指纹伪装到WebDriver
     */
    public void apply(WebDriver driver) {
        if (config == null || !config.isEnabled()) {
            logger.debug("Fingerprint spoofing is disabled");
            return;
        }

        if (driver == null) {
            logger.warn("Cannot apply fingerprint spoofing: WebDriver is null");
            return;
        }

        try {
            // 注入JavaScript伪装
            injectJavascript(driver);
            
            // 伪装Navigator属性
            if (config.isNavigatorSpoofEnabled()) {
                spoofNavigator(driver);
            }

            // 伪装Screen属性
            if (config.isScreenSpoofEnabled()) {
                spoofScreen(driver);
            }

            // 伪装Timezone
            if (config.isTimezoneSpoofEnabled()) {
                spoofTimezone(driver);
            }

            // 伪装Language
            if (config.isLanguageSpoofEnabled()) {
                spoofLanguage(driver);
            }

            // 伪装WebRTC
            if (config.isWebrtcSpoofEnabled()) {
                spoofWebRTC(driver);
            }

            // 伪装Connection
            if (config.getConnectionType() != null) {
                spoofConnection(driver);
            }

            // 移除自动化特征
            if (config.isRemoveAutomationFlags()) {
                removeAutomationFlags(driver);
            }

            initialized.set(true);
            logger.info("Fingerprint spoofing applied successfully");
        } catch (Exception e) {
            logger.error("Failed to apply fingerprint spoofing", e);
        }
    }

    /**
     * 注入JavaScript伪装代码
     */
    private void injectJavascript(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 执行指纹伪装JS
            js.executeScript(cachedJavascript);
            
            // 注入Canvas指纹保护
            if (config.isCanvasSpoofEnabled()) {
                injectCanvasProtection(driver);
            }

            // 注入WebGL指纹保护
            if (config.isWebglSpoofEnabled()) {
                injectWebGLProtection(driver);
            }

            // 注入Audio指纹保护
            if (config.isAudioSpoofEnabled()) {
                injectAudioProtection(driver);
            }

            logger.debug("JavaScript fingerprint protection injected");
        } catch (Exception e) {
            logger.warn("Failed to inject JavaScript fingerprint protection", e);
        }
    }

    /**
     * 注入Canvas指纹保护
     */
    private void injectCanvasProtection(WebDriver driver) {
        String canvasScript = buildCanvasProtectionScript();
        try {
            ((JavascriptExecutor) driver).executeScript(canvasScript);
        } catch (Exception e) {
            logger.debug("Canvas protection injection failed", e);
        }
    }

    /**
     * 构建Canvas保护脚本
     */
    private String buildCanvasProtectionScript() {
        long seed = config.getCanvasSeed() != null ? config.getCanvasSeed() : System.currentTimeMillis();
        float noiseLevel = config.getCanvasNoiseLevel();

        return String.format(
            "(function() {\n" +
            "  const canvasElement = HTMLCanvasElement.prototype;\n" +
            "  const originalGetContext = canvasElement.getContext;\n" +
            "  const noiseLevel = %f;\n" +
            "  const seed = %d;\n" +
            "  \n" +
            "  function seededRandom(s) {\n" +
            "    let x = Math.sin(s++) * 10000;\n" +
            "    return x - Math.floor(x);\n" +
            "  }\n" +
            "  \n" +
            "  canvasElement.getContext = function(type) {\n" +
            "    const context = originalGetContext.apply(this, arguments);\n" +
            "    if (type === '2d') {\n" +
            "      const originalFillText = context.fillText;\n" +
            "      const originalStrokeText = context.strokeText;\n" +
            "      const originalFillRect = context.fillRect;\n" +
            "      \n" +
            "      context.fillText = function(text, x, y, maxWidth) {\n" +
            "        let noise = seededRandom(seed) * noiseLevel;\n" +
            "        return originalFillText.call(this, text, x + noise, y + noise, maxWidth);\n" +
            "      };\n" +
            "      \n" +
            "      context.strokeText = function(text, x, y, maxWidth) {\n" +
            "        let noise = seededRandom(seed + 1) * noiseLevel;\n" +
            "        return originalStrokeText.call(this, text, x + noise, y + noise, maxWidth);\n" +
            "      };\n" +
            "      \n" +
            "      context.fillRect = function(x, y, w, h) {\n" +
            "        let noise = seededRandom(seed + 2) * noiseLevel;\n" +
            "        return originalFillRect.call(this, x + noise, y + noise, w, h);\n" +
            "      };\n" +
            "    }\n" +
            "    return context;\n" +
            "  };\n" +
            "  \n" +
            "  // 保护toDataURL\n" +
            "  const originalToDataURL = canvasElement.toDataURL;\n" +
            "  canvasElement.toDataURL = function() {\n" +
            "    let noise = seededRandom(seed + 3) * noiseLevel;\n" +
            "    // 在返回前添加微小的扰动\n" +
            "    return originalToDataURL.apply(this, arguments);\n" +
            "  };\n" +
            "})();", noiseLevel, seed);
    }

    /**
     * 注入WebGL指纹保护
     */
    private void injectWebGLProtection(WebDriver driver) {
        String webglScript = buildWebGLProtectionScript();
        try {
            ((JavascriptExecutor) driver).executeScript(webglScript);
        } catch (Exception e) {
            logger.debug("WebGL protection injection failed", e);
        }
    }

    /**
     * 构建WebGL保护脚本
     */
    private String buildWebGLProtectionScript() {
        String vendor = config.getWebglVendor() != null ? 
            config.getWebglVendor().replace("'", "\\'") : "Google Inc.";
        String renderer = config.getWebglRenderer() != null ? 
            config.getWebglRenderer().replace("'", "\\'") : "ANGLE (NVIDIA GeForce GTX 1660 Super Direct3D11 vs_0_0 mb_0_0)";

        return String.format(
            "(function() {\n" +
            "  try {\n" +
            "    const webglElement = HTMLCanvasElement.prototype;\n" +
            "    \n" +
            "    // 伪装WEBGL_debug_renderer_info\n" +
            "    const gl = document.createElement('canvas').getContext('webgl2');\n" +
            "    if (gl) {\n" +
            "      const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');\n" +
            "      if (debugInfo) {\n" +
            "        gl.getParameter = (function(original) {\n" +
            "          return function(param) {\n" +
            "            if (param === debugInfo.UNMASKED_VENDOR_WEBGL) {\n" +
            "              return '%s';\n" +
            "            }\n" +
            "            if (param === debugInfo.UNMASKED_RENDERER_WEBGL) {\n" +
            "              return '%s';\n" +
            "            }\n" +
            "            return original.apply(this, arguments);\n" +
            "          };\n" +
            "        })(gl.getParameter);\n" +
            "      }\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
            "})();", vendor, renderer);
    }

    /**
     * 注入Audio指纹保护
     */
    private void injectAudioProtection(WebDriver driver) {
        String audioScript = buildAudioProtectionScript();
        try {
            ((JavascriptExecutor) driver).executeScript(audioScript);
        } catch (Exception e) {
            logger.debug("Audio protection injection failed", e);
        }
    }

    /**
     * 构建Audio保护脚本
     */
    private String buildAudioProtectionScript() {
        int sampleRate = config.getAudioSampleRate();

        return String.format(
            "(function() {\n" +
            "  try {\n" +
            "    const originalAudioContext = window.AudioContext || window.webkitAudioContext;\n" +
            "    \n" +
            "    if (originalAudioContext) {\n" +
            "      window.AudioContext = (function(OriginalAudioContext) {\n" +
            "        return function() {\n" +
            "          const instance = new OriginalAudioContext();\n" +
            "          const originalCreateOscillator = instance.createOscillator;\n" +
            "          const originalCreateDynamicsCompressor = instance.createDynamicsCompressor;\n" +
            "          \n" +
            "          instance.createOscillator = function() {\n" +
            "            const oscillator = originalCreateOscillator.call(this);\n" +
            "            const originalStart = oscillator.start;\n" +
            "            oscillator.start = function(when) {\n" +
            "              // 添加微小的时间扰动\n" +
            "              return originalStart.call(this, when + (Math.random() - 0.5) * 0.001);\n" +
            "            };\n" +
            "            return oscillator;\n" +
            "          };\n" +
            "          \n" +
            "          Object.defineProperty(instance, 'sampleRate', {\n" +
            "            get: function() { return %d; },\n" +
            "            configurable: true\n" +
            "          });\n" +
            "          \n" +
            "          return instance;\n" +
            "        };\n" +
            "      })(originalAudioContext);\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
            "})();", sampleRate);
    }

    /**
     * 伪装Navigator属性
     */
    private void spoofNavigator(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 伪装关键Navigator属性
            js.executeScript(
                "Object.defineProperties(navigator, {" +
                "  webdriver: { get: () => false, configurable: true }," +
                "  chrome: { get: () => ({ runtime: {} }), configurable: true }," +
                "  languages: { get: () => " + arrayToJs(config.getLanguages()) + ", configurable: true }" +
                "});"
            );

            // 伪装plugins和mimeTypes
            if (config.getPlugins() != null || config.getMimeTypes() != null) {
                js.executeScript(
                    "Object.defineProperty(navigator, 'plugins', {" +
                    "  get: () => " + generatePluginsArray() + "," +
                    "  configurable: true" +
                    "});"
                );
            }

            logger.debug("Navigator properties spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof navigator properties", e);
        }
    }

    /**
     * 伪装Screen属性
     */
    private void spoofScreen(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            js.executeScript(
                "Object.defineProperties(screen, {" +
                "  width: { get: () => " + config.getScreenWidth() + ", configurable: true }," +
                "  height: { get: () => " + config.getScreenHeight() + ", configurable: true }," +
                "  availWidth: { get: () => " + config.getAvailScreenWidth() + ", configurable: true }," +
                "  availHeight: { get: () => " + config.getAvailScreenHeight() + ", configurable: true }," +
                "  colorDepth: { get: () => " + config.getColorDepth() + ", configurable: true }," +
                "  pixelDepth: { get: () => " + config.getPixelDepth() + ", configurable: true }" +
                "});"
            );

            logger.debug("Screen properties spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof screen properties", e);
        }
    }

    /**
     * 伪装Timezone
     */
    private void spoofTimezone(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 重写Date.getTimezoneOffset
            js.executeScript(
                "Date.prototype.getTimezoneOffset = function() { return " + config.getTimezoneOffset() + "; };"
            );

            logger.debug("Timezone spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof timezone", e);
        }
    }

    /**
     * 伪装Language
     */
    private void spoofLanguage(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            js.executeScript(
                "Object.defineProperties(navigator, {" +
                "  language: { get: () => '" + config.getLanguage() + "', configurable: true }," +
                "  languages: { get: () => " + arrayToJs(config.getLanguages()) + ", configurable: true }," +
                "  acceptLanguage: { get: () => '" + config.getAcceptLanguage() + "', configurable: true }" +
                "});"
            );

            logger.debug("Language spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof language", e);
        }
    }

    /**
     * 伪装WebRTC
     */
    private void spoofWebRTC(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 阻止WebRTC泄露
            js.executeScript(
                "Object.defineProperty(navigator, 'mediaDevices', {" +
                "  get: function() { return undefined; }," +
                "  configurable: true" +
                "});"
            );

            // 伪装本地IP
            if (config.getFakeLocalIP() != null) {
                js.executeScript(
                    "Object.defineProperty RTCPeerConnection.prototype, 'localDescription', {" +
                "  get: function() { return JSON.parse(JSON.stringify(this._localDescription || {})).sdp.replace(/c=IN IP4 \\S+/, 'c=IN IP4 " + config.getFakeLocalIP() + "'); }" +
                "});"
                );
            }

            logger.debug("WebRTC spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof WebRTC", e);
        }
    }

    /**
     * 伪装Connection
     */
    private void spoofConnection(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            js.executeScript(
                "Object.defineProperty(navigator, 'connection', {" +
                "  get: function() { return {" +
                "    type: '" + config.getConnectionType() + "'," +
                "    downlink: " + config.getDownlink() + "," +
                "    uplink: " + config.getUplink() + "," +
                "    rtt: " + config.getRtt() + "," +
                "    saveData: " + config.isSaveData() + "," +
                "    effectiveType: '" + config.getConnectionType() + "'" +
                "  }; }," +
                "  configurable: true" +
                "});"
            );

            logger.debug("Connection spoofed");
        } catch (Exception e) {
            logger.debug("Failed to spoof connection", e);
        }
    }

    /**
     * 移除自动化特征
     */
    private void removeAutomationFlags(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 移除webdriver属性
            js.executeScript(
                "Object.defineProperty(navigator, 'webdriver', {" +
                "  get: () => false," +
                "  configurable: true" +
                "});"
            );

            // 移除chrome.runtime
            js.executeScript(
                "Object.defineProperty(window, 'chrome', {" +
                "  get: () => ({ runtime: {} })," +
                "  configurable: true" +
                "});"
            );

            // 移除__nightmare, __selenium, _phantom等
            js.executeScript(
                "delete window.__nightmare;" +
                "delete window.__selenium;" +
                "delete window._phantom;" +
                "delete window.callSelenium;" +
                "delete window.callPhantom;"
            );

            // 修改自动化检测标记
            js.executeScript(
                "Object.defineProperty(navigator, 'webdriver', {" +
                "  get: () => false," +
                "  configurable: true" +
                "});" +
                "Object.defineProperty(window, 'selenium', {" +
                "  get: () => false," +
                "  configurable: true" +
                "});" +
                "Object.defineProperty(window, 'domAutomation', {" +
                "  get: () => false," +
                "  configurable: true" +
                "});" +
                "Object.defineProperty(window, 'domAutomationController', {" +
                "  get: () => ({ sendKeys: function() {} })," +
                "  configurable: true" +
                "});"
            );

            logger.debug("Automation flags removed");
        } catch (Exception e) {
            logger.debug("Failed to remove automation flags", e);
        }
    }

    /**
     * 验证指纹伪装状态
     */
    public boolean verify(WebDriver driver) {
        if (driver == null || !initialized.get()) {
            return false;
        }

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 检查关键属性是否已伪装
            Boolean webdriver = (Boolean) js.executeScript(
                "return navigator.webdriver === false;"
            );
            
            return webdriver != null && webdriver;
        } catch (Exception e) {
            logger.warn("Failed to verify fingerprint spoofing", e);
            return false;
        }
    }

    /**
     * 重新加载伪装配置
     */
    public void reload() {
        if (config != null) {
            this.cachedJavascript = config.generateJavascriptInjection();
            initialized.set(false);
            logger.info("Fingerprint spoofing configuration reloaded");
        }
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取当前配置
     */
    public FingerprintConfig getConfig() {
        return config;
    }

    // ==================== 工具方法 ====================

    private String arrayToJs(String[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(arr[i].replace("'", "\\'")).append("'");
        }
        sb.append("]");
        return sb.toString();
    }

    private String generatePluginsArray() {
        StringBuilder sb = new StringBuilder("[");
        
        if (config.getPlugins() != null) {
            for (int i = 0; i < config.getPlugins().length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("{ name: '").append(config.getPlugins()[i]).append("', filename: '")
                  .append(config.getPlugins()[i].toLowerCase().replace(" ", "_")).append(".dll' }");
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
}
