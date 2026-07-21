package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class OpenListTaskService {

    private final OpenListProperties properties;
    private final Path dataDir;
    private final Path executablePath;
    private String osName;
    private String arch;
    private String downloadUrl;
    private String localBinaryName;

    private volatile String currentTaskId;
    private volatile double progress; // 0.0 - 1.0
    private volatile String currentPhase; // idle, downloading, extracting, starting, running, failed, stopped
    private volatile String currentMessage;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public OpenListTaskService(OpenListProperties properties) {
        this.properties = properties;
        this.dataDir = Paths.get(properties.getDataDir());
        this.executablePath = dataDir.resolve("openlist.exe");

        this.currentPhase = "idle";
        this.currentMessage = "空闲";

        detectSystem();
    }

    private void detectSystem() {
        this.osName = System.getProperty("os.name").toLowerCase();
        String rawArch = System.getProperty("os.arch").toLowerCase();
        this.arch = rawArch.contains("amd64") || rawArch.contains("x86_64") ? "amd64"
                   : rawArch.contains("arm64") || rawArch.contains("aarch64") ? "arm64"
                   : "amd64"; // fallback

        this.localBinaryName = osName.contains("win") ? "openlist.exe"
                            : osName.contains("mac") ? "openlist-darwin-amd64"
                            : "openlist-linux-amd64";
        String osPart = osName.contains("win") ? "windows" : osName.contains("mac") ? "darwin" : "linux";
        this.downloadUrl = String.format(
            "https://github.com/OpenListTeam/OpenList/releases/latest/download/openlist-%s-%s%s",
            osPart,
            arch,
            osName.contains("win") ? ".zip" : ".tar.gz"
        );
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("installed", Files.exists(executablePath));
        s.put("running", "running".equals(currentPhase));
        s.put("version", "latest");
        s.put("port", properties.getPort());
        s.put("url", properties.getUrl());
        s.put("username", properties.getUsername());
        s.put("password", properties.getPassword());
        s.put("downloadUrl", downloadUrl);
        s.put("localBinaryName", localBinaryName);
        s.put("execPath", executablePath.toString());
        s.put("osName", osName);
        s.put("arch", arch);
        s.put("phase", currentPhase);
        s.put("progress", progress);
        s.put("message", currentMessage);
        return s;
    }

    public synchronized CompletableFuture<Void> startInstallAsync() {
        if (!"idle".equals(currentPhase)) {
            throw new IllegalStateException("当前状态: " + currentPhase);
        }
        currentTaskId = UUID.randomUUID().toString();
        progress = 0.0;
        currentPhase = "downloading";
        currentMessage = "正在下载 OpenList...";

        return CompletableFuture.runAsync(this::doInstall);
    }

    private void doInstall() {
        try {
            // 下载
            updatePhase("downloading", "正在下载 OpenList...", 0.1);
            Files.createDirectories(dataDir);
            Path tempFile = dataDir.resolve("openlist.download");
            downloadFile(downloadUrl, tempFile);

            // 解压
            updatePhase("extracting", "正在解压安装包...", 0.7);
            if (downloadUrl.endsWith(".zip")) {
                extractZip(tempFile, dataDir);
            }
            Files.deleteIfExists(tempFile);

            // 完成
            updatePhase("idle", "安装完成", 1.0);

        } catch (Exception e) {
            updatePhase("failed", "安装失败: " + e.getMessage(), 0.0);
        }
    }

    private void downloadFile(String urlString, Path target) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        long totalSize = conn.getContentLengthLong();
        long downloaded = 0;

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                downloaded += n;
                if (totalSize > 0) {
                    progress = 0.1 + (downloaded / (double) totalSize) * 0.6;
                }
            }
        }
    }

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String fileName = new File(entry.getName()).getName();
                    Path outPath = targetDir.resolve(fileName).normalize();
                    if (!outPath.startsWith(targetDir)) continue;
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public synchronized CompletableFuture<Void> startOpenListAsync() {
        if (!"idle".equals(currentPhase) && !"stopped".equals(currentPhase)) {
            throw new IllegalStateException("当前状态: " + currentPhase);
        }
        currentTaskId = UUID.randomUUID().toString();
        progress = 0.0;
        currentPhase = "starting";
        currentMessage = "正在启动 OpenList...";

        return CompletableFuture.runAsync(this::doStart);
    }

    private void doStart() {
        try {
            if (!Files.exists(executablePath)) {
                updatePhase("failed", "OpenList 未安装", 0.0);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                executablePath.toString(),
                "server",
                "--data", dataDir.toString(),
                "--port", String.valueOf(properties.getPort())
            );
            pb.directory(dataDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            Thread logThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[OpenList] " + line);
                        if (line.contains(" Listening ") || line.contains("listen") || line.contains("started")) {
                            updatePhase("running", "启动成功，正在运行...", 1.0);
                        }
                    }
                } catch (IOException e) {}
            }, "openlist-log");
            logThread.setDaemon(true);
            logThread.start();

            // 等待启动
            Thread.sleep(3000);
            if (process.isAlive()) {
                updatePhase("running", "启动成功", 1.0);
            } else {
                updatePhase("failed", "启动失败", 0.0);
            }

        } catch (Exception e) {
            updatePhase("failed", "启动失败: " + e.getMessage(), 0.0);
        }
    }

    public synchronized void stopOpenList() {
        try {
            // 找到并杀死 openlist 进程
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"taskkill", "/F", "/IM", "openlist.exe"});
            } else {
                Runtime.getRuntime().exec(new String[]{"pkill", "-f", "openlist"});
            }
            updatePhase("stopped", "已停止", 0.0);
        } catch (Exception e) {
            updatePhase("failed", "停止失败: " + e.getMessage(), 0.0);
        }
    }

    public CompletableFuture<Void> restartOpenListAsync() {
        stopOpenList();
        try { Thread.sleep(1000); } catch (Exception e) {}
        return startOpenListAsync();
    }

    private void updatePhase(String phase, String message, double pr) {
        this.currentPhase = phase;
        this.currentMessage = message;
        this.progress = pr;
        // 推送给所有 SSE 监听者
        emitters.values().forEach(emitter -> {
            try {
                emitter.send(Map.of(
                    "phase", phase,
                    "message", message,
                    "progress", pr
                ));
            } catch (Exception e) {
                // ignore
            }
        });
    }

    public void subscribe(SseEmitter emitter) {
        String id = UUID.randomUUID().toString();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
    }

    public Map<String, Object> getCurrentProgress() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("taskId", currentTaskId);
        p.put("phase", currentPhase);
        p.put("progress", progress);
        p.put("message", currentMessage);
        return p;
    }
}
