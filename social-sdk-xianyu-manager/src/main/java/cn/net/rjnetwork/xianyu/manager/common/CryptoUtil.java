package cn.net.rjnetwork.xianyu.manager.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AES-256 对称加密工具，用于密文存储通道敏感配置（SMTP 密码 / Webhook secret）。
 * 密钥由 application.yml 的 crypto.secret 派生（SHA-256 -> 32 字节）。
 * 采用 AES/CBC/PKCS5Padding，随机 IV 前缀在密文前。
 */
@Component
public class CryptoUtil {

    private final SecretKeySpec key;

    public CryptoUtil(@Value("${crypto.secret}") String secret) {
        try {
            byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(raw);
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 CryptoUtil 失败", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[16];
            java.security.SecureRandom random = new java.security.SecureRandom();
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] out = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[16];
            System.arraycopy(out, 0, iv, 0, 16);
            byte[] encrypted = new byte[out.length - 16];
            System.arraycopy(out, 16, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容未加密的历史明文（直接返回）
            return cipherText;
        }
    }
}
