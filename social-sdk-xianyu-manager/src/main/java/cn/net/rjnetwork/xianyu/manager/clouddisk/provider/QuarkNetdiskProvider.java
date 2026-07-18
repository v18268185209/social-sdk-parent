package cn.net.rjnetwork.xianyu.manager.clouddisk.provider;

import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Component
public class QuarkNetdiskProvider implements CloudStorageProvider {

    // 夸克网盘 OAuth 地址（开发占位，需去夸克开放平台申请 AppKey）
    // 夸克暂未开放公开网盘 API（2024 状态），本实现预留接口；上线前可对接夸克 UC 开发者文档
    private static final String AUTH_URL_OPEN = "https://auth.quark.cn/oauth/authorize?response_type=code&client_id=%s&scope=pan&redirect_uri=%s&state=%s";

    @Override public String getProviderType() { return "QUARK_NETDISK"; }

    @Override public Map<String, String> buildAuthUrl(String redirectUri) {
        String state = UUID.randomUUID().toString();
        return Map.of(
            "authUrl", String.format(AUTH_URL_OPEN, "YOUR_QUARK_APP_KEY", redirectUri, state),
            "state", state
        );
    }

    @Override public Map<String, String> handleCallback(String code, String state) { return Map.of(); }
    @Override public Map<String, String> refreshToken(String refreshToken) { return Map.of(); }
    @Override public Map<String, Long> getSpaceInfo(String accessToken) { return Map.of(); }
    @Override public String getUid(String accessToken) { return "TODO"; }

    @Override public FileUploadResult upload(FileUploadRequest request, InputStream content) {
        // TODO: 夸克网盘官方 API 接入（上传 + 分享需夸克内测权限）
        return new FileUploadResult("TODO", request.getFileName(), "UPLOADING");
    }

    @Override public String createShareLink(String fileId, String accessToken, String extractCode, Integer expireDays) {
        return "https://pan.quark.cn/s/PLACEHOLDER";
    }

    @Override public boolean cancelShare(String fileId, String accessToken) { return false; }
    @Override public boolean deleteFile(String fileId, String accessToken) { return false; }
    @Override public boolean exists(String fileId, String accessToken) { return false; }
}
