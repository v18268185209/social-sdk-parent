package cn.net.rjnetwork.xianyu.manager.common;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一 API 响应结构
 */
@Data
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode("OK");
        response.setMessage("Success");
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("ERROR", message);
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

    @Data
    public static class PageResponse<T> {
        private List<T> records;
        private long total;
        private long current;
        private long size;
    }
}
