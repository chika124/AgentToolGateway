package com.chika.agent.agenttoolgateway.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 策略 DSL 的 Java 模型（见 resources/policies.yaml）。
 */
@Data
public class AgentPolicySet {

    private String id;
    private String description;
    private List<ToolPolicy> policies;
    private List<SanitizeRule> globalSanitize;

    @Data
    public static class ToolPolicy {
        private String tool;             // 工具名，"*" 表示所有工具
        private List<Rule> rules;
        private List<SanitizeRule> sanitize;
    }

    @Data
    public static class Rule {
        private String field;            // JSONPath，如 $.command
        private String deny;             // 正则：命中 → DENY
        private String denyReason;
        private String allow;            // 正则：白名单模式，仅命中才放行
        private String allowReason;
        private String allowDomain;      // 域名白名单（对 $.url 之类的字段）
    }

    @Data
    public static class SanitizeRule {
        private String pattern;          // 检测正则
        private String replace;          // 替换文本
    }
}
