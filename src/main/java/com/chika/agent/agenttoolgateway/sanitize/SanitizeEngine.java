package com.chika.agent.agenttoolgateway.sanitize;

import com.chika.agent.agenttoolgateway.policy.AgentPolicySet;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 脱敏引擎：对 toolArgs 的字符串值做规则检测与掩码。
 * 命中返回改写后的参数副本，未命中返回 null（表示无需 MODIFY）。
 */
@Component
public class SanitizeEngine {

    /**
     * 对参数中所有字符串值应用脱敏规则。
     * @return 改写后的参数；未命中任何规则时返回 null
     */
    public Map<String, Object> sanitize(Map<String, Object> args, List<AgentPolicySet.SanitizeRule> rules) {
        if (rules == null || rules.isEmpty() || args == null || args.isEmpty()) {
            return null;
        }
        Map<Pattern, String> compiled = rules.stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> Pattern.compile(r.getPattern()), AgentPolicySet.SanitizeRule::getReplace));

        Map<String, Object> modified = null;
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object replaced = sanitizeValue(e.getValue(), compiled);
            if (replaced != e.getValue()) {
                if (modified == null) modified = new HashMap<>(args);
                modified.put(e.getKey(), replaced);
            }
        }
        return modified;
    }

    /** 递归处理字符串/嵌套结构；无改动时原样返回同一引用以便调用方判断 */
    private Object sanitizeValue(Object value, Map<Pattern, String> compiled) {
        if (value instanceof String s) {
            String result = s;
            for (Map.Entry<Pattern, String> rule : compiled.entrySet()) {
                result = rule.getKey().matcher(result).replaceAll(rule.getValue());
            }
            return result;
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> copy = null;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object replaced = sanitizeValue(e.getValue(), compiled);
                if (replaced != e.getValue()) {
                    if (copy == null) copy = new HashMap<>();
                    copy.put(String.valueOf(e.getKey()), replaced);
                }
            }
            if (copy == null) return value;
            Map<String, Object> merged = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) merged.put(String.valueOf(e.getKey()), e.getValue());
            merged.putAll(copy);
            return merged;
        }
        if (value instanceof List<?> l) {
            List<Object> copy = null;
            for (int i = 0; i < l.size(); i++) {
                Object replaced = sanitizeValue(l.get(i), compiled);
                if (replaced != l.get(i)) {
                    if (copy == null) copy = new java.util.ArrayList<>(l);
                    copy.set(i, replaced);
                }
            }
            return copy == null ? value : copy;
        }
        return value;
    }
}
