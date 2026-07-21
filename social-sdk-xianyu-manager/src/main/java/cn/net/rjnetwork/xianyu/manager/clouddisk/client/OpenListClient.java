package cn.net.rjnetwork.xianyu.manager.clouddisk.client;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.*;

@Component
public class OpenListClient {

    private final OpenListProperties properties;
    private String token;
    private long tokenExpireTime = 0;

    public OpenListClient(OpenListProperties properties) {
        this.properties = properties;
    }

    private String getBaseUrl() {
        return properties.getUrl().replaceFirst("/$", "");
    }

    /** 暴露给 Service 使用 */
    public String getBaseUrlPublic() {
        return getBaseUrl();
    }

    // ============== 认证 ==============

    public String login() throws IOException {
        Map<String, String> req = new LinkedHashMap<>();
        req.put("username", properties.getUsername());
        req.put("password", properties.getPassword());

        String resp = postJson("/api/auth/login", req);
        token = extractToken(resp);
        tokenExpireTime = System.currentTimeMillis() + 86400000;
        return token;
    }

    private String extractToken(String json) throws IOException {
        int idx = json.indexOf("\"data\"");
        if (idx < 0) throw new IOException("无法从响应中提取 token");
        int tokenIdx = json.indexOf("\"token\"", idx);
        if (tokenIdx < 0) throw new IOException("无 token 字段");
        int valueStart = json.indexOf("\"", tokenIdx + 7) + 1;
        int valueEnd = json.indexOf("\"", valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private String requireToken() throws IOException {
        if (token == null || System.currentTimeMillis() > tokenExpireTime) {
            return login();
        }
        return token;
    }

    // ============== 文件操作 ==============

    public String listFiles(String path) throws IOException {
        return get("/api/fs/list?path=" + URLEncoder.encode(path, "UTF-8"));
    }

    public String getFile(String path) throws IOException {
        return get("/api/fs/get?path=" + URLEncoder.encode(path, "UTF-8"));
    }

    public void mkdir(String path) throws IOException {
        Map<String, String> req = new LinkedHashMap<>();
        req.put("path", path);
        req.put("name", new File(path).getName());
        postJson("/api/fs/mkdir", req);
    }

    public void rename(String path, String newName) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("path", path);
        req.put("name", newName);
        putJson("/api/fs/rename", req);
    }

    public void move(List<String> paths, String targetDir) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("paths", paths);
        req.put("target_dir", targetDir);
        putJson("/api/fs/move", req);
    }

    public void copy(List<String> paths, String targetDir) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("paths", paths);
        req.put("target_dir", targetDir);
        putJson("/api/fs/copy", req);
    }

    public void delete(List<String> paths) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("paths", paths);
        deleteJson("/api/fs/remove", req);
    }

    /**
     * 下载文件 - 返回原始字节
     */
    public byte[] downloadFile(String path) throws IOException {
        String url = getBaseUrl() + "/api/fs/download?path=" + URLEncoder.encode(path, "UTF-8");
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("下载失败: HTTP " + status);
        }
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * 上传文件到 OpenList - 使用 /api/fs/put
     */
    public String uploadFile(String path, String filename, byte[] content) throws IOException {
        String url = getBaseUrl() + "/api/fs/put?path=" + URLEncoder.encode(path, "UTF-8");
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("File-Path", URLEncoder.encode(path + "/" + filename, "UTF-8"));
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(content);
        }

        return readResponse(conn);
    }

    /**
     * 上传文件到 OpenList - 接受 MultipartFile
     */
    public String uploadFile(String path, org.springframework.web.multipart.MultipartFile file) throws IOException {
        return uploadFile(path, file.getOriginalFilename(), file.getBytes());
    }

    // ============== 存储管理 (Admin API) ==============

    /**
     * GET /api/admin/storage/list — 列出所有存储挂载
     */
    public String listStorages() throws IOException {
        return get("/api/admin/storage/list");
    }

    /**
     * POST /api/admin/storage/create — 添加存储挂载
     */
    public String createStorage(Map<String, Object> storage) throws IOException {
        return postJson("/api/admin/storage/create", storage);
    }

    /**
     * POST /api/admin/storage/update — 更新存储挂载
     */
    public String updateStorage(Map<String, Object> storage) throws IOException {
        return postJson("/api/admin/storage/update", storage);
    }

    /**
     * POST /api/admin/storage/delete?id=xxx — 删除存储挂载
     */
    public String deleteStorage(long id) throws IOException {
        return post("/api/admin/storage/delete?id=" + id);
    }

    /**
     * POST /api/admin/storage/enable?id=xxx — 启用存储挂载
     */
    public String enableStorage(long id) throws IOException {
        return post("/api/admin/storage/enable?id=" + id);
    }

    /**
     * POST /api/admin/storage/disable?id=xxx — 禁用存储挂载
     */
    public String disableStorage(long id) throws IOException {
        return post("/api/admin/storage/disable?id=" + id);
    }

    /**
     * GET /api/admin/driver/info?driver=xxx — 获取驱动配置 schema
     */
    public String getDriverInfo(String driverName) throws IOException {
        return get("/api/admin/driver/info?driver=" + URLEncoder.encode(driverName, "UTF-8"));
    }

    // ============== HTTP 底层 ==============

    private String get(String endpoint) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        return readResponse(conn);
    }

    private String post(String endpoint) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        return readResponse(conn);
    }

    private String postJson(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        return readResponse(conn);
    }

    private String putJson(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        return readResponse(conn);
    }

    private void deleteJson(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("未知错误");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        if (status >= 400) {
            throw new IOException("HTTP " + status + ": " + sb.toString());
        }
        return sb.toString();
    }
}
