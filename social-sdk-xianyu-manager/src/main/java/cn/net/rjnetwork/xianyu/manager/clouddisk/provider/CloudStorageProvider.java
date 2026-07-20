^package cn.net.rjnetwork.xianyu.manager.clouddisk.provider;

import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadResult;

import java.io.InputStream;
import java.util.Map;

/**
 * 网盘存储 Provider 接口 — 所有网盘实现（百度 / 夸克 / 阿里云盘）统一遵守此契约
 */
public interface CloudStorageProvider {

    /** 唯一标识 BAIDU_NETDISK / QUARK_NETDISK / ALIYUN_DRIVE */
    String getProviderType();

    // ============== OAuth 流程 ==============

    /**
     * 生成授权 URL（第一步）
     * @return redirectUrl + state
     */
    Map<String, String> buildAuthUrl(String redirectUri);

    /**
     * 回调后拿 token（第二步）
     * @param code  回调 code
     * @param state 防重放 state
     * @return { accessToken, refreshToken, expiresIn, uid }
     */
    Map<String, String> handleCallback(String code, String state);

    /**
     * 刷新 token
     */
    Map<String, String> refreshToken(String refreshToken);

    // ============== 账号信息 ==============

    /** 查询账号空间使用情况 */
    Map<String, Long> getSpaceInfo(String accessToken);

    String getUid(String accessToken);

    // ============== 文件上传 ==============

    /**
     * 流式上传文件（大文件分片 / 小文件直传由 provider 内部决定）
     * @param request 元数据
     * @param content 文件输入流
     */
    FileUploadResult upload(FileUploadRequest request, InputStream content);

    // ============== 分享链接 ==============

    /** 创建分享链接 */
    String createShareLink(String fileId, String accessToken, String extractCode, Integer expireDays);

    /** 取消分享 */
    boolean cancelShare(String fileId, String accessToken);

    // ============== 文件管理 ==============

    /** 删除网盘文件 */
    boolean deleteFile(String fileId, String accessToken);

    /** 校验文件是否存在 */
    boolean exists(String fileId, String accessToken);
}
