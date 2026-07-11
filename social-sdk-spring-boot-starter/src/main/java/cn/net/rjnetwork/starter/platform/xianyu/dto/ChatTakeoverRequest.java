package cn.net.rjnetwork.starter.platform.xianyu.dto;

public class ChatTakeoverRequest {

    private Long accountId;
    private Boolean autoReply;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Boolean getAutoReply() {
        return autoReply;
    }

    public void setAutoReply(Boolean autoReply) {
        this.autoReply = autoReply;
    }
}
