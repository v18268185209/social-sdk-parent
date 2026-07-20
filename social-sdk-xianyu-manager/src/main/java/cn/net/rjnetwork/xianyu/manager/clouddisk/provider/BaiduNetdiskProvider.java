^package cn.net.rjnetwork.xianyu.manager.clouddisk.provider;

import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Component
public class BaiduNetdiskProvider implements CloudStorageProvider {

    // 百度网盘 OAuth 地址（开发用占位，实际需去百度开放平台申请 AppKey）
    private static final String AUTH_URL = "https://openapi.baidu.com/oauth/2.0/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=basic,netdisk&state=%s";
    private static final String TOKEN_URL = "https://openapi.baidu.com/oauth/2.0/token";
    private static final String API_BASE = "https://pan.baidu.com/rest/2.0/xpan";

    @Override public String getProviderType() { return "BAIDU_NETDISK"; }

    @Override public Map<String, String> buildAuthUrl(String redirectUri) {
        // TODO: 读取配置里的 baidu.clientId；state 防重放
        String state = UUID.randomUUID().toString();
        return Map.of(
            "authUrl", String.format(AUTH_URL, "YOUR_APP_KEY", redirectUri, state),
            "state", state
        );
    }

    @Override public Map<String, String> handleCallback(String code, String state) {
        // TODO: POST TOKEN_URL 拿 access_token
        return Map.of("accessToken", "TODO", "refreshToken", "TODO", "expiresIn", "31536000");
    }

    @Override public Map<String, String> refreshToken(String refreshToken) { return Map.of(); }

    @Override public Map<String, Long> getSpaceInfo(String accessToken) {
        return Map.of("total", 0L, "used", 0L, "free", 0L);
    }

    @Override public String getUid(String accessToken) { return "TODO"; }

    @Override public FileUploadResult upload(FileUploadRequest request, InputStream content) {
        // TODO: 1. POST {API_BASE}/file?method=precreate  2. 分片上传到 /tmp  3. POST /create?method=create
        return new FileUploadResult("TODO", request.getFileName(), "UPLOADING");
    }

    @Override public String createShareLink(String fileId, String accessToken, String extractCode, Integer expireDays) {
        // TODO: POST {API_BASE}/share?method=set
        return "https://pan.baidu.com/s/PLACEHOLDER";
    }

    @Override public boolean cancelShare(String fileId, String accessToken) { return false; }

    @Override public boolean deleteFile(String fileId, String accessToken) { return false; }

    @Override public boolean exists(String fileId, String accessToken) { return false; }
}
