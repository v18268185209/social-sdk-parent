package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAutoReplyLogVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.reply.mapper.AutoReplyLogMapper;
import cn.net.rjnetwork.xianyu.manager.reply.model.XianyuAutoReplyLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 自动回复日志对外接口：按绑定白名单过滤。
 */
@RestController
@RequestMapping("/openapi/v1/auto-reply-logs")
public class OpenApiAutoReplyLogController {

    private final AutoReplyLogMapper logMapper;
    private final OpenAppService openAppService;

    public OpenApiAutoReplyLogController(AutoReplyLogMapper logMapper, OpenAppService openAppService) {
        this.logMapper = logMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiAutoReplyLogVO>> list(@RequestParam(required = false) Long accountId,
                                                             @RequestParam(required = false) String replyType,
                                                             @RequestParam(required = false) Boolean matched,
                                                             @RequestParam(required = false) LocalDateTime from,
                                                             @RequestParam(required = false) LocalDateTime to) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);

        LambdaQueryWrapper<XianyuAutoReplyLog> qw = new LambdaQueryWrapper<XianyuAutoReplyLog>()
                .orderByDesc(XianyuAutoReplyLog::getCreatedAt);
        if (replyType != null && !replyType.isBlank()) qw.eq(XianyuAutoReplyLog::getReplyType, replyType);
        if (matched != null) qw.eq(XianyuAutoReplyLog::getMatched, matched);
        if (from != null) qw.ge(XianyuAutoReplyLog::getCreatedAt, from);
        if (to != null) qw.le(XianyuAutoReplyLog::getCreatedAt, to);

        List<OpenApiAutoReplyLogVO> result = logMapper.selectList(qw).stream()
                .filter(log -> bound.isEmpty() || bound.contains(log.getAccountId()))
                .filter(log -> accountId == null || accountId.equals(log.getAccountId()))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    private OpenApiAutoReplyLogVO toVo(XianyuAutoReplyLog log) {
        OpenApiAutoReplyLogVO vo = new OpenApiAutoReplyLogVO();
        vo.setId(log.getId());
        vo.setAccountId(log.getAccountId());
        vo.setRuleId(log.getRuleId());
        vo.setRuleName(log.getRuleName());
        vo.setReplyType(log.getReplyType());
        vo.setKeyword(log.getKeyword());
        vo.setBuyerMessage(log.getBuyerMessage());
        vo.setReplyText(log.getReplyText());
        vo.setMatched(log.getMatched());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
