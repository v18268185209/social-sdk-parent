package cn.net.rjnetwork.xianyu.manager.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一 API 响应结构
 */
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode("OK");
        response.setMessage("Success");
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    /** 兼容旧调用方：success 等价于 ok。 */
    public static <T> ApiResponse<T> success(T data) {
        return ok(data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public static <T> ApiResponse<T> badRequest(String message) {
        return fail("BAD_REQUEST", message);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("ERROR", message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return fail("ERROR", message);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return fail(code, message);
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> page(List<T> list, long total, long current, long size) {
        PageResponse<T> page = new PageResponse<>();
        page.setRecords(list);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        return (ApiResponse<T>) ok(page);
    }

    public static class PageResponse<T> {
        private List<T> records;
        private long total;
        private long current;
        private long size;

        public List<T> getRecords() { return records; }
        public void setRecords(List<T> records) { this.records = records; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public long getCurrent() { return current; }
        public void setCurrent(long current) { this.current = current; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }
}
