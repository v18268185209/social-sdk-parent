package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiOpsTaskVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiOpsSuggestionVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.ops.mapper.AiOpsTaskMapper;
import cn.net.rjnetwork.xianyu.manager.ops.mapper.AiOpsSuggestionMapper;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsTask;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsSuggestion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

@RestController
@RequestMapping("/openapi/v1/ai/ops")
public class OpenApiAiOpsController {

    private final AiOpsTaskMapper taskMapper;
    private final AiOpsSuggestionMapper suggestionMapper;
    private final OpenAppService openAppService;

    public OpenApiAiOpsController(AiOpsTaskMapper taskMapper, AiOpsSuggestionMapper suggestionMapper, OpenAppService openAppService) {
        this.taskMapper = taskMapper;
        this.suggestionMapper = suggestionMapper;
        this.openAppService = openAppService;
    }

    @GetMapping("/tasks")
    public OpenApiResponse<List<OpenApiAiOpsTaskVO>> listTasks(@RequestParam(required = false) Long accountId) {
        return OpenApiResponse.ok(query(taskMapper, accountId, OpenApiAiOpsTaskVO::new, AiOpsTask::getAccountId));
    }

    @GetMapping("/tasks/{id}")
    public OpenApiResponse<OpenApiAiOpsTaskVO> getTask(@PathVariable Long id) {
        return OpenApiResponse.ok(detail(taskMapper, id, OpenApiAiOpsTaskVO::new, AiOpsTask::getAccountId, "运营任务不存在"));
    }

    @GetMapping("/suggestions")
    public OpenApiResponse<List<OpenApiAiOpsSuggestionVO>> listSuggestions(@RequestParam(required = false) Long accountId) {
        return OpenApiResponse.ok(query(suggestionMapper, accountId, OpenApiAiOpsSuggestionVO::new, AiOpsSuggestion::getAccountId));
    }

    @GetMapping("/suggestions/{id}")
    public OpenApiResponse<OpenApiAiOpsSuggestionVO> getSuggestion(@PathVariable Long id) {
        return OpenApiResponse.ok(detail(suggestionMapper, id, OpenApiAiOpsSuggestionVO::new, AiOpsSuggestion::getAccountId, "运营建议不存在"));
    }

    private <E, V> List<V> query(com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Long accountId,
                                 java.util.function.Supplier<V> voCtor, Function<E, Long> accountIdFn) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        return mapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || accountIdFn.apply(e) == null || bound.contains(accountIdFn.apply(e)))
                .filter(e -> accountId == null || (accountIdFn.apply(e) != null && accountIdFn.apply(e).equals(accountId)))
                .map(e -> {
                    V vo = voCtor.get();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
    }

    private <E, V> V detail(com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Long id,
                           java.util.function.Supplier<V> voCtor, Function<E, Long> accountIdFn, String notFound) {
        OpenApp app = OpenApiContext.getOpenApp();
        E e = mapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, notFound);
        Long aid = accountIdFn.apply(e);
        if (aid != null) openAppService.assertAccountAccessible(app, aid);
        V vo = voCtor.get();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
