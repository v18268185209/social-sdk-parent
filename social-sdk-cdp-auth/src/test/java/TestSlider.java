//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.WebSocket;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class TestSlider {
//    static final String WS = "ws://127.0.0.1:9222/devtools/page/4154830505B819E7FA1F33AC9A7779D7";
//    static final AtomicInteger MID = new AtomicInteger(1);
//    static final ConcurrentLinkedQueue<String> EVENTS = new ConcurrentLinkedQueue<>();
//
//    public static void main(String[] args) throws Exception {
//        HttpClient client = HttpClient.newHttpClient();
//        CompletableFuture<WebSocket> cf = client.newWebSocketBuilder()
//                .header("Origin", "http://127.0.0.1:9222")
//                .buildAsync(URI.create(WS), new WebSocket.Listener() {
//                    @Override public void onText(WebSocket ws, CharSequence data, boolean last) {
//                        EVENTS.offer(data.toString());
//                    }
//                    @Override public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
//                        EVENTS.offer("__CLOSED__");
//                        return null;
//                    }
//                });
//        WebSocket ws = cf.get(10, TimeUnit.SECONDS);
//        System.out.println("[OK] CDP 连接成功");
//
//        // 1. 测量真实尺寸
//        String measure = cmd("Runtime.evaluate", """
//            (() => {
//                const o = {};
//                const btn = document.querySelector('#nc_1_n1z') || document.querySelector('.nc_iconfont');
//                const ts = [['#nc_1_n1t','#nc_1_n1t'],['.nc_scale','.nc_scale'],['#nc_1_wrapper','#nc_1_wrapper'],['#nc_1_nocaptcha','#nc_1_nocaptcha'],['#nocaptcha','#nocaptcha'],['.nc-container','.nc-container']];
//                let track=null,tn='';
//                for (const [s,n] of ts){const e=document.querySelector(s);if(e){track=e;tn=n;break;}}
//                if (btn){const r=btn.getBoundingClientRect();o.btn={w:+r.width.toFixed(1),h:+r.height.toFixed(1)};o.cx=+(r.left+r.width/2).toFixed(2);o.cy=+(r.top+r.height/2).toFixed(2);}
//                if (track){const r=track.getBoundingClientRect();o.track={w:+r.width.toFixed(1),h:+r.height.toFixed(1),l:+r.left.toFixed(1),rr:+r.right.toFixed(1)};o.trackSel=tn;}
//                o.all={};
//                for (const s of ['#nc_1_n1z','#nc_1_n1t','.nc_scale','#nc_1_wrapper','#nc_1_nocaptcha','#nocaptcha','.nc-container']){const e=document.querySelector(s);if(e){const r=e.getBoundingClientRect();o.all[s]={w:+r.width.toFixed(1),h:+r.height.toFixed(1)};}}
//                return JSON.stringify(o);
//            })()
//            """.replace("\n", " ").trim(), true);
//        System.out.println("测量结果: " + measure);
//
//        // 解析
//        double cx = extractNum(measure, "\"cx\":");
//        double cy = extractNum(measure, "\"cy\":");
//        double trackW = extractNum(measure, "\"track\":\\{\"w\":", true);
//        double btnW = extractNum(measure, "\"btn\":\\{\"w\":", true);
//        double slideDistance = trackW - btnW;
//        String trackSel = extractStr(measure, "\"trackSel\":\"");
//
//        System.out.printf("滑块中心: cx=%s cy=%s%n", cx, cy);
//        System.out.printf("轨道选择器: %s, 轨道宽=%s, 滑块宽=%s, 轨道距离=%s%n",
//                trackSel, trackW, btnW, slideDistance);
//
//        // 2. 朴素拖动（直接到轨道终点）
//        System.out.println("\n=== 朴素拖动（不越界）===");
//        mouseEvent(ws, "mousePressed", cx, cy, "left", 1);
//        Thread.sleep(100);
//        double targetX = cx + slideDistance;
//        mouseEvent(ws, "mouseMoved", targetX, cy, "left", 1);
//        Thread.sleep(200);
//        mouseEvent(ws, "mouseReleased", targetX, cy, "left", 0);
//        Thread.sleep(2000);
//
//        String state = eval(ws, "JSON.stringify({href:location.href,hasSlider:!!document.querySelector('#nc_1_n1z'),cookie:document.cookie})");
//        System.out.println("朴素拖动结果: " + state);
//
//        boolean failed = state.contains("\"hasSlider\":true") || state.contains("punish");
//        if (failed) {
//            System.out.println("\n=== 朴素拖动失败，刷新 + 越界拖动 ===");
//            eval(ws, "(() => { const b=document.querySelector('.nc_btn_refresh, .refresh-btn'); if(b) b.click(); })()");
//            Thread.sleep(2000);
//
//            double overshoot = slideDistance * (0.45 + Math.random() * 0.1);
//            double cursorDistance = slideDistance + overshoot;
//            double tx2 = cx + cursorDistance;
//            System.out.printf("越界距离: %.1f (%.1f%%), 释放x=%.1f%n", cursorDistance, overshoot/slideDistance*100, tx2);
//
//            mouseEvent(ws, "mousePressed", cx, cy, "left", 1);
//            Thread.sleep(100);
//            // 分10段拖动
//            for (int i = 1; i <= 10; i++) {
//                double px = cx + cursorDistance * i / 10;
//                double py = cy + (Math.random()-0.5)*2;
//                mouseEvent(ws, "mouseMoved", px, py, "left", 1);
//                Thread.sleep(20);
//            }
//            Thread.sleep(100);
//            mouseEvent(ws, "mouseReleased", tx2, cy, "left", 0);
//            Thread.sleep(2000);
//
//            String st2 = eval(ws, "JSON.stringify({href:location.href,hasSlider:!!document.querySelector('#nc_1_n1z'),cookie:document.cookie})");
//            System.out.println("越界拖动结果: " + st2);
//        }
//
//        ws.sendClose(1000, "done");
//        System.out.println("\n[完成]");
//    }
//
//    static void mouseEvent(WebSocket ws, String type, double x, double y, String button, int buttons) {
//        String params = String.format("{\"x\":%.1f,\"y\":%.1f,\"type\":\"%s\",\"button\":\"%s\",\"buttons\":%d}", x, y, type, button, buttons);
//        fire(ws, "Input.dispatchMouseEvent", params);
//    }
//
//    static String eval(WebSocket ws, String expr) {
//        return cmd("Runtime.evaluate", expr, true);
//    }
//
//    static String fire(WebSocket ws, String method, String paramsJson) {
//        String json = String.format("{\"id\":%d,\"method\":\"%s\",\"params\":%s}", MID.getAndIncrement(), method, paramsJson);
//        ws.sendText(json, true);
//        EVENTS.clear();
//        long end = System.currentTimeMillis() + 5000;
//        while (System.currentTimeMillis() < end) {
//            String e = EVENTS.poll();
//            if (e == null) { Thread.yield(); continue; }
//            if (e.equals("__CLOSED__")) return null;
//            int idx = e.indexOf("\"result\":");
//            if (idx > 0) {
//                int rStart = e.indexOf('{', idx + 8);
//                if (rStart > 0) {
//                    int depth = 0, rEnd = rStart;
//                    for (int i = rStart; i < e.length(); i++) {
//                        char c = e.charAt(i);
//                        if (c == '{') depth++;
//                        else if (c == '}') { depth--; if (depth == 0) { rEnd = i + 1; break; } }
//                    }
//                    String result = e.substring(rStart, rEnd);
//                    if (result.contains("\"value\"") || result.contains("\"type\"")) return result;
//                }
//            }
//        }
//        return null;
//    }
//
//    static String cmd(WebSocket ws, String method, String expr, boolean returnByValue) {
//        String params = String.format("{\"expression\":\"%s\",\"returnByValue\":%s}",
//                expr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "  "), returnByValue);
//        return fire(ws, method, params);
//    }
//
//    static double extractNum(String json, String key) { return extractNum(json, key, false); }
//    static double extractNum(String json, String key, boolean regex) {
//        try {
//            int idx;
//            if (regex) {
//                java.util.regex.Matcher m = java.util.regex.Pattern.compile(key).matcher(json);
//                if (!m.find()) return 0;
//                idx = m.end();
//            } else {
//                idx = json.indexOf(key);
//                if (idx < 0) return 0;
//                idx += key.length();
//            }
//            int end = idx;
//            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
//            return Double.parseDouble(json.substring(idx, end));
//        } catch (Exception e) { return 0; }
//    }
//
//    static String extractStr(String json, String key) {
//        int idx = json.indexOf(key);
//        if (idx < 0) return "";
//        idx += key.length();
//        int end = json.indexOf('"', idx);
//        return end > idx ? json.substring(idx, end) : "";
//    }
//}
