package com.chika.agent.agenttoolgateway.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 一次 tool_call 的完整上下文。
 * v1（Hook 模式）只填充 agentId/sessionId/toolName/toolArgs；
 * v2（MCP 代理）起补充 history 与 toolResult，用于响应侧脱敏与上下文感知。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallContext {

    /** Agent 身份标识（由 API key 解析得出） */
    private String agentId;

    /** 会话 ID，用于审计关联与 v3 的会话级风险链分析 */
    private String sessionId;

    /** 工具名，如 Bash / Read / WebFetch */
    private String toolName;

    /** 工具参数（原始 JSON 结构） */
    private Map<String, Object> toolArgs;

    /** v2: 会话历史（预留） */
    private List<Object> history;

    /** v2: 工具执行结果（响应侧脱敏用，预留） */
    private Object toolResult;
}
