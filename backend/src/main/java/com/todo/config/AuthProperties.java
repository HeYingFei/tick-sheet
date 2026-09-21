package com.todo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置，对应 application.yml 中的 {@code auth.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 签名密钥，生产环境必须修改 */
    private String secret = "todo-system-default-secret-change-me-in-production";

    /** Token 有效期，毫秒。默认 7 天 */
    private long expireMs = 7 * 24 * 3600 * 1000L;
}
