package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final VirtualShipTaskMapper shipTaskMapper;

    public OrderService(OrderMapper orderMapper, VirtualShipTaskMapper shipTaskMapper) {
        this.orderMapper = orderMapper;
        this.shipTaskMapper = shipTaskMapper;
    }

    public Page<XianyuOrder> listPage(int pageNum, int pageSize, Long accountId, String type) {
        Page<XianyuOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuOrder::getAccountId, accountId);
        }
        if ("SOLD".equals(type)) {
            wrapper.eq(XianyuOrder::getType, "SOLD");
        } else if ("BOUGHT".equals(type)) {
            wrapper.eq(XianyuOrder::getType, "BOUGHT");
        }
        wrapper.orderByDesc(XianyuOrder::getUpdatedAt);
        orderMapper.selectPage(page, wrapper);
        page.getRecords().forEach(this::fillVirtualShipTask);
        return page;
    }

    public XianyuOrder getById(Long id) {
        XianyuOrder order = orderMapper.selectById(id);
        fillVirtualShipTask(order);
        return order;
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

    private void fillVirtualShipTask(XianyuOrder order) {
        if (order == null) return;
        VirtualShipTask task = shipTaskMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipTask>()
                        .eq(VirtualShipTask::getOrderId, order.getId())
                        .last("LIMIT 1"));
        if (task == null) return;
        order.setVirtualShipTaskStatus(task.getStatus());
        order.setVirtualShipTaskError(task.getErrorMessage());
        order.setVirtualShipExecuteAt(task.getExecuteAt());
    }
}
