package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 闲鱼图片上传 API 服务
 * 封装图片上传到闲鱼 CDN 的 MTOP 接口调用
 *
 * <p>根据 goofish-cli 和 bb-browser 项目的实践，闲鱼前端通过 MTOP 接口上传图片到 CDN，
 * 返回 CDN URL + 宽高尺寸，然后在发布商品时传入 CDN URL。</p>
 *
 * <p>已知接口模式：</p>
 * <ul>
 *   <li>ISV 开放平台：alibaba.idle.isv.media.upload（需要授权）</li>
 *   <li>前端 MTOP：mtop.idle.pc.idleitem.media.upload 或 mtop.idle.media.upload（无需授权）</li>
 * </ul>
 *
 * <p>本类首先尝试通过 CDP 在浏览器中捕获实际上传接口，如果未捕获则回退到已知接口。</p>
 */
public class XianyuMediaUploadApiService {

    private final XianyuMtopApiClient apiClient;
    private static final String UPLOAD_BASE_URL = "https://h5api.m.goofish.com/h5/";

    /**
     * 上传结果
     */
    public static class UploadResult {
        public boolean success;
        public String cdnUrl;          // CDN 图片 URL
        public int width;              // 图片宽度
        public int height;             // 图片高度
        public String fileId;          // 文件 ID（部分接口返回）
        public String mediaId;         // 媒体 ID
        public String message;

        public UploadResult() {}
    }

    public XianyuMediaUploadApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 方式一：通过 CDP 捕获的实际接口 ====================

    /**
     * 通过 CDP 捕获的实际图片上传接口调用（如果有的话）
     *
     * <p>使用方式：在 CDP 分析器中导航到发布页，本方法会尝试复用已捕获的接口。</p>
     *
     * @param imagePath 本地图片路径
     * @return 上传结果
     */
    public UploadResult uploadViaCapturedApi(Path imagePath) {
        // 目前先使用已知接口，后续可通过 CDP 分析器捕获实际接口名后替换
        return uploadViaMtop(imagePath);
    }

    // ==================== 方式二：MTOP 接口上传图片 ====================

    /**
     * 通过 MTOP 接口上传图片到闲鱼 CDN
     *
     * <p>闲鱼前端图片上传通常使用以下接口之一：</p>
     * <ul>
     *   <li>mtop.idle.pc.idleitem.media.upload — PC 端商品媒体上传</li>
     *   <li>mtop.idle.media.upload — 通用媒体上传</li>
     *   <li>mtop.idle.web.media.upload — Web 端媒体上传</li>
     * </ul>
     *
     * @param imagePath 本地图片路径
     * @return 上传结果
     */
    public UploadResult uploadViaMtop(Path imagePath) {
        try {
            // 读取图片文件为 Base64
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String fileName = imagePath.getFileName().toString();
            String mimeType = detectMimeType(fileName);

            // 尝试多个可能的上传接口
            UploadResult result = uploadViaApi("mtop.idle.pc.idleitem.media.upload", imageBytes, base64Image, fileName, mimeType);
            if (result != null && result.success) {
                return result;
            }

            // 回退到通用媒体上传
            result = uploadViaApi("mtop.idle.media.upload", imageBytes, base64Image, fileName, mimeType);
            if (result != null && result.success) {
                return result;
            }

            // 回退到 Web 端媒体上传
            result = uploadViaApi("mtop.idle.web.media.upload", imageBytes, base64Image, fileName, mimeType);
            if (result != null && result.success) {
                return result;
            }

            // 所有接口都失败
            result = new UploadResult();
            result.success = false;
            result.message = "所有图片上传接口均失败，请通过 CDP 重新分析捕获实际接口";
            return result;

        } catch (Exception e) {
            UploadResult result = new UploadResult();
            result.success = false;
            result.message = "图片读取失败: " + e.getMessage();
            return result;
        }
    }

