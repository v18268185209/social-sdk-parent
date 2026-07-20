package cn.net.rjnetwork.starter.platform.xianyu.dto;

public class MessageSendRequest {

    private Long accountId;
    private String sessionId;
    private String content;
    private String receiverId;
    /** 发送方 userId（ACC 消息链路必填：actualReceivers 必须是 self + peer） */
    private String selfUserId;
    /** 接收方 userId（与 receiverId 语义分离，ACC 链路里 peer 用户的 userId） */
    private String peerUserId;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getSelfUserId() {
        return selfUserId;
    }

    public void setSelfUserId(String selfUserId) {
        this.selfUserId = selfUserId;
    }

    public String getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(String peerUserId) {
        this.peerUserId = peerUserId;
    }
}
