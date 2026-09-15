package org.example.service.tooling;

/**
 * 定义不同 Agent/场景下的工具装配模式。
 * 当前先按场景划分本地工具暴露范围，后续可继续扩展权限、重试、熔断等策略。
 */
public enum ToolRouteMode {
    CHAT(false, true),
    AIOPS_PLANNER(true, true),
    AIOPS_EXECUTOR(true, true);

    private final boolean includeLocalLogTools;
    private final boolean includeMcpTools;

    ToolRouteMode(boolean includeLocalLogTools, boolean includeMcpTools) {
        this.includeLocalLogTools = includeLocalLogTools;
        this.includeMcpTools = includeMcpTools;
    }

    public boolean includeLocalLogTools() {
        return includeLocalLogTools;
    }

    public boolean includeMcpTools() {
        return includeMcpTools;
    }
}
