package cn.net.rjnetwork.xianyu.manager.clouddisk.controller;

import cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.CloudStorageService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 网盘存储 API
 */
@RestController
@RequestMapping("/api/cloud-storage")
public class CloudStorageController {

    private final CloudStorageService storageService;

    public CloudStorageController(CloudStorageService storageService) {
        this.storageService = storageService;
    }

    // ============== 账号管理 ==============

    @GetMapping("/accounts")
    public ApiResponse<List<CloudStorageAccount>> listAccounts(@RequestParam(required = false) Long accountId) {
        return ApiResponse.ok(storageService.listAccounts(accountId));
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<CloudStorageAccount> getAccount(@PathVariable Long id) {
        return ApiResponse.ok(storageService.getAccountById(id));
    }

    @PutMapping("/accounts/{id}")
    public ApiResponse<CloudStorageAccount> updateAccount(@PathVariable Long id, @RequestBody CloudStorageAccount account) {
        account.setId(id);
        return ApiResponse.ok(storageService.saveAccount(account));
    }

    @DeleteMapping("/accounts/{id}")
    public ApiResponse<Boolean> deleteAccount(@PathVariable Long id) {
        return ApiResponse.ok(storageService.deleteAccount(id));
    }

    // ============== OAuth ==============

    /**
     * GET /api/cloud-storage/auth-url?provider=BAIDU_NETDISK&redirectUri=http://localhost:8080/callback
     */
    @GetMapping("/auth-url")
    public ApiResponse<Map<String, String>> getAuthUrl(@RequestParam String provider,
                                                       @RequestParam String redirectUri) {
        return ApiResponse.ok(storageService.buildAuthUrl(provider, redirectUri));
    }

    /**
     * POST /api/cloud-storage/callback?provider=BAIDU_NETDISK&code=xxx&state=xxx&accountId=1
     */
    @PostMapping("/callback")
    public ApiResponse<CloudStorageAccount> handleCallback(@RequestParam String provider,
                                                           @RequestParam String code,
                                                           @RequestParam(required = false) String state,
                                                           @RequestParam Long accountId) {
        return ApiResponse.ok(storageService.handleCallback(provider, code, state, accountId));
    }

    // ============== 文件上传 ==============
    // 真实场景建议走分片上传（前端直接调网盘 SDK 或走本服务代理中转）

    @PostMapping("/accounts/{storageAccountId}/files")
    public ApiResponse<CloudStorageFile> uploadFile(@PathVariable Long storageAccountId,
                                                    @RequestParam MultipartFile file) {
        try {
            FileUploadRequest req = new FileUploadRequest();
            req.setFileName(file.getOriginalFilename());
            req.setFileSize(file.getSize());
            req.setMimeType(file.getContentType());
            req.setTargetPath("/xianyu-virtual-ship");
            req.setExpireDays(7);
            req.setContent(file.getInputStream());
            return ApiResponse.ok(storageService.uploadFile(storageAccountId, req));
        } catch (Exception e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    // ============== 分享 ==============

    @PostMapping("/files/{fileId}/share")
    public ApiResponse<String> shareFile(@PathVariable Long fileId) {
        return ApiResponse.ok(storageService.shareFile(fileId));
    }

    @DeleteMapping("/files/{fileId}/share")
    public ApiResponse<Boolean> cancelShare(@PathVariable Long fileId) {
        return ApiResponse.ok(storageService.cancelShare(fileId));
    }

    // ============== 文件查询 ==============

    @GetMapping("/files")
    public ApiResponse<List<CloudStorageFile>> listFiles(@RequestParam(required = false) Long storageAccountId) {
        return ApiResponse.ok(storageService.listFiles(storageAccountId));
    }

    @GetMapping("/files/{fileId}")
    public ApiResponse<CloudStorageFile> getFile(@PathVariable Long fileId) {
        return ApiResponse.ok(storageService.getFileById(fileId));
    }
}
