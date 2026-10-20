package com.chika.agent.agenttoolgateway.infrastructure.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 网关配置项（application.yaml 的 agenttoolgateway.* 前缀） */
@Data
@Component
@ConfigurationProperties(prefix = "agenttoolgateway")
public class GatewayProperties {

    /** API key → Agent 身份映射 */
    private Map<String, String> apiKeys = new HashMap<>();

    private Audit audit = new Audit();

    @Data
    public static class Audit {
        private String file = "logs/toolcall-audit.jsonl";
    }
}
