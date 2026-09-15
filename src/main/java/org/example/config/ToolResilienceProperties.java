package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 查询型工具和外部依赖的统一容错配置。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "tool.resilience")
public class ToolResilienceProperties {
    private Dependency prometheus = new Dependency();
    private Dependency logs = new Dependency();
    private Dependency reranker = new Dependency();

    @Getter
    @Setter
    public static class Dependency {
        /**
         * 是否启用统一容错执行器。
         */
        private boolean enabled = true;

        /**
         * 最大尝试次数，包含首次调用。
         */
        private int maxAttempts = 2;

        /**
         * 初始退避间隔。
         */
        private long initialBackoffMs = 200L;

        /**
         * 最大退避间隔。
         */
        private long maxBackoffMs = 1000L;

        /**
         * 熔断窗口中的连续失败阈值。
         */
        private int failureThreshold = 3;

        /**
         * 熔断打开时长。
         */
        private long openDurationMs = 15000L;
    }
}
