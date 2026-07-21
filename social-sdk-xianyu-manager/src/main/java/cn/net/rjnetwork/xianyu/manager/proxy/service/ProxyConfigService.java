package cn.net.rjnetwork.xianyu.manager.proxy.service;

import cn.net.rjnetwork.xianyu.manager.proxy.mapper.ProxyConfigMapper;
import cn.net.rjnetwork.xianyu.manager.proxy.model.ProxyConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProxyConfigService {

    private final ProxyConfigMapper mapper;

    public ProxyConfigService(ProxyConfigMapper mapper) {
        this.mapper = mapper;
    }

    public List<ProxyConfig> listAll() {
        LambdaQueryWrapper<ProxyConfig> w = new LambdaQueryWrapper<>();
        w.orderByAsc(ProxyConfig::getSortOrder);
        return mapper.selectList(w);
    }

    public List<ProxyConfig> listEnabled() {
        LambdaQueryWrapper<ProxyConfig> w = new LambdaQueryWrapper<>();
        w.eq(ProxyConfig::getEnabled, 1).orderByAsc(ProxyConfig::getSortOrder);
        return mapper.selectList(w);
    }

    public ProxyConfig findByType(String providerType) {
        LambdaQueryWrapper<ProxyConfig> w = new LambdaQueryWrapper<>();
        w.eq(ProxyConfig::getProviderType, providerType);
        return mapper.selectOne(w);
    }

    public ProxyConfig save(ProxyConfig config) {
        if (config.getId() != null) {
            config.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(config);
        } else {
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            mapper.insert(config);
        }
        return config;
    }

    public int delete(String providerType) {
        LambdaQueryWrapper<ProxyConfig> w = new LambdaQueryWrapper<>();
        w.eq(ProxyConfig::getProviderType, providerType);
        return mapper.delete(w);
    }
}
