^package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiOrderVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 订单域对外接口：列表按绑定白名单过滤，详情做账号作用域校验，响应脱敏（排除实际发货内容）。
 */
@RestController
@RequestMapping("/openapi/v1/orders")
public class OpenApiOrderController {

    private final OrderMapper orderMapper;
    private final OpenAppService openAppService;

    public OpenApiOrderController(OrderMapper orderMapper, OpenAppService openAppService) {
        this.orderMapper = orderMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiOrderVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiOrderVO> result = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()).stream()
                .filter(o -> bound.isEmpty() || bound.contains(o.getAccountId()))
                .filter(o -> accountId == null || o.getAccountId().equals(accountId))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiOrderVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        XianyuOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "订单不存在");
        }
        openAppService.assertAccountAccessible(app, order.getAccountId());
        return OpenApiResponse.ok(toVo(order));
    }

    private OpenApiOrderVO toVo(XianyuOrder o) {
        OpenApiOrderVO vo = new OpenApiOrderVO();
        vo.setId(o.getId());
        vo.setAccountId(o.getAccountId());
        vo.setType(o.getType());
        vo.setOrderId(o.getOrderId());
        vo.setItemTitle(o.getItemTitle());
        vo.setCounterpartyName(o.getCounterpartyName());
        vo.setAmount(o.getAmount());
        vo.setStatus(o.getStatus());
        vo.setTrackingNo(o.getTrackingNo());
        vo.setOrderTime(o.getOrderTime());
        vo.setGoodsType(o.getGoodsType());
        vo.setRequireVirtualShip(o.getRequireVirtualShip());
        vo.setVirtualShippedAt(o.getVirtualShippedAt());
        vo.setAutoReceiptAt(o.getAutoReceiptAt());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setUpdatedAt(o.getUpdatedAt());
        return vo;
    }
}
