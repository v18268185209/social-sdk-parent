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
            return true;
        }

        System.out.println("Downloading OpenList from: " + downloadUrl);
        Path tempFile = dataDir.resolve(localBinaryName + ".download");
        downloadFile(downloadUrl, tempFile);

        if (localBinaryName.endsWith(".exe") || downloadUrl.endsWith(".zip")) {
            extractZip(tempFile, dataDir, localBinaryName);
        } else {
            extractTarGz(tempFile, dataDir, localBinaryName);
        }

        Files.deleteIfExists(tempFile);
        executablePath = dataDir.resolve(localBinaryName);
        return true;
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

    private void extractZip(Path zipPath, Path targetDir, String outputPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path outPath = targetDir.resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(targetDir)) {
                        throw new SecurityException("Invalid zip entry: " + entry.getName());
                    }
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                        }
                    }
                    if (entry.getName().equals("openlist") || entry.getName().equals("openlist.exe")) {
                        break;
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(Path tarGzPath, Path targetDir, String outputPath) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("tar", "xzf", tarGzPath.toString(), "-C", targetDir.toString());
            pb.start().waitFor();
        } catch (Exception e) {
            // fallback to zip download
            String fallbackUrl = downloadUrl.replace(".tar.gz", ".zip");
            Path zipPath = tarGzPath;
            downloadFile(fallbackUrl, zipPath);
            extractZip(zipPath, targetDir, outputPath);
        }
    }
}
