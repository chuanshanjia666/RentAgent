package com.rentagent.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段加密单元测试（NFR-04）：身份证 AES-GCM 加解密与 SHA-256 哈希比对。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-CRYPTO-xx）。
 */
class CryptoUtilTest {

    private final CryptoUtil crypto = new CryptoUtil(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    @DisplayName("UT-CRYPTO-01 AES-GCM 加密后可原样解密")
    void encryptThenDecryptRoundTrip() {
        String plain = "210102198001011234";

        String enc = crypto.encrypt(plain);

        assertEquals(plain, crypto.decrypt(enc));
    }

    @Test
    @DisplayName("UT-CRYPTO-02 密文不含明文且随机 IV 使同一明文两次密文不同")
    void randomIvMakesCiphertextUnique() {
        String plain = "210102198001011234";

        String first = crypto.encrypt(plain);
        String second = crypto.encrypt(plain);

        assertNotEquals(plain, first);
        assertNotEquals(first, second, "每次加密随机 IV，密文不可比对");
        assertEquals(plain, crypto.decrypt(first));
        assertEquals(plain, crypto.decrypt(second));
    }

    @Test
    @DisplayName("UT-CRYPTO-03 SHA-256 哈希符合标准向量且稳定")
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                crypto.sha256Hex("abc"));
        assertEquals(crypto.sha256Hex("210102198001011234"), crypto.sha256Hex("210102198001011234"));
    }

    @Test
    @DisplayName("UT-CRYPTO-04 密文被篡改时 GCM 完整性校验失败")
    void failsOnTamperedCiphertext() {
        String enc = crypto.encrypt("210102198001011234");
        byte[] raw = Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    @DisplayName("UT-CRYPTO-05 非法密文解密抛出明确异常，不返回脏数据")
    void failsOnMalformedCiphertext() {
        assertThrows(IllegalStateException.class, () -> crypto.decrypt("不是密文"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(Base64.getEncoder().encodeToString(new byte[4])));
    }

    @Test
    @DisplayName("UT-CRYPTO-06 中文姓名等 UTF-8 内容加解密无损")
    void preservesUtf8Content() {
        String plain = "李建国·测试中文与符号 #@!";

        String enc = crypto.encrypt(plain);

        assertEquals(plain, crypto.decrypt(enc));
        assertTrue(crypto.sha256Hex(plain).matches("[0-9a-f]{64}"));
    }
}
