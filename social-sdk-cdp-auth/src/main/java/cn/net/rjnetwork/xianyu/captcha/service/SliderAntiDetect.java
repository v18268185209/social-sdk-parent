package cn.net.rjnetwork.xianyu.captcha.service;

/**
 * 滑块反检测注入脚本
 * <p>在 page.add_init_script() 时注入，隐藏自动化特征。
 * 对应 xianyu-auto-bot 的 slider_stealth_patch.STEALTH_INIT_SCRIPT + STEALTH_LAUNCH_ARGS，
 * 并额外覆盖：clientHints、network fingerprint、permissions、...</p>
 */
public final class SliderAntiDetect {

    /** 默认 seed（对应旧版逻辑，全局单一噪声）。 */
    public static final long DEFAULT_SEED = 0L;

    private SliderAntiDetect() {}

    /**
     * 完整的反检测 JS init script（无 seed 兜底版本，噪声固定）。
     *
     * <p>新代码优先使用 {@link #buildScript(long)}。
     */
    public static final String INIT_SCRIPT = buildScript(DEFAULT_SEED);

    /**
     * 根据账号 seed 生成反检测 JS 脚本（per-account 指纹隔离）。
     *
     * <p>同 seed 返回同 JS（跨重启指纹不变），不同 seed 返回不同 canvas/WebGL 噪声（账号间指纹唯一）。
     *
     * @param seed 派生种子（建议由 ChromeProfileManager#deriveSeed(accountId) 生成）
     * @return 完整的反检测 init script，可直接通过 CDP Runtime.evaluate 注入
     */
    public static String buildScript(long seed) {
        long canvasNoise = deriveNoise(seed, "canvas");
        long webglNoise = deriveNoise(seed, "webgl");
        int screenW = (int) (deriveNoise(seed, "screenW") % 200 + 1280);
        int screenH = (int) (deriveNoise(seed, "screenH") % 200 + 600);
        long hwConcurrency = (deriveNoise(seed, "hw") % 4) + 4;
        long deviceMemory = (long) Math.pow(2, (deriveNoise(seed, "mem") % 3) + 2);

        String webglVendor = pickVendor(webglNoise);
        String webglRenderer = pickRenderer(webglNoise);

        return ""
                + "// ====== Xianyu Slider Anti-Detect Init Script (seed=" + seed + ") ======\n"
                + "(() => {\n"
                + "  'use strict';\n"
                + "  const SEED = " + seed + ";\n"
                + "\n"
                + "  // 1. 隐藏 webdriver\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'webdriver', { get: () => false });\n"
                + "    delete navigator.__proto__.webdriver;\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 2. 伪造 chrome.runtime\n"
                + "  try {\n"
                + "    window.chrome = window.chrome || {};\n"
                + "    window.chrome.runtime = window.chrome.runtime || {};\n"
                + "    window.chrome.loadTimes = function() { return {}; };\n"
                + "    window.chrome.csi = function() { return {}; };\n"
                + "    window.chrome.app = window.chrome.app || {};\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 3. 伪造 plugins\n"
                + "  try {\n"
                + "    const plugins = [\n"
                + "      { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format', length: 1 },\n"
                + "      { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '', length: 1 },\n"
                + "      { name: 'Native Client', filename: 'internal-nacl-plugin', description: '', length: 2 },\n"
                + "    ];\n"
                + "    plugins.item = (i) => plugins[i] || null;\n"
                + "    plugins.namedItem = (name) => plugins.find(p => p.name === name) || null;\n"
                + "    plugins.refresh = () => {};\n"
                + "    Object.defineProperty(navigator, 'plugins', {\n"
                + "      get: () => Object.setPrototypeOf(plugins, PluginArray.prototype),\n"
                + "    });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 4. 伪造 languages\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'languages', {\n"
                + "      get: () => ['zh-CN', 'zh', 'en-US', 'en'],\n"
                + "    });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 5. Canvas fingerprint - per-account noise\n"
                + "  try {\n"
                + "    const _toDataURL = HTMLCanvasElement.prototype.toDataURL;\n"
                + "    HTMLCanvasElement.prototype.toDataURL = function(type) {\n"
                + "      const ctx = this.getContext('2d');\n"
                + "      if (ctx && this.width > 10 && this.height > 10) {\n"
                + "        const imageData = ctx.getImageData(0, 0, this.width, this.height);\n"
                + "        if (imageData.data.length > 3) {\n"
                + "          const noise = " + canvasNoise + ";\n"
                + "          imageData.data[(noise) % imageData.data.length] ^= 1;\n"
                + "          imageData.data[(noise + 37) % imageData.data.length] ^= 1;\n"
                + "        }\n"
                + "        ctx.putImageData(imageData, 0, 0);\n"
                + "      }\n"
                + "      return _toDataURL.apply(this, arguments);\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 6. WebGL fingerprint - per-account noise\n"
                + "  try {\n"
                + "    const _getParameter = WebGLRenderingContext.prototype.getParameter;\n"
                + "    WebGLRenderingContext.prototype.getParameter = function(parameter) {\n"
                + "      if (parameter === 37445) {\n"
                + "        return '" + webglVendor + "';\n"
                + "      }\n"
                + "      if (parameter === 37446) {\n"
                + "        return '" + webglRenderer + "';\n"
                + "      }\n"
                + "      return _getParameter.call(this, parameter);\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 7. permissions query override\n"
                + "  try {\n"
                + "    const _query = window.navigator.permissions ? window.navigator.permissions.query : null;\n"
                + "    if (_query) {\n"
                + "      window.navigator.permissions.query = function(parameters) {\n"
                + "        if (parameters && parameters.name === 'notifications') {\n"
                + "          return Promise.resolve({ state: Notification.permission, onchange: null });\n"
                + "        }\n"
                + "        return _query.call(this, parameters);\n"
                + "      };\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 8. Headless UA scrub\n"
                + "  try {\n"
                + "    if (navigator.userAgent && navigator.userAgent.includes('Headless')) {\n"
                + "      Object.defineProperty(navigator, 'userAgent', {\n"
                + "        get: () => navigator.userAgent.replace('Headless', ''),\n"
                + "      });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 9. Screen fingerprint consistency (per-account)\n"
                + "  try {\n"
                + "    Object.defineProperty(screen, 'width', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'height', { get: () => " + screenH + " });\n"
                + "    Object.defineProperty(screen, 'availWidth', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'availHeight', { get: () => " + screenH + " });\n"
                + "    Object.defineProperty(screen, 'colorDepth', { get: () => 24 });\n"
                + "    Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 10. Connection rtt\n"
                + "  try {\n"
                + "    if (navigator.connection) {\n"
                + "      Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 11. Hardware concurrency / device memory (per-account)\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => " + hwConcurrency + " });\n"
                + "    Object.defineProperty(navigator, 'deviceMemory', { get: () => " + deviceMemory + " });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 12. isTrusted bypass attempt via dispatchEvent shim\n"
                + "  //    某些 nc.js 版本会检查 event.isTrusted\n"
                + "  try {\n"
                + "    const _createEvent = document.createEvent;\n"
                + "    document.createEvent = function(type) {\n"
                + "      const evt = _createEvent.call(this, type);\n"
                + "      if (evt && typeof evt.initEvent === 'function') {\n"
                + "        Object.defineProperty(evt, 'isTrusted', { get: () => true });\n"
                + "      }\n"
                + "      return evt;\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "})();\n";
    }

    /**
     * 从 seed + label 派生长噪声值（SHA-256 派生）。
     */
    public static long deriveNoise(long seed, String label) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((seed + ":" + label).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return Math.abs(value);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Math.abs(seed * 31L + label.hashCode());
        }
    }

    /**
     * 根据噪声选 WebGL vendor 字符串。
     */
    public static String pickVendor(long noise) {
        String[] vendors = {
                "Google Inc.",
                "Google Inc. (Intel)",
                "Google Inc. (NVIDIA)",
                "Google Inc. (AMD)",
        };
        return vendors[(int) (noise % vendors.length)];
    }

    /**
     * 根据噪声选 WebGL renderer 字符串。
     */
    public static String pickRenderer(long noise) {
        String[] renderers = {
                "ANGLE (Intel, Intel(R) UHD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
        };
        return renderers[(int) (noise % renderers.length)];
    }

    /** 完整的反检测 JS init script — 保留为 buildScript(DEFAULT_SEED) 的别名。 */
    public static final String INIT_SCRIPT_INLINE = INIT_SCRIPT;
    static {
        // 原始字面量版本（兜底）保留在 buildScript 中。
    }

    private static final String LEGACY_SCRIPT_START =
        "(() => {\n" +
        "  'use strict';\n" +
         "  // 1. 隐藏 webdriver\n"
        + "  try {\n"
        + "    Object.defineProperty(navigator, 'webdriver', { get: () => false });\n"
        + "    delete navigator.__proto__.webdriver;\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 2. 伪造 chrome.runtime\n"
        + "  try {\n"
        + "    window.chrome = window.chrome || {};\n"
        + "    window.chrome.runtime = window.chrome.runtime || {};\n"
        + "    window.chrome.loadTimes = function() { return {}; };\n"
        + "    window.chrome.csi = function() { return {}; };\n"
        + "    window.chrome.app = window.chrome.app || {};\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 3. 伪造 plugins\n"
        + "  try {\n"
        + "    const plugins = [\n"
        + "      { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format', length: 1 },\n"
        + "      { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '', length: 1 },\n"
        + "      { name: 'Native Client', filename: 'internal-nacl-plugin', description: '', length: 2 },\n"
        + "    ];\n"
        + "    plugins.item = (i) => plugins[i] || null;\n"
        + "    plugins.namedItem = (name) => plugins.find(p => p.name === name) || null;\n"
        + "    plugins.refresh = () => {};\n"
        + "    Object.defineProperty(navigator, 'plugins', {\n"
        + "      get: () => Object.setPrototypeOf(plugins, PluginArray.prototype),\n"
        + "    });\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 4. 伪造 languages\n"
        + "  try {\n"
        + "    Object.defineProperty(navigator, 'languages', {\n"
        + "      get: () => ['zh-CN', 'zh', 'en-US', 'en'],\n"
        + "    });\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 5. Canvas fingerprint - slight noise\n"
        + "  try {\n"
        + "    const _toDataURL = HTMLCanvasElement.prototype.toDataURL;\n"
        + "    HTMLCanvasElement.prototype.toDataURL = function(type) {\n"
        + "      const ctx = this.getContext('2d');\n"
        + "      if (ctx && this.width > 10 && this.height > 10) {\n"
        + "        const imageData = ctx.getImageData(0, 0, this.width, this.height);\n"
        + "        if (imageData.data.length > 3) {\n"
        + "          imageData.data[0] = imageData.data[0] ^ 1;\n"
        + "          imageData.data[100] = imageData.data[100] ^ 1;\n"
        + "        }\n"
        + "        ctx.putImageData(imageData, 0, 0);\n"
        + "      }\n"
        + "      return _toDataURL.apply(this, arguments);\n"
        + "    };\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 6. WebGL fingerprint\n"
        + "  try {\n"
        + "    const _getParameter = WebGLRenderingContext.prototype.getParameter;\n"
        + "    WebGLRenderingContext.prototype.getParameter = function(parameter) {\n"
        + "      if (parameter === 37445) {\n"
        + "        return 'ANGLE (Intel, Intel(R) UHD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)';\n"
        + "      }\n"
        + "      if (parameter === 37446) {\n"
        + "        return 'WebKit WebGL';\n"
        + "      }\n"
        + "      return _getParameter.call(this, parameter);\n"
        + "    };\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 7. permissions query override\n"
        + "  try {\n"
        + "    const _query = window.navigator.permissions ? window.navigator.permissions.query : null;\n"
        + "    if (_query) {\n"
        + "      window.navigator.permissions.query = function(parameters) {\n"
        + "        if (parameters && parameters.name === 'notifications') {\n"
        + "          return Promise.resolve({ state: Notification.permission, onchange: null });\n"
        + "        }\n"
        + "        return _query.call(this, parameters);\n"
        + "      };\n"
        + "    }\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 8. Headless UA scrub\n"
        + "  try {\n"
        + "    if (navigator.userAgent && navigator.userAgent.includes('Headless')) {\n"
        + "      Object.defineProperty(navigator, 'userAgent', {\n"
        + "        get: () => navigator.userAgent.replace('Headless', ''),\n"
        + "      });\n"
        + "    }\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 9. Screen fingerprint consistency\n"
        + "  try {\n"
        + "    Object.defineProperty(screen, 'availWidth', { get: () => screen.width });\n"
        + "    Object.defineProperty(screen, 'availHeight', { get: () => screen.height });\n"
        + "    Object.defineProperty(screen, 'colorDepth', { get: () => 24 });\n"
        + "    Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 10. Connection rtt\n"
        + "  try {\n"
        + "    if (navigator.connection) {\n"
        + "      Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 });\n"
        + "    }\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 11. isTrusted bypass attempt via dispatchEvent shim\n"
        + "  //    某些 nc.js 版本会检查 event.isTrusted\n"
        + "  try {\n"
        + "    const _createEvent = document.createEvent;\n"
        + "    document.createEvent = function(type) {\n"
        + "      const evt = _createEvent.call(this, type);\n"
        + "      if (evt && typeof evt.initEvent === 'function') {\n"
        + "        Object.defineProperty(evt, 'isTrusted', { get: () => true });\n"
        + "      }\n"
        + "      return evt;\n"
        + "    };\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 12. Date now() / performance.now() jitter (subtle, to avoid time-based detection)\n"
        + "  try {\n"
        + "    // Do NOT spoof performance.now() too aggressively or it breaks slider animation\n"
        + "    // Just leave it alone - AWSC checks timing via requestAnimationFrame instead\n"
        + "  } catch (e) {}\n"
        + "\n"
        + "  // 13. Override getContext for 2d canvas - noise already handled in toDataURL\n"
        + "\n"
        + "})();\n";

    /** Chromium 反检测启动参数列表 */
    public static final String[] LAUNCH_ARGS = {
        "--disable-blink-features=AutomationControlled",
        "--disable-features=IsolateOrigins,site-per-process",
        "--disable-site-isolation-trials",
        "--no-sandbox",
        "--disable-gpu-sandbox",
        "--disable-dev-shm-usage",
        "--disable-setuid-sandbox",
        "--disable-infobars",
        "--disable-background-timer-throttling",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-features=TranslateUI",
        "--disable-ipc-flooding-protection",
        "--disable-hang-monitor",
        "--disable-prompt-on-repost",
        "--disable-sync",
        "--disable-default-apps",
        "--disable-crash-reporter",
        "--disable-component-extensions-with-background-pages",
        "--password-store=basic",
        "--use-mock-keychain",
        "--disable-breakpad",
        "--disable-client-side-phishing-detection",
        "--disable-component-update",
        "--disable-domain-reliability",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-features=InterestFeedContentSuggestions",
        "--disable-features=CalculateNativeWinOcclusion",
        "--enable-features=NetworkService,NetworkServiceInProcess",
        "--force-color-profile=srgb",
        "--metrics-recording-only",
        "--mute-audio",
        "--hide-scrollbars",
        "--disable-notifications",
        "--disable-popup-blocking",
    };
}