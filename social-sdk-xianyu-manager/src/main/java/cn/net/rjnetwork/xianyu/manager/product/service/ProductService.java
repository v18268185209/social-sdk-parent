package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public Page<XianyuProduct> listPage(int pageNum, int pageSize, Long accountId, String keyword, String status) {
        Page<XianyuProduct> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuProduct> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuProduct::getAccountId, accountId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(XianyuProduct::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(XianyuProduct::getStatus, status);
        }
        wrapper.orderByDesc(XianyuProduct::getUpdatedAt);
        productMapper.selectPage(page, wrapper);
        return page;
    }

    public XianyuProduct getById(Long id) {
        return productMapper.selectById(id);
    }

    @Transactional
    public XianyuProduct create(ProductCreateRequest request) {
        XianyuProduct product = new XianyuProduct();
        product.setAccountId(request.getAccountId());
        product.setTitle(request.getTitle());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setCategoryId(request.getCategoryId());
        product.setImages(request.getImages());
        product.setDescription(request.getDescription());
        product.setStatus("DRAFT");
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        return product;
    }

    @Transactional
    public XianyuProduct update(ProductUpdateRequest request) {
        XianyuProduct product = productMapper.selectById(request.getId());
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + request.getId());
        }
        if (request.getTitle() != null) product.setTitle(request.getTitle());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getOriginalPrice() != null) product.setOriginalPrice(request.getOriginalPrice());
        if (request.getStock() != 0) product.setStock(request.getStock());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getImages() != null) product.setImages(request.getImages());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        productMapper.deleteById(id);
    }

    @Transactional
    public XianyuProduct shelfOn(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setStatus("ON_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct shelfOff(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setStatus("OFF_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct updatePrice(Long id, java.math.BigDecimal price) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setPrice(price);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct updateStock(Long id, Integer stock) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setStock(stock);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    public List<XianyuProduct> listByAccountId(Long accountId) {
        LambdaQueryWrapper<XianyuProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuProduct::getAccountId, accountId);
        wrapper.orderByDesc(XianyuProduct::getUpdatedAt);
        return productMapper.selectList(wrapper);
    }
}
