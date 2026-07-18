package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadResult;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageAccountMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageFileMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import cn.net.rjnetwork.xianyu.manager.clouddisk.provider.BaiduNetdiskProvider;
import cn.net.rjnetwork.xianyu.manager.clouddisk.provider.CloudStorageProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CloudStorageService {

    private final CloudStorageAccountMapper accountMapper;
    private final CloudStorageFileMapper fileMapper;

    private final BaiduNetdiskProvider baiduProvider;

    private static final List<String> PROVIDER_TYPES = List.of("BAIDU_NETDISK", "QUARK_NETDISK", "ALIYUN_DRIVE");

    public CloudStorageService(CloudStorageAccountMapper accountMapper,
                               CloudStorageFileMapper fileMapper,
                               BaiduNetdiskProvider baiduProvider) {
        this.accountMapper = accountMapper;
        this.fileMapper = fileMapper;
        this.baiduProvider = baiduProvider;
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
        switch (provider) {
            case "BAIDU_NETDISK" -> {
                return baiduProvider.buildAuthUrl(redirectUri);
            }
            case "QUARK_NETDISK", "ALIYUN_DRIVE" -> {
                return Map.of("message", "暂不支持：" + provider);
            }
            default -> throw new IllegalArgumentException("未知网盘类型: " + provider);
        }
    }

    @Transactional
    public CloudStorageAccount handleCallback(String provider, String code, String state, Long xianyuAccountId) {
        CloudStorageProvider prov = getProvider(provider);
        Map<String, String> tokenInfo = prov.handleCallback(code, state);

        String accessToken = tokenInfo.getOrDefault("accessToken", "");
        String refreshToken = tokenInfo.getOrDefault("refreshToken", "");
        String expiresIn = tokenInfo.getOrDefault("expiresIn", "3600");
        String uid = tokenInfo.getOrDefault("uid", prov.getUid(accessToken));

        CloudStorageAccount account = new CloudStorageAccount();
        account.setAccountId(xianyuAccountId);
        account.setProvider(provider);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(Long.parseLong(expiresIn)));
        account.setUid(uid);

        Map<String, Long> space = prov.getSpaceInfo(accessToken);
        account.setTotalSpace(space.getOrDefault("total", 0L));
        account.setUsedSpace(space.getOrDefault("used", 0L));

        return saveAccount(account);
    }

    private CloudStorageProvider getProvider(String provider) {
        switch (provider) {
            case "BAIDU_NETDISK" -> { return baiduProvider; }
            case "QUARK_NETDISK", "ALIYUN_DRIVE" -> { return baiduProvider; /* fallback */ }
            default -> throw new IllegalArgumentException("未知网盘: " + provider);
        }
    }

    // ============== 文件上传 ==============

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
            FileUploadResult result = baiduProvider.upload(request, request.getContent());
            meta.setRemoteFileId(result.getRemoteFileId());
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

    // ============== 分享 ==============

    /**
     * 创建分享链接（默认 7 天有效期 + 4 位随机提取码）
     */
    public String shareFile(Long fileId) {
        CloudStorageFile file = fileMapper.selectById(fileId);
        if (file == null) throw new IllegalArgumentException("文件不存在: " + fileId);
        if (!"COMPLETED".equals(file.getUploadStatus())) throw new IllegalStateException("文件未就绪，当前状态: " + file.getUploadStatus());

        CloudStorageAccount account = accountMapper.selectById(file.getStorageAccountId());
        String extractCode = file.getExtractCode();
        if (extractCode == null || extractCode.isBlank()) {
            extractCode = String.format("%04d", new Random().nextInt(10000));
        }

        String link = baiduProvider.createShareLink(file.getRemoteFileId(), account.getAccessToken(), extractCode, 7);
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
        CloudStorageAccount account = accountMapper.selectById(file.getStorageAccountId());
        boolean ok = baiduProvider.cancelShare(file.getRemoteFileId(), account.getAccessToken());
        if (ok) {
            file.setShareLink(null);
            file.setExtractCode(null);
            file.setShareExpiresAt(null);
            file.setUpdatedAt(LocalDateTime.now());
            fileMapper.updateById(file);
        }
        return ok;
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
