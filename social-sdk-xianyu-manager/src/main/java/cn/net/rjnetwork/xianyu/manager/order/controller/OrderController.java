package cn.net.rjnetwork.xianyu.manager.order.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<Page<XianyuOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String tab) {
        return ApiResponse.ok(orderService.listPage(page, size, accountId, tab));
    }

    @GetMapping("/{id}")
    public ApiResponse<XianyuOrder> getById(@PathVariable Long id) {
        XianyuOrder order = orderService.getById(id);
        if (order == null) return ApiResponse.fail("NOT_FOUND", "Order not found");
        return ApiResponse.ok(order);
    }

    @PostMapping("/{id}/delivery")
    public ApiResponse<XianyuOrder> delivery(@PathVariable Long id, @RequestParam String trackingNo) {
        return ApiResponse.ok(orderService.delivery(id, trackingNo));
    }
}
