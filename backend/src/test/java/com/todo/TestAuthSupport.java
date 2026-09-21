package com.todo;

import com.todo.config.AuthProperties;
import com.todo.support.JwtUtil;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 测试用认证支持。
 *
 * <p>自 V2 起 {@code /api/**} 全部经由 AuthInterceptor 校验 JWT，这里通过 MockMvc 的
 * 默认请求统一携带 Authorization，避免在每个用例里重复拼装 token，同时保留真实的认证链路。
 *
 * <p>固定使用第一个账号（BIGSERIAL 从 1 开始）作为测试数据的归属，
 * 这样用例内创建的数据与查询条件天然落在同一用户名下。
 */
@TestConfiguration
public class TestAuthSupport {

    /** 测试数据统一归属的用户 ID */
    public static final long TEST_USER_ID = 1L;

    @Bean
    MockMvcBuilderCustomizer authHeaderCustomizer(AuthProperties authProperties) {
        String token = JwtUtil.sign(TEST_USER_ID, "admin",
                authProperties.getSecret(), authProperties.getExpireMs());
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header("Authorization", "Bearer " + token));
    }
}
