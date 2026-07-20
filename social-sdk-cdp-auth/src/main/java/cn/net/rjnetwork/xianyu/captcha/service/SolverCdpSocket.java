package cn.net.rjnetwork.xianyu.captcha.service;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDP 连接管理（原始 Socket 实现，无第三方依赖）
 * <p>负责 WebSocket 握手、文本帧收发、XOR masking。</p>
 */
public class SolverCdpSocket implements AutoCloseable {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicLong idGen = new AtomicLong(1);

    public SolverCdpSocket(String wsUrl, int timeoutMs) throws Exception {
        URI uri = URI.create(wsUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(timeoutMs);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();

        handshake(host, port, path);
    }

    private void handshake(String host, int port, String path) throws IOException {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        String req = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + host + ":" + port + "\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Key: " + key + "\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "\r\n";

        out.write(req.getBytes(StandardCharsets.UTF_8));
        out.flush();

        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (true) {
            int n = in.read(buf);
            if (n == -1) throw new IOException("handshake: connection closed");
            headerBuf.write(buf, 0, n);
            byte[] all = headerBuf.toByteArray();
            int end = findHeaderEnd(all);
            if (end >= 0) {
                String resp = new String(all, 0, end, StandardCharsets.UTF_8);
                if (!resp.contains("101")) throw new IOException("handshake failed: " + resp.substring(0, Math.min(200, resp.length())));
                return;
            }
        }
    }

    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i <= data.length - 4; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    /**
     * 发送文本帧（opcode=0x81，客户端→服务端需 XOR masking）
     */
    public synchronized void sendText(String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey = new byte[4];
        RANDOM.nextBytes(maskKey);

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text opcode

        int len = payloadBytes.length;
        if (len < 126) {
            frame.write(0x80 | len);
        } else if (len < 65536) {
            frame.write(0x80 | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 56; i >= 0; i -= 8) {
                frame.write((len >> i) & 0xFF);
            }
        }

        frame.write(maskKey);
        for (int i = 0; i < payloadBytes.length; i++) {
            frame.write(payloadBytes[i] ^ maskKey[i % 4]);
        }

        out.write(frame.toByteArray());
        out.flush();
    }

    /**
     * 读取一帧（阻塞直到完整帧或超时）
     */
    public synchronized String readFrame() throws IOException {
        ByteArrayOutputStream fragment = new ByteArrayOutputStream();
        byte[] readBuf = new byte[8192];

        while (true) {
            int n = in.read(readBuf);
            if (n == -1) throw new IOException("connection closed");
            fragment.write(readBuf, 0, n);
            byte[] data = fragment.toByteArray();

            if (data.length < 2) continue;

            int opcode = data[0] & 0x0F;
            int secondByte = data[1] & 0xFF;
            long payloadLen = secondByte & 0x7F;
            int offset = 2;

            if (payloadLen == 126) {
                if (data.length < offset + 2) continue;
                payloadLen = ((long) (data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                offset += 2;
            } else if (payloadLen == 127) {
                if (data.length < offset + 8) continue;
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLen = (payloadLen << 8) | (data[offset + i] & 0xFF);
                }
                offset += 8;
            }

            boolean masked = (secondByte & 0x80) != 0;
            if (masked) offset += 4;

            if (data.length < offset + payloadLen) continue;

            if (opcode == 8) return null; // close frame

            byte[] payload = new byte[(int) payloadLen];
            System.arraycopy(data, offset, payload, 0, payload.length);

            if (masked) {
                byte[] maskKey = new byte[4];
                System.arraycopy(data, offset - 4, maskKey, 0, 4);
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    public long nextId() {
        return idGen.getAndIncrement();
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    public boolean isClosed() {
        return socket.isClosed();
    }
}
