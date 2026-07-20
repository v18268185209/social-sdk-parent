package cn.net.rjnetwork.xianyu.manager.clouddisk.dto;

import lombok.Data;
import java.io.InputStream;

/** 文件上传请求 */
@Data
public class FileUploadRequest {
    private String fileName;
    private String fileHash;
    private Long fileSize;
    private String mimeType;
    /** �盘内目标目录，默认 /xianyu-virtual-ship */
    private String targetPath;
    private String description;
    /** 分享提取码（null 表示系统随机生成） */
    private String extractCode;
    /** 分享有效期（天），默认 7 */
    private Integer expireDays;
    private InputStream content;
}
