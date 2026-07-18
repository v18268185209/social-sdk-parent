package cn.net.rjnetwork.xianyu.manager.clouddisk.dto;

import lombok.Data;

/** 文件上传结果 */
@Data
public class FileUploadResult {
    /** 网盘侧 file_id */
    private String remoteFileId;
    private String fileName;
    /** PENDING / UPLOADING / COMPLETED / FAILED */
    private String status;
    /** 失败时的错误信息 */
    private String errorMessage;

    public FileUploadResult(String remoteFileId, String fileName, String status) {
        this.remoteFileId = remoteFileId;
        this.fileName = fileName;
        this.status = status;
    }
}
