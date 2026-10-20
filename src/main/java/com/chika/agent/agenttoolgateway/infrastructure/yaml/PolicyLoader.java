package com.chika.agent.agenttoolgateway.infrastructure.yaml;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import com.chika.agent.agenttoolgateway.domain.model.AgentPolicySet;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略加载：启动时从 policies.yaml 读取并解析为 AgentPolicySet，
 * 按 agentId 建立索引。修改策略需重启（v1 足够，后续可加热加载）。
 */
@Slf4j
@Component
public class PolicyLoader {

    private final Map<String, AgentPolicySet> policyByAgent = new ConcurrentHashMap<>();

    public PolicyLoader(@Value("classpath:policies.yaml") Resource resource) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            Yaml yaml = new Yaml(new LoaderOptions());
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                log.warn("policies.yaml 为空，所有 tool_call 将默认放行");
                return;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agents = (List<Map<String, Object>>) root.get("agents");
            if (agents == null) return;
            Yaml mapper = new Yaml(new LoaderOptions());
            for (Map<String, Object> agent : agents) {
                AgentPolicySet set = mapper.loadAs(mapper.dump(agent), AgentPolicySet.class);
                policyByAgent.put(set.getId(), set);
            }
            log.info("已加载 {} 个 Agent 的策略集", policyByAgent.size());
        }
    }

    /** 查询某 Agent 的策略集；未配置返回 null（由引擎决定默认行为） */
    public AgentPolicySet forAgent(String agentId) {
        return policyByAgent.get(agentId);
    }
}
