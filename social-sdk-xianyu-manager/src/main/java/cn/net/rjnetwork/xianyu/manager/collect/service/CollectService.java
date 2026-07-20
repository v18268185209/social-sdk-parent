^package cn.net.rjnetwork.xianyu.manager.collect.service;

import cn.net.rjnetwork.xianyu.manager.collect.mapper.CollectMapper;
import cn.net.rjnetwork.xianyu.manager.collect.model.XianyuCollect;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CollectService {

    private final CollectMapper collectMapper;

    public CollectService(CollectMapper collectMapper) {
        this.collectMapper = collectMapper;
    }

    public List<XianyuCollect> list(Long accountId, String targetType) {
        LambdaQueryWrapper<XianyuCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuCollect::getAccountId, accountId);
        if (targetType != null) {
            wrapper.eq(XianyuCollect::getTargetType, targetType);
        }
        wrapper.orderByDesc(XianyuCollect::getCollectedAt);
        return collectMapper.selectList(wrapper);
    }

    public void add(XianyuCollect collect) {
        collect.setCreatedAt(LocalDateTime.now());
        collect.setUpdatedAt(LocalDateTime.now());
        collectMapper.insert(collect);
    }

    public void remove(Long id) {
        collectMapper.deleteById(id);
    }
}
