package cn.net.rjnetwork.starter.platform.xianyu.dto;

public class RuleMatchRequest {

    private Long accountId;
    private String text;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
