package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiVirtualShipConfigVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiVirtualShipTaskVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipConfigMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
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
@RequestMapping("/openapi/v1/virtual-ship")
public class OpenApiVirtualShipController {

    private final VirtualShipConfigMapper configMapper;
    private final VirtualShipTaskMapper taskMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OpenAppService openAppService;

    public OpenApiVirtualShipController(VirtualShipConfigMapper configMapper, VirtualShipTaskMapper taskMapper,
                                        ProductMapper productMapper, OrderMapper orderMapper, OpenAppService openAppService) {
        this.configMapper = configMapper;
        this.taskMapper = taskMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.openAppService = openAppService;
    }

    // ---------- 发货配置（直接 accountId） ----------
    @GetMapping("/configs")
    public OpenApiResponse<List<OpenApiVirtualShipConfigVO>> listConfigs(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiVirtualShipConfigVO> result = configMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || e.getAccountId() == null || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || (e.getAccountId() != null && e.getAccountId().equals(accountId)))
                .map(e -> {
                    OpenApiVirtualShipConfigVO vo = new OpenApiVirtualShipConfigVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/configs/{id}")
    public OpenApiResponse<OpenApiVirtualShipConfigVO> getConfig(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        VirtualShipConfig e = configMapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "发货配置不存在");
        if (e.getAccountId() != null) openAppService.assertAccountAccessible(app, e.getAccountId());
        OpenApiVirtualShipConfigVO vo = new OpenApiVirtualShipConfigVO();
        BeanUtils.copyProperties(e, vo);
        return OpenApiResponse.ok(vo);
    }

    // ---------- 发货任务（经 productId / orderId 反查 accountId） ----------
    @GetMapping("/tasks")
    public OpenApiResponse<List<OpenApiVirtualShipTaskVO>> listTasks(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiVirtualShipTaskVO> result = taskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(t -> toTaskVo(t, resolveAccountId(t)))
                .filter(vo -> bound.isEmpty() || (vo.getAccountId() != null && bound.contains(vo.getAccountId())))
                .filter(vo -> accountId == null || accountId.equals(vo.getAccountId()))
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/tasks/{id}")
    public OpenApiResponse<OpenApiVirtualShipTaskVO> getTask(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        VirtualShipTask t = taskMapper.selectById(id);
        if (t == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "发货任务不存在");
        Long aid = resolveAccountId(t);
        if (aid != null) openAppService.assertAccountAccessible(app, aid);
        return OpenApiResponse.ok(toTaskVo(t, aid));
    }

    private OpenApiVirtualShipTaskVO toTaskVo(VirtualShipTask t, Long accountId) {
        OpenApiVirtualShipTaskVO vo = new OpenApiVirtualShipTaskVO();
        BeanUtils.copyProperties(t, vo);
        vo.setAccountId(accountId);
        return vo;
    }

    private Long resolveAccountId(VirtualShipTask t) {
        if (t.getProductId() != null) {
            XianyuProduct p = productMapper.selectById(t.getProductId());
            if (p != null && p.getAccountId() != null) return p.getAccountId();
        }
        if (t.getOrderId() != null) {
            XianyuOrder o = orderMapper.selectById(t.getOrderId());
            if (o != null && o.getAccountId() != null) return o.getAccountId();
        }
        return null;
    }
}
