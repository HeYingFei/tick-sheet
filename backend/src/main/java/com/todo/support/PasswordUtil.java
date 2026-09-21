package com.todo.support;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希工具，使用 PBKDF2WithHmacSHA256（JDK 自带，无需第三方依赖）。
 *
 * <p>存储格式：{@code iterations:base64salt:base64hash}
 */
public final class PasswordUtil {

    private static final String ALG = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;

    private PasswordUtil() {
    }

    /**
     * 对明文密码进行哈希。
     */
    public static String hash(String plain) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        byte[] hash = pbkdf2(plain.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        return ITERATIONS
                + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 验证明文密码是否与哈希匹配。
     */
    public static boolean verify(String plain, String stored) {
        if (plain == null || stored == null) {
            return false;
        }

        String[] parts = stored.split(":");
        if (parts.length != 3) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);

            byte[] actualHash = pbkdf2(plain.toCharArray(), salt, iterations, expectedHash.length * 8);
            return constantTimeEquals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALG);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 不可用", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
