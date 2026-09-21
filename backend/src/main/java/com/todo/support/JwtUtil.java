package com.todo.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 轻量级 JWT 工具，基于 JDK 自带的 HMAC-SHA256 实现，无需第三方依赖。
 *
 * <p>token 格式：header.payload.signature（标准三段式）
 */
public final class JwtUtil {

    private static final String ALG = "HmacSHA256";
    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final long DEFAULT_EXPIRE_MS = 7 * 24 * 3600 * 1000L; // 7 天

    private JwtUtil() {
    }

    /**
     * 签发 token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param secret   签名密钥
     */
    public static String sign(Long userId, String username, String secret) {
        return sign(userId, username, secret, DEFAULT_EXPIRE_MS);
    }

    public static String sign(Long userId, String username, String secret, long expireMs) {
        long exp = System.currentTimeMillis() + expireMs;
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"userId\":" + userId + ",\"username\":\"" + username + "\",\"exp\":" + exp + "}")
                        .getBytes(StandardCharsets.UTF_8));

        String content = HEADER + "." + payload;
        String signature = hmacSha256(content, secret);
        return content + "." + signature;
    }

    /**
     * 验证并解析 token，返回 userId。无效或过期返回 null。
     */
    public static Long verify(String token, String secret) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        // 验签
        String expectedSig = hmacSha256(parts[0] + "." + parts[1], secret);
        if (!constantTimeEquals(expectedSig, parts[2])) {
            return null;
        }

        // 解析 payload
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // 简单解析，不引入 JSON 库
            long exp = extractLong(json, "exp");
            if (System.currentTimeMillis() > exp) {
                return null; // 过期
            }
            return extractLong(json, "userId");
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    /** 常量时间比较，防止时序攻击 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /** 从简易 JSON 中提取 long 值 */
    private static long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) throw new IllegalArgumentException("missing key: " + key);
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