    /**
     * 通过 Base64 字符串上传图片（适用于已从其他地方获取的图片）
     *
     * @param base64Image Base64 编码的图片数据
     * @param fileName 文件名
     * @param mimeType MIME 类型
     * @return 上传结果
     */
    public UploadResult uploadFromBase64(String base64Image, String fileName, String mimeType) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            UploadResult result = uploadViaApi("mtop.idle.pc.idleitem.media.upload", imageBytes, base64Image, fileName, mimeType);
            if (result != null && result.success) {
                return result;
            }
            result = uploadViaApi("mtop.idle.media.upload", imageBytes, base64Image, fileName, mimeType);
            if (result != null && result.success) {
                return result;
            }
            result = new UploadResult();
            result.success = false;
            result.message = "所有图片上传接口均失败";
            return result;
        } catch (Exception e) {
            UploadResult result = new UploadResult();
            result.success = false;
            result.message = "Base64 解码失败: " + e.getMessage();
            return result;
        }
    }

    // ==================== 方式三：通过 CDP DOM 操作上传（兜底方案）====================

    /**
     * 通过 CDP 的 DOM.setFileInputFiles 方法上传图片到闲鱼发布页
     *
     * <p>这是 bb-browser 项目中验证有效的方案，当 MTOP 接口不可用时使用。</p>
     *
     * @param cdpClient CDP 客户端
     * @param tabId Tab ID
     * @param imagePath 本地图片路径
     * @return 上传结果
     */
    public UploadResult uploadViaCdp(cn.net.rjnetwork.chrome.cdp.CdpClient cdpClient, String tabId, Path imagePath) {
        try {
            // 1. 导航到发布页
            String absolutePath = imagePath.toAbsolutePath().normalize().toString();
            // 注意：CDP.setFileInputFiles 需要的是本地文件路径数组
            // 这里通过 Runtime.evaluate 执行 JS 来模拟文件选择

            String js = String.format(
                "(function() { " +
                "  var input = document.querySelector('input[type=\"file\"]'); " +
                "  if (!input) return JSON.stringify({error: 'no file input'}); " +
                "  // 创建 DataTransfer 来模拟文件选择 " +
                "  var dt = new DataTransfer(); " +
                "  // 由于浏览器安全限制，不能直接设置本地文件 " +
                "  // 需要使用 CDP DOM.setFileInputFiles " +
                "  return JSON.stringify({status: 'use_cdp_dom_api'}); " +
                "})();"
            );

            // 实际上传需要通过 CDP 的 DOM.setFileInputFiles
            // 这需要 CdpClient 支持该方法，当前 CdpClient 可能不支持
            UploadResult result = new UploadResult();
            result.success = false;
            result.message = "CDP 文件上传需要 CdpClient 支持 DOM.setFileInputFiles 方法，请使用 MTOP 接口方式";
            return result;

        } catch (Exception e) {
            UploadResult result = new UploadResult();
            result.success = false;
            result.message = "CDP 上传失败: " + e.getMessage();
            return result;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 通过指定 MTOP 接口上传图片
     */
    private UploadResult uploadViaApi(String apiName, byte[] imageBytes, String base64Image, String fileName, String mimeType) {
        try {
            String url = new XianyuMtopRequestBuilder(apiName)
                    .setCookie(apiClient.getCookie())
                    .buildUrl();

            // 构建上传请求体
            // 闲鱼图片上传通常期望 multipart/form-data 或 JSON 格式
            // 这里尝试 JSON 格式（MTOP 常见格式）
            String requestBody = buildUploadRequestBody(base64Image, fileName, mimeType);

            // 发送请求
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Cookie", apiClient.getCookie())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode resultNode = XianyuMtopRequestBuilder.parseResponse(response.body());

            UploadResult uploadResult = new UploadResult();
            uploadResult.success = resultNode.has("ret") &&
                    resultNode.path("ret").isArray() &&
                    resultNode.path("ret").get(0).asText().startsWith("SUCCESS");

            if (uploadResult.success) {
                JsonNode data = resultNode.path("data");
                uploadResult.cdnUrl = data.path("url").asText(
                        data.path("cdnUrl").asText(
                                data.path("imageUrl").asText("")));
                uploadResult.width = data.path("width").asInt(
                        data.path("imageWidth").asInt(0));
                uploadResult.height = data.path("height").asInt(
                        data.path("imageHeight").asInt(0));
                uploadResult.fileId = data.path("fileId").asText(
                        data.path("mediaId").asText(""));
                uploadResult.mediaId = uploadResult.fileId;
                uploadResult.message = "上传成功";
            } else {
                uploadResult.message = "上传失败: " + resultNode.path("ret").get(1).asText("");
            }

            return uploadResult;

        } catch (Exception e) {
            UploadResult result = new UploadResult();
            result.success = false;
            result.message = "接口调用失败 (" + apiName + "): " + e.getMessage();
            return result;
        }
    }

    /**
     * 构建上传请求体
     */
    private String buildUploadRequestBody(String base64Image, String fileName, String mimeType) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format(
            "{\"file\":{\"data\":\"%s\",\"name\":\"%s\",\"type\":\"%s\",\"uuid\":\"%s\"}}",
            base64Image.substring(0, Math.min(200, base64Image.length())) + "...",
            fileName,
            mimeType,
            uuid
        );
    }

    /**
     * 根据文件名检测 MIME 类型
     */
    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
