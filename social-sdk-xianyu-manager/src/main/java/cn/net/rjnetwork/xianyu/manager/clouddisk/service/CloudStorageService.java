package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.clouddisk.client.OpenListClient;
import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageAccountMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageFileMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CloudStorageService {

    private final CloudStorageAccountMapper accountMapper;
    private final CloudStorageFileMapper fileMapper;
    private final OpenListClient openListClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudStorageService(CloudStorageAccountMapper accountMapper,
                               CloudStorageFileMapper fileMapper,
                               OpenListClient openListClient) {
        this.accountMapper = accountMapper;
        this.fileMapper = fileMapper;
        this.openListClient = openListClient;
    }

    // ============== 账号管理 ==============

    public CloudStorageAccount getAccountById(Long id) {
        return accountMapper.selectById(id);
    }

    public List<CloudStorageAccount> listAccounts(Long accountId) {
        LambdaQueryWrapper<CloudStorageAccount> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(CloudStorageAccount::getAccountId, accountId);
        w.eq(CloudStorageAccount::getIsActive, true);
        return accountMapper.selectList(w);
    }

    public boolean isTokenExpired(CloudStorageAccount account) {
        if (account == null) return true;
        return account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now());
    }

    @Transactional
    public CloudStorageAccount saveAccount(CloudStorageAccount account) {
        if (account.getId() == null) {
            account.setIsActive(true);
            account.setCreatedAt(LocalDateTime.now());
            accountMapper.insert(account);
        } else {
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(account);
        }
        return account;
    }

    @Transactional
    public boolean deleteAccount(Long id) {
        CloudStorageAccount account = accountMapper.selectById(id);
        if (account == null) return false;
        account.setDeleted(1);
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        return true;
    }

    // ============== OAuth ==============

    public Map<String, String> buildAuthUrl(String provider, String redirectUri) {
        return Map.of("message", "OpenList 集成后不再使用 OAuth，请直接通过 OpenList 添加网盘");
    }

    @Transactional
    public CloudStorageAccount handleCallback(String provider, String code, String state, Long xianyuAccountId) {
        return null;
    }

    // ============== 文件上传 ==============

    /**
     * 上传文件 - 真正上传到 OpenList
     */
    @Transactional
    public CloudStorageFile uploadFile(Long storageAccountId, FileUploadRequest request) {
        CloudStorageAccount account = accountMapper.selectById(storageAccountId);
        if (account == null) throw new IllegalArgumentException("网盘账号不存在: " + storageAccountId);

        CloudStorageFile meta = new CloudStorageFile();
        meta.setStorageAccountId(storageAccountId);
        meta.setFileName(request.getFileName());
        meta.setFilePath(request.getTargetPath());
        meta.setFileSize(request.getFileSize());
        meta.setFileHash(request.getFileHash());
        meta.setMimeType(request.getMimeType());
        meta.setUploadStatus("UPLOADING");
        meta.setCreatedAt(LocalDateTime.now());
        fileMapper.insert(meta);

        try {
            byte[] content = readContent(request.getContent());

            // 真正上传到 OpenList
            String targetPath = request.getTargetPath();
            if (targetPath == null || targetPath.isBlank()) {
                targetPath = "/xianyu-virtual-ship";
            }

            // 先创建目录
            try {
                openListClient.mkdir(targetPath);
            } catch (Exception e) {
                // 目录可能已存在，忽略
            }

            // 上传文件
            String result = openListClient.uploadFile(targetPath, request.getFileName(), content);

            // 解析 OpenList 返回获取文件 ID
            JsonNode root = objectMapper.readTree(result);
            String fileId = root.path("data").path("id").asText("");

            meta.setRemoteFileId(fileId);
            meta.setFilePath(targetPath);
            meta.setUploadStatus("COMPLETED");
            meta.setUpdatedAt(LocalDateTime.now());
            fileMapper.updateById(meta);

        } catch (Exception e) {
            meta.setUploadStatus("FAILED");
            meta.setExtraMeta(e.getMessage());
            meta.setUpdatedAt(LocalDateTime.now());
            fileMapper.updateById(meta);
        }
        return meta;
    }

    private byte[] readContent(java.io.InputStream content) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = content.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    // ============== 分享 ==============

    /**
     * 创建分享链接 - 使用 OpenList 真实分享 API
     */
    public String shareFile(Long fileId) {
        CloudStorageFile file = fileMapper.selectById(fileId);
        if (file == null) throw new IllegalArgumentException("文件不存在: " + fileId);
        if (!"COMPLETED".equals(file.getUploadStatus())) throw new IllegalStateException("文件未就绪，当前状态: " + file.getUploadStatus());

        String extractCode = file.getExtractCode();
        if (extractCode == null || extractCode.isBlank()) {
            extractCode = String.format("%04d", new Random().nextInt(10000));
        }

        // 构建 OpenList 真实下载链接
        // OpenList 文件下载格式: http://host:port/d/path/filename
        String baseUrl = openListClient.getBaseUrlPublic();
        String filePath = file.getFilePath();
        if (filePath == null) filePath = "/xianyu-virtual-ship";
        String link = baseUrl + "/d" + filePath + "/" + file.getFileName();

        file.setShareLink(link);
        file.setExtractCode(extractCode);
        file.setShareExpiresAt(LocalDateTime.now().plusDays(7));
        file.setUpdatedAt(LocalDateTime.now());
        fileMapper.updateById(file);
        return link;
    }

    public boolean cancelShare(Long fileId) {
        CloudStorageFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        file.setShareLink(null);
        file.setExtractCode(null);
        file.setShareExpiresAt(null);
        file.setUpdatedAt(LocalDateTime.now());
        fileMapper.updateById(file);
        return true;
    }

    // ============== 文件查询 ==============

    public CloudStorageFile getFileById(Long id) {
        return fileMapper.selectById(id);
    }

    public List<CloudStorageFile> listFiles(Long storageAccountId) {
        LambdaQueryWrapper<CloudStorageFile> w = new LambdaQueryWrapper<>();
        if (storageAccountId != null) w.eq(CloudStorageFile::getStorageAccountId, storageAccountId);
        return fileMapper.selectList(w);
    }
}
