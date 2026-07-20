package cn.net.rjnetwork.xianyu.manager.message.dto;

import lombok.Data;

@Data
public class MessageSendRequest {
    private Long accountId;
    private String sessionId;
    private String content;
}
