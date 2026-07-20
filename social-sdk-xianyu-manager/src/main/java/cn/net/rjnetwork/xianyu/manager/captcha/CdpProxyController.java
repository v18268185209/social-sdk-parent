package cn.net.rjnetwork.xianyu.manager.captcha;

import cn.net.rjnetwork.xianyu.captcha.config.CdpCaptchaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * CDP HTTP 代理入口。
 * <p>用于生成可公网/内网访问的 DevTools 页面：用户打开 manager 后端地址即可控制远端 CDP Chrome，
 * 不需要远程桌面，也不要求用户能直接访问 192.168.1.127:9333。</p>
 */
@Controller
@RequestMapping("/cdp-proxy")
public class CdpProxyController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CdpCaptchaConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    public CdpProxyController(CdpCaptchaConfig config) {
        this.config = config;
    }

    /**
     * 打开 CDP DevTools 控制页，默认定位到闲鱼消息页 target。
     */
    @GetMapping("/open")
    public RedirectView open(HttpServletRequest request,
                             @RequestParam(required = false) Long accountId,
                             @RequestParam(defaultValue = "true") boolean im) throws Exception {
        JsonNode target = findOrCreateTarget(im ? "https://www.goofish.com/im" : "about:blank");
        String wsPath = target.path("webSocketDebuggerUrl").asText("");
        String pageId = target.path("id").asText("");
        if (pageId.isBlank() || wsPath.isBlank()) {
            throw new IllegalStateException("无法获取 CDP 页面 target/webSocketDebuggerUrl");
        }

        String wsProxy = externalHost(request) + "/cdp-ws/devtools/page/" + pageId;
        String frontend = target.path("devtoolsFrontendUrl").asText("");
        String redirect;
        if (frontend != null && !frontend.isBlank()) {
            redirect = frontend;
            if (redirect.startsWith("/")) {
                redirect = "https://chrome-devtools-frontend.appspot.com" + redirect;
            }
            redirect = redirect.replaceFirst("ws=[^&]+", "ws=" + URLEncoder.encode(wsProxy, StandardCharsets.UTF_8));
        } else {
            redirect = "https://chrome-devtools-frontend.appspot.com/serve_rev/@latest/inspector.html?ws="
                    + URLEncoder.encode(wsProxy, StandardCharsets.UTF_8);
        }
        return new RedirectView(redirect);
    }

    /** 代理 CDP /json/version、/json/list 等 HTTP 接口，便于诊断。 */
    @RequestMapping(value = {"/json", "/json/**"}, method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.POST})
    @ResponseBody
    public ResponseEntity<byte[]> proxyJson(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI().replaceFirst("^/cdp-proxy", "");
        String query = request.getQueryString();
        String targetUrl = config.getCdpEndpoint() + path + (query != null && !query.isBlank() ? "?" + query : "");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.ALL));
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(URI.create(targetUrl), HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
        HttpHeaders out = new HttpHeaders();
        MediaType ct = resp.getHeaders().getContentType();
        if (ct != null) out.setContentType(ct);
        return new ResponseEntity<>(resp.getBody(), out, resp.getStatusCode());
    }

    private JsonNode findOrCreateTarget(String desiredUrl) throws Exception {
        JsonNode list = restTemplate.getForObject(config.getCdpEndpoint() + "/json/list", JsonNode.class);
        JsonNode fallback = null;
        if (list != null && list.isArray()) {
            for (JsonNode t : list) {
                if (!"page".equals(t.path("type").asText(""))) continue;
                String url = t.path("url").asText("");
                String title = t.path("title").asText("");
                if (url.contains("goofish.com/im")) return t;
                if (fallback == null && (url.contains("goofish") || title.contains("闲鱼") || title.contains("咸鱼"))) {
                    fallback = t;
                }
            }
        }
        if (fallback != null) return fallback;
        String encoded = URLEncoder.encode(desiredUrl, StandardCharsets.UTF_8);
        return restTemplate.exchange(URI.create(config.getCdpEndpoint() + "/json/new?" + encoded), HttpMethod.PUT,
                HttpEntity.EMPTY, JsonNode.class).getBody();
    }

    private String externalHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String host = forwardedHost != null && !forwardedHost.isBlank() ? forwardedHost : request.getHeader("Host");
        String proto = forwardedProto != null && !forwardedProto.isBlank() ? forwardedProto : request.getScheme();
        // DevTools frontend 的 ws 参数不带 scheme：host/path。HTTPS 页面会自动使用 wss，HTTP 页面使用 ws。
        return host;
    }
}
