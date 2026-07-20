^package cn.net.rjnetwork.xianyu.manager.clouddisk.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cloud_storage_file")
public class CloudStorageFile extends BaseEntity {

    private Long storageAccountId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileHash;

    private String mimeType;

    private String shareLink;

    private String extractCode;

    private LocalDateTime shareExpiresAt;

    /** PENDING / UPLOADING / COMPLETED / FAILED */
    private String uploadStatus;

    private String remoteFileId;

    private String extraMeta;
}
