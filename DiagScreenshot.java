import cn.net.rjnetwork.chrome.cdp.CdpClient;

public class DiagScreenshot {
    public static void main(String[] a) throws Exception {
        System.out.println("=== 1. 连接 browser ===");
        CdpClient c = CdpClient.attachRemote("http://192.168.1.127:9333");
        System.out.println("OK");

        System.out.println("=== 2. 新建 target ===");
        String tid = c.createTarget("https://www.goofish.com");
        System.out.println("targetId=" + tid);

        System.out.println("=== 3. attach ===");
        String sid = c.attachTarget(tid);
        c.setSessionId(sid);
        System.out.println("sessionId=" + sid);

        System.out.println("=== 4. activateTarget ===");
        try {
            c.activateTarget(tid).get(5, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("activated");
        } catch (Exception e) {
            System.out.println("activate failed: " + e);
        }

        System.out.println("=== 5. sleep 5s ===");
        Thread.sleep(5000);

        System.out.println("=== 6. evaluate title ===");
        try {
            String title = c.evaluateString("document.title");
            System.out.println("title=" + title);
        } catch (Exception e) {
            System.out.println("title failed: " + e);
        }

        System.out.println("=== 7. screenshot (10s timeout) ===");
        try {
            var shot = c.captureScreenshot().get(10, java.util.concurrent.TimeUnit.SECONDS);
            String data = shot.get("data").asText();
            System.out.println("screenshot OK, b64 length=" + data.length());
        } catch (Exception e) {
            System.out.println("screenshot failed: " + e);
        }

        System.out.println("=== 8. close ===");
        try { c.closeTarget(tid); } catch (Exception e) { System.out.println("close: " + e); }
        c.close();
        System.exit(0);
    }
}
