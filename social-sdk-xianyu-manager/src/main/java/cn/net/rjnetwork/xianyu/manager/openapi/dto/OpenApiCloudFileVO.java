^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiCloudFileVO {

    /** 由 storageAccountId 反查得到，便于外部按账号过滤与展示 */
    private Long accountId;
    private Long id;
    private Long storageAccountId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileHash;
    private String mimeType;
    private LocalDateTime shareExpiresAt;
    private String uploadStatus;
    private String remoteFileId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 注意：shareLink / extractCode / extraMeta 为文件访问凭据，已脱敏排除
}
