package com.chika.agent.agenttoolgateway.interfaces.dto;

import lombok.Data;

import java.util.Map;

/** /v1/toolcalls/check 请求体 */
@Data
public class ToolCheckRequest {
    private String sessionId;
    private String toolName;
    private Map<String, Object> toolArgs;
}
