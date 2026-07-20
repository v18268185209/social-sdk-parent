package cn.net.rjnetwork.xianyu.manager.circuit;

import java.time.LocalDateTime;

/**
 * 熔断器实体（内存 + DB 持久化映射）
 */
public class CircuitBreaker {

    private Long id;
    private Long accountId;
    private String serviceName;
    private String state;                       // CLOSED / OPEN / HALF_OPEN
    private Integer failureCount = 0;
    private Integer successCount = 0;
    private LocalDateTime lastFailureAt;
    private String lastFailureMessage;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime openedAt;
    private LocalDateTime cooldownUntil;
    private Integer thresholdCount = 5;
    private Integer cooldownSeconds = 300;
    private Integer halfOpenMaxSuccess = 3;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isClosed() { return "CLOSED".equals(state); }
    public boolean isOpen() { return "OPEN".equals(state); }
    public boolean isHalfOpen() { return "HALF_OPEN".equals(state); }

    public boolean isInCooldown() {
        return cooldownUntil != null && LocalDateTime.now().isBefore(cooldownUntil);
    }

    public void recordFailure(String message) {
        this.failureCount++;
        this.lastFailureAt = LocalDateTime.now();
        this.lastFailureMessage = message;
        if (this.failureCount >= thresholdCount) {
            this.state = "OPEN";
            this.openedAt = LocalDateTime.now();
            this.cooldownUntil = LocalDateTime.now().plusSeconds(cooldownSeconds);
        }
    }

    public void recordSuccess() {
        this.successCount++;
        this.lastSuccessAt = LocalDateTime.now();
        if ("HALF_OPEN".equals(this.state)) {
            if (this.successCount >= halfOpenMaxSuccess) {
                this.state = "CLOSED";
                this.failureCount = 0;
                this.successCount = 0;
                this.openedAt = null;
                this.cooldownUntil = null;
            }
        } else if ("OPEN".equals(this.state) && !isInCooldown()) {
            this.state = "HALF_OPEN";
            this.successCount = 1;
        }
    }

    public void reset() {
        this.state = "CLOSED";
        this.failureCount = 0;
        this.successCount = 0;
        this.openedAt = null;
        this.cooldownUntil = null;
    }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public LocalDateTime getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(LocalDateTime lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public String getLastFailureMessage() { return lastFailureMessage; }
    public void setLastFailureMessage(String msg) { this.lastFailureMessage = msg; }
    public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(LocalDateTime t) { this.lastSuccessAt = t; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime t) { this.openedAt = t; }
    public LocalDateTime getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(LocalDateTime t) { this.cooldownUntil = t; }
    public Integer getThresholdCount() { return thresholdCount; }
    public void setThresholdCount(Integer n) { this.thresholdCount = n; }
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer s) { this.cooldownSeconds = s; }
    public Integer getHalfOpenMaxSuccess() { return halfOpenMaxSuccess; }
    public void setHalfOpenMaxSuccess(Integer n) { this.halfOpenMaxSuccess = n; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
