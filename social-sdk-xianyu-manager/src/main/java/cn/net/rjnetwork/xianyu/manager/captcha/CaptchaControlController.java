^package cn.net.rjnetwork.xianyu.manager.captcha;

import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 URL 的 CDP 人工滑块控制页。
 * <p>用户无需远程桌面；打开本页即可看到 CDP Chrome 当前页面截图，并把鼠标事件转发到 CDP。</p>
 */
@Controller
@RequestMapping("/captcha-control")
public class CaptchaControlController {

    private final XianyuCaptchaSolver captchaSolver;
    private final AccountMapper accountMapper;

    public CaptchaControlController(XianyuCaptchaSolver captchaSolver, AccountMapper accountMapper) {
        this.captchaSolver = captchaSolver;
        this.accountMapper = accountMapper;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page(@RequestParam(required = false) Long accountId) {
        String aid = accountId == null ? "" : String.valueOf(accountId);
        return """
                <!doctype html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />
                  <title>闲鱼滑块人工控制</title>
                  <style>
                    body{margin:0;background:#111;color:#eee;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Arial,sans-serif}
                    .bar{position:sticky;top:0;background:#1f1f1f;padding:10px 12px;z-index:2;box-shadow:0 1px 8px #0008}
                    .bar button{margin-right:8px;padding:8px 12px;border:0;border-radius:6px;background:#1677ff;color:white;cursor:pointer}
                    .bar button.warn{background:#fa8c16}.bar button.ok{background:#52c41a}.bar span{margin-left:8px;color:#aaa}
                    .hint{padding:10px 12px;color:#ddd;background:#222;border-bottom:1px solid #333;line-height:1.6}
                    #screenWrap{padding:12px;overflow:auto;text-align:center}
                    #screen{max-width:100%;height:auto;border:1px solid #333;background:#000;cursor:crosshair;touch-action:none;user-select:none}
                    pre{white-space:pre-wrap;background:#191919;padding:10px;margin:12px;border-radius:6px;color:#bfbfbf;text-align:left}
                  </style>
                </head>
                <body>
                  <div class=\"bar\">
                    <button onclick=\"refreshShot()\">刷新截图</button>
                    <button class=\"ok\" onclick=\"checkCookie()\">检查并保存 Cookie</button>
                    <button class=\"warn\" onclick=\"toggleAuto()\" id=\"autoBtn\">自动刷新：开</button>
                    <span id=\"status\">准备中...</span>
                  </div>
                  <div class=\"hint\">
                    本页是 CDP Chrome 的网页代理控制台，不是普通浏览器登录页。请在下方截图上直接拖动滑块；鼠标事件会转发到已登录的远程 Chrome。<br>
                    如果看到登录页，请也在这里完成登录；完成后点“检查并保存 Cookie”。
                  </div>
                  <div id=\"screenWrap\"><img id=\"screen\" draggable=\"false\" /></div>
                  <pre id=\"debug\"></pre>
                  <script>
                    const accountId = '__ACCOUNT_ID__';
                    let naturalW=0,naturalH=0,mouseDown=false,auto=true,timer=null,lastSend=0;
                    const img=document.getElementById('screen'), st=document.getElementById('status'), dbg=document.getElementById('debug');
                    function setStatus(s){st.textContent=s}
                    function mapXY(e){
                      const r=img.getBoundingClientRect();
                      const cx=(e.touches?e.touches[0].clientX:e.clientX)-r.left;
                      const cy=(e.touches?e.touches[0].clientY:e.clientY)-r.top;
                      return {x:Math.max(0,Math.min(naturalW,cx*naturalW/r.width)),y:Math.max(0,Math.min(naturalH,cy*naturalH/r.height))};
                    }
                    async function refreshShot(){
                      try{
                        const q=accountId?('?accountId='+encodeURIComponent(accountId)):'';
                        const r=await fetch('/captcha-control/snapshot'+q,{cache:'no-store'}); const j=await r.json();
                        if(!j.success){setStatus('截图失败：'+j.message); return}
                        const d=j.data; naturalW=d.width||0; naturalH=d.height||0;
                        img.src='data:'+d.imageType+';base64,'+d.image;
                        setStatus((d.title||'')+' | '+(d.url||'')+' | cookieUsable='+d.cookieUsable);
                        dbg.textContent=JSON.stringify({url:d.url,title:d.title,width:d.width,height:d.height,cookieUsable:d.cookieUsable},null,2);
                      }catch(e){setStatus('截图异常：'+e)}
                    }
                    async function sendMouse(type,e){
                      if(!naturalW||!naturalH)return;
                      if(type==='move' && Date.now()-lastSend<25)return; lastSend=Date.now();
                      const p=mapXY(e);
                      const q=accountId?('?accountId='+encodeURIComponent(accountId)):'';
                      await fetch('/captcha-control/mouse'+q,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({type,x:p.x,y:p.y})}).catch(()=>{});
                    }
                    img.addEventListener('mousedown',e=>{mouseDown=true;sendMouse('down',e);e.preventDefault()});
                    window.addEventListener('mousemove',e=>{if(mouseDown)sendMouse('move',e)});
                    window.addEventListener('mouseup',e=>{if(mouseDown){mouseDown=false;sendMouse('up',e);setTimeout(refreshShot,800)}});
                    img.addEventListener('touchstart',e=>{mouseDown=true;sendMouse('down',e);e.preventDefault()},{passive:false});
                    img.addEventListener('touchmove',e=>{if(mouseDown)sendMouse('move',e);e.preventDefault()},{passive:false});
                    img.addEventListener('touchend',e=>{if(mouseDown){mouseDown=false;sendMouse('up',e);setTimeout(refreshShot,800)}});
                    async function checkCookie(){
                      const q=accountId?('?accountId='+encodeURIComponent(accountId)):'';
                      const r=await fetch('/captcha-control/check-cookie'+q,{method:'POST'}); const j=await r.json();
                      setStatus(j.success?'Cookie 可用，已保存/可继续同步':'Cookie 不可用：'+j.message);
                      dbg.textContent=JSON.stringify(j,null,2);
                    }
                    function toggleAuto(){auto=!auto;document.getElementById('autoBtn').textContent='自动刷新：'+(auto?'开':'关')}
                    timer=setInterval(()=>{if(auto&&!mouseDown)refreshShot()},1500);
                    refreshShot();
                  </script>
                </body></html>
                """.replace("__ACCOUNT_ID__", aid);
    }

    @GetMapping("/snapshot")
    @ResponseBody
    public ApiResponse<Map<String, Object>> snapshot(@RequestParam(required = false) Long accountId) {
        try {
            return ApiResponse.ok(captchaSolver.getControlSnapshot());
        } catch (Exception e) {
            return ApiResponse.fail("SNAPSHOT_FAILED", e.getMessage());
        }
    }

    @PostMapping("/mouse")
    @ResponseBody
    public ApiResponse<String> mouse(@RequestBody MouseEvent event) {
        try {
            captchaSolver.dispatchControlMouse(event.type, event.x, event.y);
            return ApiResponse.ok("OK");
        } catch (Exception e) {
            return ApiResponse.fail("MOUSE_FAILED", e.getMessage());
        }
    }

    @PostMapping("/check-cookie")
    @ResponseBody
    public ApiResponse<Map<String, Object>> checkCookie(@RequestParam(required = false) Long accountId) {
        try {
            String cookie = captchaSolver.extractCurrentImCookie();
            boolean usable = cookie != null && (cookie.toLowerCase().contains("x5sec")
                    || (cookie.toLowerCase().contains("_m_h5_tk") && (cookie.toLowerCase().contains("unb") || cookie.toLowerCase().contains("cookie2") || cookie.toLowerCase().contains("sgcookie"))));
            if (usable && accountId != null) {
                XianyuAccount acc = accountMapper.selectById(accountId);
                if (acc != null) {
                    acc.setImCookieHeader(cookie);
                    accountMapper.updateById(acc);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("usable", usable);
            data.put("saved", usable && accountId != null);
            data.put("cookieLength", cookie == null ? 0 : cookie.length());
            data.put("cookiePreview", preview(cookie));
            if (!usable) return ApiResponse.fail("COOKIE_NOT_USABLE", "当前 CDP 浏览器尚未产生可用 IM Cookie");
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("COOKIE_CHECK_FAILED", e.getMessage());
        }
    }

    private String preview(String s) {
        if (s == null) return "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return s.length() > 240 ? s.substring(0, 240) + "..." : s;
    }

    public static class MouseEvent {
        public String type;
        public double x;
        public double y;
    }
}
