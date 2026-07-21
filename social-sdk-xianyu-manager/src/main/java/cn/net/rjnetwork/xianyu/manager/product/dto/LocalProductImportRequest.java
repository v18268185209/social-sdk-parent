package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class LocalProductImportRequest {
    /** CSV 文件 */
    private MultipartFile file;

    /** 是否去重（按标题） */
    private boolean deduplicate = false;

    /** 重复时是否覆盖（false=跳过） */
    private boolean overwriteDuplicate = false;

    /** Base64 图片存储目录 */
    private String imageStoragePath = "/uploads/local-products";

    /** 虚拟发货内容分隔符 */
    private String deliverContentSeparator = "\\|\\|\\|";

    /** 默认商品类型（CSV 未指定时） */
    private String defaultGoodsType = "PHYSICAL";
}
