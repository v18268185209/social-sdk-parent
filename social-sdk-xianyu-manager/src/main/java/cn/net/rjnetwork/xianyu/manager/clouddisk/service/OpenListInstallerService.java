package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class OpenListInstallerService {

    private final OpenListProperties properties;
    private final Path dataDir;
    private Path executablePath;
    private String osName;
    private String arch;
    private String downloadUrl;
    private String localBinaryName;

    public OpenListInstallerService(OpenListProperties properties) {
        this.properties = properties;
        this.dataDir = Paths.get(properties.getDataDir());
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(dataDir);
        detectSystem();
        findExistingExecutable();
    }

    private void detectSystem() {
        this.osName = System.getProperty("os.name").toLowerCase();
        if (this.osName.contains("win")) {
            this.arch = detectWindowsArch();
        } else if (this.osName.contains("linux")) {
            this.arch = detectLinuxArch();
        } else if (this.osName.contains("mac")) {
            this.arch = "darwin-" + detectAmd64Arm64();
        } else {
            this.arch = "amd64";
        }

        localBinaryName = getLocalBinaryName();
        downloadUrl = buildDownloadUrl();
        executablePath = dataDir.resolve(localBinaryName);
    }

    private String detectWindowsArch() {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "wmic", "cpu", "get", "architecture").start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.equals("9") || line.equals("0")) return "amd64";
                if (line.equals("12") || line.equals("8")) return "arm64";
            }
        } catch (IOException e) {
            // fallback
        }
        return "amd64";
    }

    private String detectLinuxArch() {
        try {
            Process p = new ProcessBuilder("uname", "-m").start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine().trim();
            return line.equals("aarch64") ? "arm64" : "amd64";
        } catch (IOException e) {
            return "amd64";
        }
    }

    private String detectAmd64Arm64() {
        try {
            Process p = new ProcessBuilder("uname", "-m").start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine().trim();
            return line.equals("arm64") ? "arm64" : "amd64";
        } catch (IOException e) {
            return "amd64";
        }
    }

    private String getLocalBinaryName() {
        if (osName.contains("win")) {
            return "openlist.exe";
        }
        if (osName.contains("mac")) {
            if (arch.contains("arm64")) return "openlist-darwin-arm64";
            return "openlist-darwin-amd64";
        }
        if (arch.contains("arm64")) return "openlist-linux-arm64";
        return "openlist-linux-amd64";
    }

    private String buildDownloadUrl() {
        String osFamily = "linux";
        if (osName.contains("win")) osFamily = "windows";
        else if (osName.contains("mac")) osFamily = "darwin";

        String archFamily = arch.contains("arm64") ? "arm64" : "amd64";
        String suffix = osName.contains("win") ? ".zip" : ".tar.gz";

        return String.format(
            "https://github.com/OpenListTeam/OpenList/releases/latest/download/openlist-%s-%s%s",
            osFamily, archFamily, suffix
        );
    }

    private void findExistingExecutable() {
        for (String name : Arrays.asList(localBinaryName, "openlist", "openlist.exe")) {
            Path candidate = dataDir.resolve(name);
            if (Files.exists(candidate)) {
                executablePath = candidate;
                return;
            }
        }
        executablePath = dataDir.resolve(localBinaryName);
    }

    public Map<String, Object> getStatus() {
        boolean installed = Files.exists(executablePath);
        boolean running = false;

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("installed", installed);
        status.put("running", running);
        status.put("version", "latest");
        status.put("port", properties.getPort());
        status.put("url", properties.getUrl());
        status.put("username", properties.getUsername());
        status.put("password", properties.getPassword());
        status.put("downloadUrl", downloadUrl);
        status.put("localBinaryName", localBinaryName);
        status.put("execPath", executablePath.toString());
        status.put("osName", osName);
        status.put("arch", arch);
        return status;
    }

    public Map<String, Object> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("downloadUrl", downloadUrl);
        info.put("localBinaryName", localBinaryName);
        info.put("execPath", executablePath.toString());
        info.put("dataDir", dataDir.toString());
        info.put("accessUrl", properties.getUrl());
        info.put("defaultUsername", properties.getUsername());
        info.put("defaultPassword", properties.getPassword());
        info.put("osName", osName);
        info.put("arch", arch);
        return info;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getExecutablePath() {
        return executablePath;
    }

    public boolean install() throws IOException {
        if (Files.exists(executablePath)) {
            // 已存在：mac/linux 下补一次可执行权限，避免历史解压但无 +x 导致启动 Permission denied
            ensureExecutablePermission();
            return true;
        }

        System.out.println("Downloading OpenList from: " + downloadUrl);
        Path tempFile = dataDir.resolve(localBinaryName + ".download");
        downloadFile(downloadUrl, tempFile);

        if (localBinaryName.endsWith(".exe") || downloadUrl.endsWith(".zip")) {
            extractZip(tempFile, dataDir, localBinaryName);
        } else {
            try {
                extractTarGz(tempFile, dataDir, localBinaryName);
            } catch (IOException extractErr) {
                // tar.gz 解压失败：按系统判断下载对应 zip 包重试（mac/linux 也有 zip release asset）
                System.out.println("tar.gz extract failed (" + extractErr.getMessage() + "), fallback to zip");
                String fallbackUrl = downloadUrl.replace(".tar.gz", ".zip");
                Path zipTemp = dataDir.resolve(localBinaryName + ".zip.download");
                try {
                    downloadFile(fallbackUrl, zipTemp);
                    extractZip(zipTemp, dataDir, localBinaryName);
                } finally {
                    Files.deleteIfExists(zipTemp);
                }
            }
        }

        Files.deleteIfExists(tempFile);
        executablePath = dataDir.resolve(localBinaryName);
        // mac/linux 下解压后默认无执行权限，必须 chmod +x，否则 ProcessBuilder.start 抛 Permission denied
        ensureExecutablePermission();
        return true;
    }

    /**
     * mac/linux 下给可执行文件加执行权限（chmod +x）。Windows 下 no-op。
     */
    private void ensureExecutablePermission() throws IOException {
        if (osName.contains("win")) return;
        if (!Files.exists(executablePath)) return;
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(executablePath, perms);
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统（理论上 mac/linux 都是 POSIX，兜底）
        }
    }

    private void downloadFile(String urlString, Path target) throws IOException {
        URL url = new URL(urlString);
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    public void extractZip(Path zipPath, Path targetDir, String outputPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // 只处理顶层文件（不递归创建子目录）
                    String fileName = entry.getName();
                    int lastSlash = fileName.lastIndexOf('/');
                    if (lastSlash >= 0) continue; // 跳过子目录中的文件
                    
                    Path outPath = targetDir.resolve(fileName).normalize();
                    if (!outPath.startsWith(targetDir)) {
                        throw new SecurityException("Invalid zip entry: " + fileName);
                    }
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

    private void extractTarGz(Path tarGzPath, Path targetDir, String outputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", tarGzPath.toString(), "-C", targetDir.toString());
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException startErr) {
            // tar 命令不存在（极少见，mac/linux 默认都有）→ 抛 IOException 触发 install() 里的 zip fallback
            throw new IOException("tar command not available: " + startErr.getMessage());
        }
        try {
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                // tar 解压失败（压缩包损坏、格式不对、磁盘满等）→ 抛 IOException 触发 zip fallback
                String errText = "";
                try (BufferedReader er = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = er.readLine()) != null) errText += line + "\n";
                }
                throw new IOException("tar exited " + exitCode + ": " + errText.trim());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("tar extract interrupted", ie);
        }
    }
}
