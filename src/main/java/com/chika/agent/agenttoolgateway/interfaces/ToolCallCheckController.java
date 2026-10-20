package com.chika.agent.agenttoolgateway.interfaces;

import com.chika.agent.agenttoolgateway.application.ToolCallCheckService;
import com.chika.agent.agenttoolgateway.interfaces.dto.ToolCheckRequest;
import com.chika.agent.agenttoolgateway.interfaces.dto.ToolCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Hook 模式接入层（v1）：只做 HTTP 协议 ↔ 应用服务的转换，
 * 不含业务逻辑。Agent 的 PreToolUse hook 调用本接口，
 * verdict 映射回 hook 的 exit code。
 */
@RestController
@RequestMapping("/v1/toolcalls")
@RequiredArgsConstructor
public class ToolCallCheckController {

    private final ToolCallCheckService checkService;

    @PostMapping("/check")
    public ResponseEntity<ToolCheckResponse> check(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody ToolCheckRequest request) {

        String agentId = checkService.authenticate(apiKey);
        if (agentId == null) {
            // 401：hook 侧应视为 DENY
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ToolCheckResponse.denied("invalid or missing API key"));
        }
        if (request.getToolName() == null || request.getToolName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ToolCheckResponse.denied("toolName is required"));
        }

        var verdict = checkService.check(agentId, request.getSessionId(),
                request.getToolName(), request.getToolArgs());
        return ResponseEntity.ok(ToolCheckResponse.from(verdict));
    }
}
