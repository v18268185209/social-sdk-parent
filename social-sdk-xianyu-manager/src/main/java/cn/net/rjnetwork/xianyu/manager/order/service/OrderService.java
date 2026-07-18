package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public Page<XianyuOrder> listPage(int pageNum, int pageSize, Long accountId, String tab) {
        Page<XianyuOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuOrder::getAccountId, accountId);
        }
        if ("sold".equals(tab)) {
            wrapper.eq(XianyuOrder::getStatus, "PAID");
        } else if ("bought".equals(tab)) {
            wrapper.eq(XianyuOrder::getStatus, "PENDING");
        }
        wrapper.orderByDesc(XianyuOrder::getUpdatedAt);
        orderMapper.selectPage(page, wrapper);
        return page;
    }

    public XianyuOrder getById(Long id) {
        return orderMapper.selectById(id);
    }

    public XianyuOrder delivery(Long orderId, String trackingNo) {
        XianyuOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        order.setTrackingNo(trackingNo);
        order.setStatus("SHIPPED");
        orderMapper.updateById(order);
        return order;
    }

    public void saveOrder(XianyuOrder order) {
        order.setCreatedAt(java.time.LocalDateTime.now());
        order.setUpdatedAt(java.time.LocalDateTime.now());
        orderMapper.insert(order);
    }
}
