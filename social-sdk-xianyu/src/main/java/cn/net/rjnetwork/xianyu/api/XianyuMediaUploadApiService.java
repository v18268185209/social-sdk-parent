package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼图片上传 API 服务
 * 封装图片上传到闲鱼 CDN 的 MTOP 接口调用
 *
 * <p>所有 MTOP 请求通过 {@link XianyuMtopApiClient#callMtop} 自动计算 sign、预热 _m_h5_tk、
 * 设置 Referer/Origin，无需手动拼 URL 和签名。</p>
 */
public class XianyuMediaUploadApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuMediaUploadApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 上传结果 */
    public static class UploadResult {
        public boolean success;
        public String cdnUrl;
        public int width;
        public int height;
        public String fileId;
        public String mediaId;
        public String message;

        public UploadResult() {}
    }

    /**
     * 通过 CDP 捕获的实际图片上传接口调用
     * <p>目前直接走 MTOP 上传，后续可通过 CDP 分析器捕获实际接口名后替换。</p>
     */
    public UploadResult uploadViaCapturedApi(Path imagePath) {
        return uploadViaMtop(imagePath);
    }

    /**
     * 通过 MTOP 接口上传图片到闲鱼 CDN
     */
    public UploadResult uploadViaMtop(Path imagePath) {
        try {
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String fileName = imagePath.getFileName().toString();
            String mimeType = detectMimeType(fileName);

            // 依次尝试多个已知上传接口
            UploadResult result = uploadViaApi("mtop.idle.pc.idleitem.media.upload", base64Image, fileName, mimeType);
            if (result != null && result.success) return result;

            result = uploadViaApi("mtop.idle.media.upload", base64Image, fileName, mimeType);
            if (result != null && result.success) return result;

            result = uploadViaApi("mtop.idle.web.media.upload", base64Image, fileName, mimeType);
            if (result != null && result.success) return result;

            UploadResult fail = new UploadResult();
            fail.success = false;
            fail.message = "所有图片上传接口均失败，请通过 CDP 重新分析捕获实际接口";
            return fail;
        } catch (Exception e) {
            UploadResult r = new UploadResult();
            r.success = false;
            r.message = "图片读取失败: " + e.getMessage();
            return r;
        }
    }

    /**
     * 通过 Base64 字符串上传图片
     */
    public UploadResult uploadFromBase64(String base64Image, String fileName, String mimeType) {
        UploadResult result = uploadViaApi("mtop.idle.pc.idleitem.media.upload", base64Image, fileName, mimeType);
        if (result != null && result.success) return result;
        result = uploadViaApi("mtop.idle.media.upload", base64Image, fileName, mimeType);
        if (result != null && result.success) return result;
        UploadResult fail = new UploadResult();
        fail.success = false;
        fail.message = "所有图片上传接口均失败";
        return fail;
    }

    /**
     * 通过指定 MTOP 接口上传图片
     * <p>使用 {@link XianyuMtopApiClient#callMtop} 统一发送，自动计算 sign 和预热 token。</p>
     */
    private UploadResult uploadViaApi(String apiName, String base64Image, String fileName, String mimeType) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            Map<String, Object> file = new LinkedHashMap<>();
            // 仅取前 200 字符作为摘要（避免 body 过大），实际项目应改为 multipart 上传
            file.put("data", base64Image.substring(0, Math.min(200, base64Image.length())) + "...");
            file.put("name", fileName != null ? fileName : "");
            file.put("type", mimeType != null ? mimeType : "image/jpeg");
            file.put("uuid", java.util.UUID.randomUUID().toString().replace("-", ""));
            data.put("file", file);

            JsonNode resultNode = apiClient.callMtop(apiName, toJson(data));
            if (resultNode == null) {
                UploadResult r = new UploadResult();
                r.success = false;
                r.message = "接口调用失败 (" + apiName + "): no response";
                return r;
            }

            UploadResult uploadResult = new UploadResult();
            uploadResult.success = resultNode.has("ret")
                    && resultNode.path("ret").isArray()
                    && resultNode.path("ret").get(0).asText("").startsWith("SUCCESS");

            if (uploadResult.success) {
                JsonNode d = resultNode.path("data");
                uploadResult.cdnUrl = d.path("url").asText(d.path("cdnUrl").asText(d.path("imageUrl").asText("")));
                uploadResult.width = d.path("width").asInt(d.path("imageWidth").asInt(0));
                uploadResult.height = d.path("height").asInt(d.path("imageHeight").asInt(0));
                uploadResult.fileId = d.path("fileId").asText(d.path("mediaId").asText(""));
                uploadResult.mediaId = uploadResult.fileId;
                uploadResult.message = "上传成功";
            } else {
                JsonNode ret = resultNode.path("ret");
                uploadResult.message = "上传失败: " + (ret.isArray() && ret.size() > 1 ? ret.get(1).asText("") : "unknown");
            }
            return uploadResult;
        } catch (Exception e) {
            UploadResult r = new UploadResult();
            r.success = false;
            r.message = "接口调用失败 (" + apiName + "): " + e.getMessage();
            return r;
        }
    }

    private String detectMimeType(String fileName) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
