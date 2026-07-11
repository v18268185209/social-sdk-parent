package cn.net.rjnetwork.demo.model;

public class QrAuthenticateRequest {

    private String qrSessionId;
    private Boolean allowManualLogin;

    public String getQrSessionId() {
        return qrSessionId;
    }

    public void setQrSessionId(String qrSessionId) {
        this.qrSessionId = qrSessionId;
    }

    public Boolean getAllowManualLogin() {
        return allowManualLogin;
    }

    public void setAllowManualLogin(Boolean allowManualLogin) {
        this.allowManualLogin = allowManualLogin;
    }
}
