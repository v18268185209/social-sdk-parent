^package cn.net.rjnetwork.xianyu.manager.clouddisk.client;

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

    public String login() throws IOException {
        Map<String, String> req = new LinkedHashMap<>();
        req.put("username", properties.getUsername());
        req.put("password", properties.getPassword());

        String resp = postJson("/api/auth/login", req);
        token = extractToken(resp);
        tokenExpireTime = System.currentTimeMillis() + 86400000; // 24 hours
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

    public byte[] downloadFile(String path) throws IOException {
        String url = getBaseUrl() + "/api/fs/download?path=" + URLEncoder.encode(path, "UTF-8");
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        conn.setRequestMethod("GET");
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

    private String get(String endpoint) throws IOException {
        return sendGet(endpoint);
    }

    private String postJson(String endpoint, Map<String, ?> body) throws IOException {
        return sendPost(endpoint, body);
    }

    private String sendGet(String endpoint) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());
        return readResponse(conn);
    }

    private String sendPost(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        return readResponse(conn);
    }

    private void putJson(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        readResponse(conn);
    }

    private void deleteJson(String endpoint, Map<String, ?> body) throws IOException {
        URL u = new URL(getBaseUrl() + endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + requireToken());

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
