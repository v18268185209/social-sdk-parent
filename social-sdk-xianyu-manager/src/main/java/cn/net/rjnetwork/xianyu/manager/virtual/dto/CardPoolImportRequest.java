^package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;
import java.util.List;

/**
 * 卡密批量导入请求
 */
@Data
public class CardPoolImportRequest {
    private Long productId;
    /** 卡密列表，每行一个，格式：卡号 或 卡号|密码 */
    private List<String> cards;
}
