package org.example.service.tooling;

import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;

/**
 * 统一封装一次工具装配的结果。
 */
public class AssembledTools {
    private final Object[] methodTools;
    private final ToolCallback[] toolCallbacks;

    public AssembledTools(Object[] methodTools, ToolCallback[] toolCallbacks) {
        this.methodTools = methodTools;
        this.toolCallbacks = toolCallbacks;
    }

    public Object[] getMethodTools() {
        return methodTools;
    }

    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks;
    }

    public List<String> methodToolNames() {
        return Arrays.stream(methodTools)
                .map(tool -> tool.getClass().getSimpleName())
                .toList();
    }

    public List<String> toolCallbackNames() {
        return Arrays.stream(toolCallbacks)
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }
}
