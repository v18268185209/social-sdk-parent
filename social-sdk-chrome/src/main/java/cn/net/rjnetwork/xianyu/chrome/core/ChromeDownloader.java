package cn.net.rjnetwork.xianyu.chrome.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Chrome/Chromium 自动下载器。
 * 在系统未安装 Chrome 时，从官方镜像下载到项目指定目录（默认 ./chrome-bin/）。
 */
public final class ChromeDownloader {

    private static final Logger log = LoggerFactory.getLogger(ChromeDownloader.class);

    /** macOS 官方 Chrome 下载模板（ARM/x64 自动区分） */
    private static final String MAC_CHROME_URL_TEMPLATE =
            "https://dl.google.com/chrome/mac/%s/stable/googlechrome.dmg";

    /** macOS Chromium 备用（官方没有直链，这里仅占位） */
    private static final String MAC_CHROMIUM_URL = "";

    /** Windows Chrome（离线安装包） */
    private static final String WIN_CHROME_URL =
            "https://dl.google.com/chrome/install/GoogleChromeStandalone64.exe";

    /** Linux Chromium（通过 snap 包名触发系统安装，或 apt 脚本） */
    private static final String LINUX_DOWNLOAD_DIR = "chromium-browser";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 120000;
    private static final long MIN_DOWNLOAD_BYTES = 1024 * 1024; // 1MB，防止拿到错误页

    private ChromeDownloader() {}

    /** 下载结果。 */
    public static final class DownloadResult {
        public final boolean success;
        public final String path;
        public final String message;
        public final long bytes;

        private DownloadResult(boolean success, String path, String message, long bytes) {
            this.success = success;
            this.path = path;
            this.message = message;
            this.bytes = bytes;
        }

        static DownloadResult ok(String path, String message, long bytes) {
            return new DownloadResult(true, path, message, bytes);
        }

        static DownloadResult fail(String message) {
            return new DownloadResult(false, null, message, 0);
        }

        @Override
        public String toString() {
            return success ?
                    String.format("DownloadResult{success=true, path='%s', bytes=%d}", path, bytes) :
                    String.format("DownloadResult{success=false, message='%s'}", message);
        }
    }

    /**
     * 自动下载 Chrome/Chromium 到指定目录。
     *
     * @param targetDir 目标目录（不存在会自动创建）
     * @return 下载结果
     */
    public static DownloadResult download(String targetDir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path dir = Paths.get(targetDir == null || targetDir.isBlank() ? "./chrome-bin" : targetDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return DownloadResult.fail("无法创建下载目录: " + e.getMessage());
        }

        if (os.contains("mac")) {
            return downloadMac(dir);
        } else if (os.contains("win")) {
            return downloadWindows(dir);
        } else {
            return downloadLinux(dir);
        }
    }

    /**
     * 带进度回调的下载。
     *
     * @param targetDir 目标目录
     * @param listener 进度监听器（可为 null）
     * @return 下载结果
     */
    public static DownloadResult download(String targetDir, ProgressListener listener) {
        // 当前实现与 download(targetDir) 等价（回调预留接口）
        return download(targetDir);
    }

    /**
     * 检查下载目标文件是否已存在且有效。
     */
    public static boolean isValidExisting(String path) {
        if (path == null || path.isBlank()) return false;
        File f = new File(path);
        return f.exists() && f.canExecute() && f.length() > MIN_DOWNLOAD_BYTES;
    }

    private static DownloadResult downloadMac(Path dir) {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        // Apple Silicon 包可能与 Intel 不同；这里用统一稳定链接
        String pkg = arch.contains("aarch") ? "universal" : "universal";
        String url = String.format(MAC_CHROME_URL_TEMPLATE, pkg);
        Path dmg = dir.resolve("GoogleChrome.dmg");
        log.info("[DOWNLOAD] macOS Chrome DMG: {}", url);
        try {
            long bytes = downloadFile(url, dmg);
            return DownloadResult.ok(dmg.toString(),
                    "已下载 Google Chrome for macOS (DMG)，请手动安装后重试探测；或通过 Homebrew 自动安装。", bytes);
        } catch (IOException e) {
            return DownloadResult.fail("macOS 下载失败: " + e.getMessage());
        }
    }

    private static DownloadResult downloadWindows(Path dir) {
        Path exe = dir.resolve("GoogleChromeStandalone64.exe");
        log.info("[DOWNLOAD] Windows Chrome 安装包: {}", WIN_CHROME_URL);
        try {
            long bytes = downloadFile(WIN_CHROME_URL, exe);
            return DownloadResult.ok(exe.toString(),
                    "已下载 Chrome Windows 离线安装包（" + bytes + " bytes），请双击安装后重试探测。", bytes);
        } catch (IOException e) {
            return DownloadResult.fail("Windows 下载失败: " + e.getMessage());
        }
    }

    private static DownloadResult downloadLinux(Path dir) {
        // Linux 没有官方直链；建议用系统包管理器或 snap
        String command = detectLinuxPackageManager();
        if (command == null) {
            return DownloadResult.fail(
                    "Linux 无法自动安装 Chromium：请手动执行 'sudo apt install chromium-browser' 或 snap install chromium。");
        }
        // 返回占位
        return DownloadResult.fail(
                "Linux 请通过包管理器安装: " + command + " chromium，或 snap install chromium。");
    }

    private static String detectLinuxPackageManager() {
        for (String cmd : List.of("/usr/bin/apt-get", "/usr/bin/apt", "/usr/bin/dnf", "/usr/bin/yum", "/usr/bin/pacman")) {
            if (Files.exists(Paths.get(cmd))) {
                return cmd;
            }
        }
        return null;
    }

    private static long downloadFile(String urlStr, Path target) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; XianyuSDK)");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        long total = conn.getContentLengthLong();
        Path tmp = Paths.get(target.toString() + ".downloading");
        long downloaded;
        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            downloaded = Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        if (downloaded < MIN_DOWNLOAD_BYTES) {
            Files.deleteIfExists(tmp);
            throw new IOException("下载内容过小 (" + downloaded + " bytes)，疑似错误页面");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("[DOWNLOAD] 完成: {} ({} bytes)", target, downloaded);
        return downloaded;
    }

    /**
     * 解压 zip 包到指定目录（备用入口：当提供 Chromium zip 时使用）。
     */
    public static Path unzipZip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            Path resolved = null;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("非法 zip 条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    resolved = entryPath;
                }
                zis.closeEntry();
            }
            return resolved;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }
}
