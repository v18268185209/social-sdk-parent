package cn.net.rjnetwork.xianyu.manager.virtual.dto;

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

/**
 * 卡密池查询请求
 */
@Data
public class CardPoolQueryRequest {
    private Long productId;
    private String status; // AVAILABLE / USED / EXPIRED
}

/**
 * 自动发货配置创建请求
 */
@Data
public class VirtualShipConfigRequest {
    private Long accountId;
    private Boolean enabled;
    private Integer delaySeconds;
    private Integer autoConfirmDays;
    private Boolean notifyAfterShip;
}

/**
 * 自动发货配置更新请求
 */
@Data
public class VirtualShipConfigUpdateRequest {
    private Long id;
    private Boolean enabled;
    private Integer delaySeconds;
    private Integer autoConfirmDays;
    private Boolean notifyAfterShip;
}

/**
 * 发货任务重试请求
 */
@Data
public class ShipTaskRetryRequest {
    private Long taskId;
}
