package com.chika.agent.agenttoolgateway.domain.service;

import com.chika.agent.agenttoolgateway.infrastructure.audit.AuditLogger;
import com.chika.agent.agenttoolgateway.domain.model.AgentPolicySet;
import com.chika.agent.agenttoolgateway.domain.model.ToolCallContext;
import com.chika.agent.agenttoolgateway.domain.model.Verdict;
import com.chika.agent.agenttoolgateway.infrastructure.yaml.PolicyLoader;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * L1 规则引擎：按 Agent 策略集对 tool_call 做匹配、边界控制与脱敏。
 *
 * 决策顺序：deny 规则（命中即拒）→ allow 白名单（不匹配即拒）→ allow_domain 边界 → 脱敏（命中即 MODIFY）。
 * 未配置策略的 Agent 默认放行（研究场景；生产可改为默认拒绝）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RulePolicyEngine implements PolicyEngine {

    private final PolicyLoader policyLoader;
    private final SanitizeEngine sanitizeEngine;
    private final AuditLogger auditLogger;

    @Override
    public Verdict decide(ToolCallContext ctx) {
        AgentPolicySet set = policyLoader.forAgent(ctx.getAgentId());
        Verdict verdict;
        if (set == null) {
            verdict = Verdict.allow();
        } else {
            verdict = evaluate(ctx, set);
        }
        auditLogger.record(ctx, verdict);
        return verdict;
    }

    private Verdict evaluate(ToolCallContext ctx, AgentPolicySet set) {
        DocumentContext json = JsonPath.parse(ctx.getToolArgs() == null ? Map.of() : ctx.getToolArgs());

        // 先精确工具名，后通配 "*"，只取第一个命中的策略块
        AgentPolicySet.ToolPolicy policy = findPolicy(set, ctx.getToolName());
        if (policy != null) {
            Verdict v = applyRules(json, policy.getRules());
            if (v.getDecision() != Verdict.Decision.ALLOW) return v;
            Verdict s = trySanitize(ctx, policy.getSanitize());
            if (s != null) return s;
        }
        // 全局脱敏（tool: "*" 级别或策略集顶层）
        AgentPolicySet.ToolPolicy global = findPolicy(set, "*");
        if (global != null) {
            Verdict s = trySanitize(ctx, global.getSanitize());
            if (s != null) return s;
        }
        Verdict s = trySanitize(ctx, set.getGlobalSanitize());
        if (s != null) return s;
        return Verdict.allow();
    }

    private AgentPolicySet.ToolPolicy findPolicy(AgentPolicySet set, String tool) {
        if (set.getPolicies() == null) return null;
        for (AgentPolicySet.ToolPolicy p : set.getPolicies()) {
            if (tool.equals(p.getTool())) return p;
        }
        for (AgentPolicySet.ToolPolicy p : set.getPolicies()) {
            if ("*".equals(p.getTool())) return p;
        }
        return null;
    }

    private Verdict applyRules(DocumentContext json, List<AgentPolicySet.Rule> rules) {
        if (rules == null) return Verdict.allow();
        for (AgentPolicySet.Rule rule : rules) {
            String value = readField(json, rule.getField());
            if (value == null) continue;

            if (rule.getDeny() != null && Pattern.compile(rule.getDeny()).matcher(value).find()) {
                return Verdict.deny(rule.getDenyReason() != null
                        ? rule.getDenyReason()
                        : "参数 " + rule.getField() + " 命中禁止规则");
            }
            if (rule.getAllow() != null && !Pattern.compile(rule.getAllow()).matcher(value).find()) {
                return Verdict.deny(rule.getAllowReason() != null
                        ? rule.getAllowReason()
                        : "参数 " + rule.getField() + " 不在白名单内");
            }
            if (rule.getAllowDomain() != null) {
                String host = extractHost(value);
                if (host == null || !Pattern.compile(rule.getAllowDomain()).matcher(host).find()) {
                    return Verdict.deny("域名 " + (host == null ? "(无法解析)" : host) + " 不在允许边界内");
                }
            }
        }
        return Verdict.allow();
    }

    private Verdict trySanitize(ToolCallContext ctx, List<AgentPolicySet.SanitizeRule> rules) {
        if (rules == null || rules.isEmpty()) return null;
        Map<String, Object> modified = sanitizeEngine.sanitize(ctx.getToolArgs(), rules);
        return modified == null ? null : Verdict.modify(modified);
    }

    private String readField(DocumentContext json, String field) {
        if (field == null) return null;
        try {
            Object v = json.read(field);
            return v == null ? null : String.valueOf(v);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private String extractHost(String url) {
        try {
            return URI.create(url.trim()).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
