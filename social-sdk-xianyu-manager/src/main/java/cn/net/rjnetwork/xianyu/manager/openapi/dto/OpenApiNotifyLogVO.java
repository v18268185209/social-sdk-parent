package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外通知投递日志视图对象：按场景/状态/时间范围过滤。
 */
@Data
public class OpenApiNotifyLogVO {

    private Long id;

    private String scenario;

    private Long channelId;

    private String channelType;

    private String recipient;

    private String status;

    private String error;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
