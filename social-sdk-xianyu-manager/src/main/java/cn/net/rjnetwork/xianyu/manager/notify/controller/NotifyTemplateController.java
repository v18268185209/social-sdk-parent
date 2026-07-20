^package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyTemplateMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 通知模板配置（按场景）。列出全部内置场景，可覆盖默认模板。
 */
@RestController
@RequestMapping("/api/notify/templates")
public class NotifyTemplateController {

    private final NotifyTemplateMapper templateMapper;

    public NotifyTemplateController(NotifyTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /** 内置场景列表（含当前已保存的覆盖模板） */
    @GetMapping
    public ApiResponse<List<NotifyTemplate>> list() {
        return ApiResponse.ok(templateMapper.selectList(
                new LambdaQueryWrapper<NotifyTemplate>().orderByAsc(NotifyTemplate::getScenario)));
    }

    /** 所有场景常量（即使没有库内模板也能前端渲染） */
    @GetMapping("/scenarios")
    public ApiResponse<List<java.util.Map<String, String>>> scenarios() {
        List<java.util.Map<String, String>> list = Arrays.stream(
                cn.net.rjnetwork.xianyu.manager.notify.NotifyScenario.values())
                .map(s -> java.util.Map.of(
                        "scenario", s.name(),
                        "label", s.getLabel(),
                        "defaultTitle", s.getDefaultTitle(),
                        "defaultBody", s.getDefaultBody(),
                        "cooldownSeconds", String.valueOf(s.getCooldownSeconds())))
                .toList();
        return ApiResponse.ok(list);
    }

    @PostMapping
    public ApiResponse<NotifyTemplate> upsert(@RequestBody NotifyTemplate tpl) {
        if (tpl.getScenario() == null || tpl.getScenario().isBlank()) {
            return ApiResponse.fail("scenario 不能为空");
        }
        NotifyTemplate existing = templateMapper.selectOne(
                new LambdaQueryWrapper<NotifyTemplate>().eq(NotifyTemplate::getScenario, tpl.getScenario()));
        if (existing != null) {
            existing.setTitleTpl(tpl.getTitleTpl());
            existing.setBodyTpl(tpl.getBodyTpl());
            existing.setEnabled(tpl.getEnabled() != null ? tpl.getEnabled() : true);
            templateMapper.updateById(existing);
            return ApiResponse.ok(existing);
        }
        templateMapper.insert(tpl);
        return ApiResponse.ok(tpl);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
