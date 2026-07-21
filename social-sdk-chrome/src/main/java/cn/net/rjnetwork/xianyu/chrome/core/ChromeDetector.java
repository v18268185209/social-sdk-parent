package cn.net.rjnetwork.xianyu.chrome.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chrome/Chromium 可执行文件探测器。
 * 支持 macOS、Windows、Linux 三大平台，按优先级探测系统已安装的浏览器路径。
 */
public final class ChromeDetector {

    private static final Logger log = LoggerFactory.getLogger(ChromeDetector.class);

    private ChromeDetector() {}

    /** 探测结果。 */
    public static final class DetectionResult {
        /** 是否发现可执行文件。 */
        public final boolean found;
        /** 绝对路径（未发现时为 null）。 */
        public final String path;
        /** 浏览器类型（chrome / chromium / edge 等）。 */
        public final String type;
        /** 探测过程中搜索过的候选路径。 */
        public final List<String> searched;

        private DetectionResult(boolean found, String path, String type, List<String> searched) {
            this.found = found;
            this.path = path;
            this.type = type;
            this.searched = searched;
        }

        static DetectionResult none(List<String> searched) {
            return new DetectionResult(false, null, null, searched);
        }

        static DetectionResult ok(String path, String type, List<String> searched) {
            return new DetectionResult(true, path, type, searched);
        }

        @Override
        public String toString() {
            return found ?
                    String.format("DetectionResult{found=true, path='%s', type='%s'}", path, type) :
                    String.format("DetectionResult{found=false, searched=%d paths}", searched.size());
        }
    }

    /**
     * 探测当前系统是否已安装 Chrome/Chromium。
     *
     * @return 探测结果
     */
    public static DetectionResult detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return detectMac();
        } else if (os.contains("win")) {
            return detectWindows();
        } else {
            return detectLinux();
        }
    }

    /**
     * 探测指定路径是否为有效的 Chrome 可执行文件。
     * 仅校验存在性与可执行权限，不做版本解析。
     */
    public static DetectionResult detectAt(String explicitPath) {
        if (explicitPath == null || explicitPath.isBlank()) {
            return detect();
        }
        File f = new File(explicitPath.trim());
        if (f.exists() && f.canExecute()) {
            return DetectionResult.ok(f.getAbsolutePath(), guessType(explicitPath), List.of(explicitPath));
        }
        log.info("[DETECT] 指定路径不可执行: {}", explicitPath);
        return detect();
    }

    private static DetectionResult detectMac() {
        List<String> candidates = new ArrayList<>(Arrays.asList(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
        ));
        // 用户目录下的 Chrome
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isEmpty()) {
            candidates.add(userHome + "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            candidates.add(userHome + "/Applications/Chromium.app/Contents/MacOS/Chromium");
        }
        for (Path p : whichAll("google-chrome", "google-chrome-stable", "chromium", "chromium-browser")) {
            candidates.add(p.toString());
        }
        for (Path p : whichAll("/Applications/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing")) {
            candidates.add(p.toString());
        }
        return firstExisting(candidates, "macOS");
    }

    private static DetectionResult detectWindows() {
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        List<String> candidates = new ArrayList<>();
        if (programFiles != null) {
            candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add(programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
            candidates.add(programFiles + "\\Chromium\\Application\\chrome.exe");
            candidates.add(programFiles + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
        }
        if (programFilesX86 != null) {
            candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add(programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
            candidates.add(programFilesX86 + "\\Chromium\\Application\\chrome.exe");
            candidates.add(programFilesX86 + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
        }
        if (localAppData != null) {
            candidates.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add(localAppData + "\\Chromium\\Application\\chrome.exe");
            candidates.add(localAppData + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        return firstExisting(candidates, "Windows");
    }

    private static DetectionResult detectLinux() {
        List<String> candidates = new ArrayList<>();
        for (Path p : whichAll(
                "google-chrome", "google-chrome-stable", "google-chrome-beta", "google-chrome-dev",
                "chromium", "chromium-browser",
                "chromium-browser-with-plugins",
                "microsoft-edge", "microsoft-edge-stable",
                "brave-browser", "brave")) {
            candidates.add(p.toString());
        }
        for (String prefix : List.of("/usr/bin", "/usr/local/bin", "/snap/bin", "/opt/google/chrome")) {
            candidates.add(prefix + "/google-chrome");
            candidates.add(prefix + "/chromium");
            candidates.add(prefix + "/chromium-browser");
            candidates.add(prefix + "/microsoft-edge");
            candidates.add(prefix + "/brave-browser");
        }
        return firstExisting(candidates, "Linux");
    }

    private static List<Path> whichAll(String... commands) {
        List<Path> found = new ArrayList<>();
        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            if (cmd.contains(File.separator)) {
                // 视作绝对路径直接检查
                File f = new File(cmd);
                if (f.exists() && f.canExecute()) {
                    found.add(Paths.get(f.getAbsolutePath()));
                }
                continue;
            }
            try {
                Process which = new ProcessBuilder("which", cmd).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(which.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.isEmpty()) {
                        Path p = Paths.get(line.trim()).toAbsolutePath();
                        if (Files.exists(p)) {
                            found.add(p);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return found;
    }

    private static DetectionResult firstExisting(List<String> candidates, String osName) {
        List<String> searched = new ArrayList<>();
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            searched.add(c);
            File f = new File(c);
            if (f.exists() && f.canExecute()) {
                log.info("[DETECT] 在 {} 发现浏览器: {}", osName, f.getAbsolutePath());
                return DetectionResult.ok(f.getAbsolutePath(), guessType(c), searched);
            }
        }
        log.info("[DETECT] 在 {} 未找到浏览器，已搜索 {} 个路径", osName, searched.size());
        return DetectionResult.none(searched);
    }

    private static String guessType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("edge") || lower.contains("msedge")) return "msedge";
        if (lower.contains("brave")) return "brave";
        if (lower.contains("chromium")) return "chromium";
        if (lower.contains("chrome")) return "chrome";
        return "unknown";
    }
}
