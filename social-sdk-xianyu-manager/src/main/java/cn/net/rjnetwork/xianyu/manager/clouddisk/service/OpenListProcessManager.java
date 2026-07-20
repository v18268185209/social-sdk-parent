package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class OpenListProcessManager {

    private final OpenListInstallerService installerService;
    private final OpenListProperties properties;
    private Process process;

    public OpenListProcessManager(OpenListInstallerService installerService, OpenListProperties properties) {
        this.installerService = installerService;
        this.properties = properties;
    }

    public Map<String, Object> start() throws IOException {
        if (process != null && process.isAlive()) {
            return Map.of("status", "running", "message", "已经在运行");
        }

        if (!Files.exists(installerService.getExecutablePath())) {
            throw new IOException("OpenList 未安装，请先执行安装");
        }

        Path workDir = installerService.getDataDir();
        String execPath = installerService.getExecutablePath().toString();

        System.out.println("Starting OpenList from: " + execPath);
        ProcessBuilder pb = new ProcessBuilder(execPath, "--path", workDir.toString(), "--port", String.valueOf(properties.getPort()));
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        Thread t = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "openlist-starter");
        t.setDaemon(true);
        t.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean running = process != null && process.isAlive();
        return Map.of(
            "status", running ? "running" : "failed",
            "message", running ? "启动成功" : "启动失败"
        );
    }

    public Map<String, Object> stop() {
        if (process == null || !process.isAlive()) {
            return Map.of("status", "stopped", "message", "未运行");
        }

        process.destroyForcibly();
        return Map.of("status", "stopped", "message", "已停止");
    }

    public Map<String, Object> restart() throws IOException {
        stop();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return start();
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public String getStatus() {
        if (process != null && process.isAlive()) return "running";
        return "stopped";
    }
}
