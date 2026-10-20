package com.chika.agent.agenttoolgateway.application;

import com.chika.agent.agenttoolgateway.domain.model.ToolCallContext;
import com.chika.agent.agenttoolgateway.domain.model.Verdict;
import com.chika.agent.agenttoolgateway.domain.service.PolicyEngine;
import com.chika.agent.agenttoolgateway.infrastructure.auth.ApiKeyAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用服务：编排一次 tool_call 检查的完整用例
 * （鉴权 → 构建上下文 → 策略决策）。
 * Controller 只做协议转换，不包含业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class ToolCallCheckService {

    private final ApiKeyAuthenticator authenticator;
    private final PolicyEngine policyEngine;

    /** 鉴权失败返回 null（Controller 映射为 401） */
    public String authenticate(String apiKey) {
        return authenticator.authenticate(apiKey);
    }

    public Verdict check(String agentId, String sessionId, String toolName,
                         java.util.Map<String, Object> toolArgs) {
        ToolCallContext ctx = ToolCallContext.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .toolName(toolName)
                .toolArgs(toolArgs)
                .build();
        return policyEngine.decide(ctx);
    }
}
