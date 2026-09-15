package org.example.service.tooling;

import lombok.Builder;
import lombok.Getter;

/**
 * 工具访问上下文。
 * 统一携带会话和场景信息，方便按请求类型装配工具。
 */
@Getter
@Builder(toBuilder = true)
public class ToolAccessContext {
    // 会话ID
    private final String sessionId;
    // 请求来源
    private final String requestSource;
    // 路由模式
    private final ToolRouteMode routeMode;

    /**
     * 创建聊天会话的工具访问上下文
     * @param sessionId
     * @return
     */
    public static ToolAccessContext forChat(String sessionId) {
        return ToolAccessContext.builder()
                .sessionId(sessionId)
                .requestSource("chat")
                .routeMode(ToolRouteMode.CHAT)
                .build();
    }

    /**
     * 创建聊天会话的工具访问上下文
     * @param sessionId
     * @return
     */
    public static ToolAccessContext forAiOps() {
        return ToolAccessContext.builder()
                .sessionId("ai-ops")
                .requestSource("ai_ops")
                .routeMode(ToolRouteMode.AIOPS_PLANNER)
                .build();
    }

    /**
     * 更新路由模式
     * @param routeMode
     * @return
     */
    public ToolAccessContext withRouteMode(ToolRouteMode routeMode) {
        return this.toBuilder()
                .routeMode(routeMode)
                .build();
    }
}
