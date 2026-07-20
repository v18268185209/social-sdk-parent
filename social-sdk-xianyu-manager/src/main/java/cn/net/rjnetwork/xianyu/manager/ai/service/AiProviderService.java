package cn.net.rjnetwork.xianyu.manager.ai.service;

import cn.net.rjnetwork.core.ai.client.OpenAiCompatibleClient;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderRequest;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiProviderMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AiProviderService {

    private final AiProviderMapper providerMapper;

    public AiProviderService(AiProviderMapper providerMapper) {
        this.providerMapper = providerMapper;
    }

    public Page<AiProvider> listPage(int pageNum, int pageSize, String keyword) {
        Page<AiProvider> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AiProvider::getName, keyword);
        }
        wrapper.orderByDesc(AiProvider::getUpdatedAt);
        providerMapper.selectPage(page, wrapper);
        return page;
    }

    public AiProvider getById(Long id) {
        return providerMapper.selectById(id);
    }

    @Transactional
    public AiProvider create(AiProviderRequest request) {
        AiProvider provider = new AiProvider();
        provider.setName(request.getName());
        provider.setApiBaseUrl(request.getApiBaseUrl());
        provider.setApiKey(request.getApiKey());
        provider.setProviderType(request.getProviderType() != null ? request.getProviderType() : "OPENAI_COMPATIBLE");
        provider.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        provider.setRemark(request.getRemark());
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        providerMapper.insert(provider);
        return provider;
    }

    @Transactional
    public AiProvider update(AiProviderUpdateRequest request) {
        AiProvider provider = providerMapper.selectById(request.getId());
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + request.getId());
        }
        if (request.getName() != null) provider.setName(request.getName());
        if (request.getApiBaseUrl() != null) provider.setApiBaseUrl(request.getApiBaseUrl());
        if (request.getApiKey() != null) provider.setApiKey(request.getApiKey());
        if (request.getProviderType() != null) provider.setProviderType(request.getProviderType());
        if (request.getEnabled() != null) provider.setEnabled(request.getEnabled());
        if (request.getRemark() != null) provider.setRemark(request.getRemark());
        provider.setUpdatedAt(LocalDateTime.now());
        providerMapper.updateById(provider);
        return provider;
    }

    @Transactional
    public void delete(Long id) {
        providerMapper.deleteById(id);
    }

    /**
     * 按 OpenAI 标准规范从远端厂商拉取可用模型列表
     * 调用 {apiBaseUrl}/models，需厂商的 apiKey 鉴权
     */
    public List<Map<String, Object>> listRemoteModels(Long providerId) {
        AiProvider provider = getById(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(provider.getApiBaseUrl(), provider.getApiKey());
        return client.listModels();
    }
}
