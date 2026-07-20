^package cn.net.rjnetwork.xianyu.manager.ai.service;

import cn.net.rjnetwork.xianyu.manager.ai.dto.AiModelRequest;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiModelUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiModelMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiModelService {

    private final AiModelMapper modelMapper;

    public AiModelService(AiModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public Page<AiModel> listPage(int pageNum, int pageSize, Long providerId, String modelType, String keyword) {
        Page<AiModel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        if (providerId != null) {
            wrapper.eq(AiModel::getProviderId, providerId);
        }
        if (modelType != null && !modelType.isBlank()) {
            wrapper.eq(AiModel::getModelType, modelType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AiModel::getModelName, keyword);
        }
        wrapper.orderByDesc(AiModel::getUpdatedAt);
        modelMapper.selectPage(page, wrapper);
        return page;
    }

    /**
     * 根据厂商ID查询所有模型（不分页）
     */
    public List<AiModel> listByProvider(Long providerId, String modelType) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getProviderId, providerId);
        if (modelType != null && !modelType.isBlank()) {
            wrapper.eq(AiModel::getModelType, modelType);
        }
        wrapper.orderByAsc(AiModel::getDisplayName);
        return modelMapper.selectList(wrapper);
    }

    public AiModel getById(Long id) {
        return modelMapper.selectById(id);
    }

    @Transactional
    public AiModel create(AiModelRequest request) {
        AiModel model = new AiModel();
        model.setProviderId(request.getProviderId());
        model.setModelName(request.getModelName());
        model.setDisplayName(request.getDisplayName());
        model.setModelType(request.getModelType());
        model.setCapabilities(request.getCapabilities());
        model.setDefaultTemperature(request.getDefaultTemperature() != null ? request.getDefaultTemperature() : 0.7);
        model.setDefaultMaxTokens(request.getDefaultMaxTokens() != null ? request.getDefaultMaxTokens() : 1024);
        model.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        model.setRemark(request.getRemark());
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.insert(model);
        return model;
    }

    @Transactional
    public AiModel update(AiModelUpdateRequest request) {
        AiModel model = modelMapper.selectById(request.getId());
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + request.getId());
        }
        if (request.getProviderId() != null) model.setProviderId(request.getProviderId());
        if (request.getModelName() != null) model.setModelName(request.getModelName());
        if (request.getDisplayName() != null) model.setDisplayName(request.getDisplayName());
        if (request.getModelType() != null) model.setModelType(request.getModelType());
        if (request.getCapabilities() != null) model.setCapabilities(request.getCapabilities());
        if (request.getDefaultTemperature() != null) model.setDefaultTemperature(request.getDefaultTemperature());
        if (request.getDefaultMaxTokens() != null) model.setDefaultMaxTokens(request.getDefaultMaxTokens());
        if (request.getEnabled() != null) model.setEnabled(request.getEnabled());
        if (request.getRemark() != null) model.setRemark(request.getRemark());
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(model);
        return model;
    }

    @Transactional
    public void delete(Long id) {
        modelMapper.deleteById(id);
    }
}
