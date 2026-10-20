package com.chika.agent.agenttoolgateway.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 策略引擎的裁决结果。
 *
 * - ALLOW: 放行，Agent 本地执行工具
 * - DENY: 拒绝，reason 会回喂给模型形成"策略反馈闭环"
 * - MODIFY: 放行但参数已被改写（如脱敏掩码），modifiedArgs 为改写后的完整参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Verdict {

    public enum Decision { ALLOW, DENY, MODIFY }

    private Decision decision;
    private String reason;
    private Map<String, Object> modifiedArgs;

    public static Verdict allow() {
        return new Verdict(Decision.ALLOW, null, null);
    }

    public static Verdict deny(String reason) {
        return new Verdict(Decision.DENY, reason, null);
    }

    public static Verdict modify(Map<String, Object> modifiedArgs) {
        return new Verdict(Decision.MODIFY, "参数已脱敏处理", modifiedArgs);
    }
}
