package org.example.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ToolCallbackProvider 兜底配置
 * 当 MCP 客户端未启用或未成功注册工具时，提供空实现，保证应用仍可启动。
 */
@Configuration
public class ToolCallbackProviderConfig {

    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider fallbackToolCallbackProvider() {
        return () -> new ToolCallback[0];
    }
}
