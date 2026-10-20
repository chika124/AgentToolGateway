package com.chika.agent.agenttoolgateway.interfaces.dto;

import com.chika.agent.agenttoolgateway.domain.model.Verdict;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** /v1/toolcalls/check 响应体 */
@Data
@NoArgsConstructor
public class ToolCheckResponse {

    private String decision;          // ALLOW / DENY / MODIFY
    private String reason;
    private Map<String, Object> modifiedArgs;

    public static ToolCheckResponse from(Verdict verdict) {
        ToolCheckResponse r = new ToolCheckResponse();
        r.decision = verdict.getDecision().name();
        r.reason = verdict.getReason();
        r.modifiedArgs = verdict.getModifiedArgs();
        return r;
    }

    public static ToolCheckResponse denied(String reason) {
        ToolCheckResponse r = new ToolCheckResponse();
        r.decision = "DENY";
        r.reason = reason;
        return r;
    }
}
