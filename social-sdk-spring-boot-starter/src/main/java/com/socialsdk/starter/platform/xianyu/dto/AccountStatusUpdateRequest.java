package com.socialsdk.starter.platform.xianyu.dto;

public class AccountStatusUpdateRequest {

    private String status;
    private String lastError;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
