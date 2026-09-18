package com.rentagent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 敏感字段加密（NFR-04）：身份证号 AES-GCM 加密存储 + SHA-256 哈希比对 */
@Component
public class CryptoUtil {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(@Value("${rentagent.aes-key}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public String decrypt(String base64) {
        try {
            byte[] all = Base64.getDecoder().decode(base64);
            // 前 12 字节为随机 IV，其余为密文（含 GCM 认证标签）
            byte[] iv = Arrays.copyOf(all, GCM_IV_LEN);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(all, GCM_IV_LEN, all.length - GCM_IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    public String sha256Hex(String plain) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plain.getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
