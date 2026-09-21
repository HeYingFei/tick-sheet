package com.todo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用自定义配置，对应 application.yml 中的 {@code app.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Backup backup = new Backup();

    private Cors cors = new Cors();

    @Data
    public static class Backup {
        /** 备份文件存储目录 */
        private String dir = "./data/backup";
        /** 备份保留上限，超出时提示用户清理，不自动删除 */
        private int keepMax = 20;
    }

    @Data
    public static class Cors {
        /**
         * 允许的跨域来源模式，逗号分隔，支持通配。
         *
         * <p>开发期默认放开：前端可能经 localhost、127.0.0.1、局域网 IP 或 IDE 预览域名
         * 访问，端口也不固定，逐条枚举很容易漏掉导致预检被拒（表现为
         * {@code Invalid CORS request}）。生产环境经 Nginx 同源代理后应收紧为具体域名。
         */
        private List<String> allowedOriginPatterns = List.of("*");
    }
}
