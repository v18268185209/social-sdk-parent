package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiCloudAccountVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiCloudFileVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageAccountMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.CloudStorageFileMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/openapi/v1/cloud")
public class OpenApiCloudStorageController {

    private final CloudStorageAccountMapper accountMapper;
    private final CloudStorageFileMapper fileMapper;
    private final OpenAppService openAppService;

    public OpenApiCloudStorageController(CloudStorageAccountMapper accountMapper, CloudStorageFileMapper fileMapper,
                                         OpenAppService openAppService) {
        this.accountMapper = accountMapper;
        this.fileMapper = fileMapper;
        this.openAppService = openAppService;
    }

    // ---------- 云盘账号（直接 accountId，已脱敏排除 token） ----------
    @GetMapping("/accounts")
    public OpenApiResponse<List<OpenApiCloudAccountVO>> listAccounts(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiCloudAccountVO> result = accountMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || e.getAccountId() == null || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || (e.getAccountId() != null && e.getAccountId().equals(accountId)))
                .map(e -> {
                    OpenApiCloudAccountVO vo = new OpenApiCloudAccountVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/accounts/{id}")
    public OpenApiResponse<OpenApiCloudAccountVO> getAccount(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        CloudStorageAccount e = accountMapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "云盘账号不存在");
        if (e.getAccountId() != null) openAppService.assertAccountAccessible(app, e.getAccountId());
        OpenApiCloudAccountVO vo = new OpenApiCloudAccountVO();
        BeanUtils.copyProperties(e, vo);
        return OpenApiResponse.ok(vo);
    }

    // ---------- 云盘文件（经 storageAccountId 反查 accountId，已脱敏排除 shareLink/extractCode） ----------
    @GetMapping("/files")
    public OpenApiResponse<List<OpenApiCloudFileVO>> listFiles(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiCloudFileVO> result = fileMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(f -> toFileVo(f, resolveAccountId(f)))
                .filter(vo -> bound.isEmpty() || (vo.getAccountId() != null && bound.contains(vo.getAccountId())))
                .filter(vo -> accountId == null || accountId.equals(vo.getAccountId()))
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/files/{id}")
    public OpenApiResponse<OpenApiCloudFileVO> getFile(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        CloudStorageFile f = fileMapper.selectById(id);
        if (f == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "云盘文件不存在");
        Long aid = resolveAccountId(f);
        if (aid != null) openAppService.assertAccountAccessible(app, aid);
        return OpenApiResponse.ok(toFileVo(f, aid));
    }

    private OpenApiCloudFileVO toFileVo(CloudStorageFile f, Long accountId) {
        OpenApiCloudFileVO vo = new OpenApiCloudFileVO();
        BeanUtils.copyProperties(f, vo);
        vo.setAccountId(accountId);
        return vo;
    }

    private Long resolveAccountId(CloudStorageFile f) {
        if (f.getStorageAccountId() != null) {
            CloudStorageAccount a = accountMapper.selectById(f.getStorageAccountId());
            if (a != null) return a.getAccountId();
        }
        return null;
    }
}
