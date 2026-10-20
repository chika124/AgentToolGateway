package com.chika.agent.agenttoolgateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 身份鉴权：API key → Agent 身份。
 * v1 用静态映射（配置文件），后续可换数据库/注册中心。
 */
@Component
public class ApiKeyAuthenticator {

    private final Map<String, String> keyToAgent;

    public ApiKeyAuthenticator(@Value("#{${agenttoolgateway.api-keys:{}}}") Map<String, String> keyToAgent) {
        this.keyToAgent = keyToAgent;
    }

    /**
     * @return 对应的 agentId；无效 key 返回 null
     */
    public String authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return keyToAgent.get(apiKey);
    }
}
