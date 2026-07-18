package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiNotifyMessageVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiNotifyTemplateVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiNotifyChannelVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyMessageMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyTemplateMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyChannelMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyMessage;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyTemplate;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
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
@RequestMapping("/openapi/v1/notify")
public class OpenApiNotifyController {

    private final NotifyMessageMapper messageMapper;
    private final NotifyTemplateMapper templateMapper;
    private final NotifyChannelMapper channelMapper;
    private final OpenAppService openAppService;

    public OpenApiNotifyController(NotifyMessageMapper messageMapper, NotifyTemplateMapper templateMapper,
                                   NotifyChannelMapper channelMapper, OpenAppService openAppService) {
        this.messageMapper = messageMapper;
        this.templateMapper = templateMapper;
        this.channelMapper = channelMapper;
        this.openAppService = openAppService;
    }

    // ---------- 站内通知（按账号作用域；accountId 可空=全局通知） ----------
    @GetMapping("/messages")
    public OpenApiResponse<List<OpenApiNotifyMessageVO>> listMessages(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiNotifyMessageVO> result = messageMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || e.getAccountId() == null || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || (e.getAccountId() != null && e.getAccountId().equals(accountId)))
                .map(e -> {
                    OpenApiNotifyMessageVO vo = new OpenApiNotifyMessageVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/messages/{id}")
    public OpenApiResponse<OpenApiNotifyMessageVO> getMessage(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        NotifyMessage e = messageMapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "通知不存在");
        if (e.getAccountId() != null) openAppService.assertAccountAccessible(app, e.getAccountId());
        OpenApiNotifyMessageVO vo = new OpenApiNotifyMessageVO();
        BeanUtils.copyProperties(e, vo);
        return OpenApiResponse.ok(vo);
    }

    // ---------- 通知模板（全局目录，无敏感字段） ----------
    @GetMapping("/templates")
    public OpenApiResponse<List<OpenApiNotifyTemplateVO>> listTemplates() {
        List<OpenApiNotifyTemplateVO> result = templateMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(e -> {
                    OpenApiNotifyTemplateVO vo = new OpenApiNotifyTemplateVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    // ---------- 通知通道（全局，已脱敏排除 configJson 密文） ----------
    @GetMapping("/channels")
    public OpenApiResponse<List<OpenApiNotifyChannelVO>> listChannels() {
        List<OpenApiNotifyChannelVO> result = channelMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(e -> {
                    OpenApiNotifyChannelVO vo = new OpenApiNotifyChannelVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }
}
