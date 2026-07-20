package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 卡密池查询请求
 */
@Data
public class CardPoolQueryRequest {
    private Long productId;
    private String status; // AVAILABLE / USED / EXPIRED
}
