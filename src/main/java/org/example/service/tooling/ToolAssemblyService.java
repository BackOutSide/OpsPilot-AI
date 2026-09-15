package org.example.service.tooling;

import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一的工具装配入口。
 * 先集中处理不同场景的工具集合构建，后续可在这里扩展重试、熔断和审计策略。
 */
@Service
public class ToolAssemblyService {
    private static final Logger logger = LoggerFactory.getLogger(ToolAssemblyService.class);

    // 时间工具
    private final DateTimeTools dateTimeTools;
    // 内部文档工具
    private final InternalDocsTools internalDocsTools;
    // 查询指标工具
    private final QueryMetricsTools queryMetricsTools;
    // 查询日志工具
    private final QueryLogsTools queryLogsTools;
    // 工具回调提供者
    private final ToolCallbackProvider toolCallbackProvider;

    @Autowired
    public ToolAssemblyService(DateTimeTools dateTimeTools,
                               InternalDocsTools internalDocsTools,
                               QueryMetricsTools queryMetricsTools,
                               @Autowired(required = false) QueryLogsTools queryLogsTools,
                               ToolCallbackProvider toolCallbackProvider) {
        this.dateTimeTools = dateTimeTools;
        this.internalDocsTools = internalDocsTools;
        this.queryMetricsTools = queryMetricsTools;
        this.queryLogsTools = queryLogsTools;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 装配工具
     * @param context
     * @return
     */
    public AssembledTools assembleTools(ToolAccessContext context) {
        List<Object> methodTools = new ArrayList<>();
        methodTools.add(dateTimeTools);
        methodTools.add(internalDocsTools);
        methodTools.add(queryMetricsTools);

        if (context.getRouteMode().includeLocalLogTools() && queryLogsTools != null) {
            methodTools.add(queryLogsTools);
        }

        ToolCallback[] callbacks = context.getRouteMode().includeMcpTools()
                ? sanitizeToolCallbacks(toolCallbackProvider.getToolCallbacks())
                : new ToolCallback[0];

        AssembledTools assembledTools = new AssembledTools(
                methodTools.toArray(new Object[0]),
                callbacks
        );

        logger.info("工具装配完成: mode={}, sessionId={}, methodTools={}, toolCallbacks={}",
                context.getRouteMode(),
                context.getSessionId(),
                assembledTools.methodToolNames(),
                assembledTools.toolCallbackNames());
        return assembledTools;
    }

    public ToolCallback[] getToolCallbacks(ToolAccessContext context) {
        return assembleTools(context).getToolCallbacks();
    }

    public void logAvailableTools(ToolAccessContext context) {
        AssembledTools assembledTools = assembleTools(context);
        logger.info("当前方法工具: {}", assembledTools.methodToolNames());
        logger.info("当前 MCP 工具: {}", assembledTools.toolCallbackNames());
    }

    private ToolCallback[] sanitizeToolCallbacks(ToolCallback[] callbacks) {
        if (callbacks == null || callbacks.length == 0) {
            return new ToolCallback[0];
        }
        return Arrays.stream(callbacks)
                .filter(callback -> callback != null && callback.getToolDefinition() != null)
                .toArray(ToolCallback[]::new);
    }
}
