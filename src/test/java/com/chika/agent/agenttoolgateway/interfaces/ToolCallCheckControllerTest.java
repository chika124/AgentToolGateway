package com.chika.agent.agenttoolgateway.interfaces;

import com.chika.agent.agenttoolgateway.domain.service.PolicyEngine;
import com.chika.agent.agenttoolgateway.interfaces.dto.ToolCheckResponse;
import com.chika.agent.agenttoolgateway.domain.model.Verdict;
import com.chika.agent.agenttoolgateway.domain.model.ToolCallContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ToolCallCheckControllerTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    PolicyEngine policyEngine;

    private ToolCheckResponse post(Map<String, Object> body, String apiKey) throws Exception {
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/toolcalls/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readValue(result.getResponse().getContentAsString(), ToolCheckResponse.class);
    }

    @Test
    void invalidApiKeyRejected() throws Exception {
        var body = Map.of("toolName", "Bash", "toolArgs", Map.of("command", "ls"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/toolcalls/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "wrong-key")
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void destructiveBashDenied() throws Exception {
        var body = Map.of("toolName", "Bash", "toolArgs", Map.of("command", "rm -rf /tmp/x"));
        var resp = post(body, "dev-local-key-001");
        assertThat(resp.getDecision()).isEqualTo("DENY");
        assertThat(resp.getReason()).contains("禁止破坏性");
    }

    @Test
    void whitelistViolatingBashDenied() throws Exception {
        var body = Map.of("toolName", "Bash", "toolArgs", Map.of("command", "nc -l 9999"));
        assertThat(post(body, "dev-local-key-001").getDecision()).isEqualTo("DENY");
    }

    @Test
    void normalBashAllowed() throws Exception {
        var body = Map.of("toolName", "Bash", "toolArgs", Map.of("command", "git status"));
        assertThat(post(body, "dev-local-key-001").getDecision()).isEqualTo("ALLOW");
    }

    @Test
    void sensitiveReadDenied() throws Exception {
        var body = Map.of("toolName", "Read", "toolArgs", Map.of("file_path", "/home/u/.ssh/id_rsa"));
        assertThat(post(body, "dev-local-key-001").getDecision()).isEqualTo("DENY");
    }

    @Test
    void externalUrlBlockedByDomainBoundary() throws Exception {
        var body = Map.of("toolName", "WebFetch",
                "toolArgs", Map.of("url", "https://evil.example.com/data"));
        var resp = post(body, "dev-local-key-001");
        assertThat(resp.getDecision()).isEqualTo("DENY");
        assertThat(resp.getReason()).contains("边界");
    }

    @Test
    void apiKeyInArgsSanitizedToModify() throws Exception {
        var body = Map.of("toolName", "Write",
                "toolArgs", Map.of("content", "token is sk-AbCdEfGhIjKlMnOpQrSt1234 done"));
        var resp = post(body, "dev-local-key-001");
        assertThat(resp.getDecision()).isEqualTo("MODIFY");
        assertThat(resp.getModifiedArgs())
                .containsEntry("content", "token is sk-***REDACTED*** done");
    }

    @Test
    void engineDirectDenyHasReason() throws Exception {
        Verdict v = policyEngine.decide(ToolCallContext.builder()
                .agentId("claude-code-dev").toolName("Bash")
                .toolArgs(Map.of("command", "sudo reboot")).build());
        assertThat(v.getDecision()).isEqualTo(Verdict.Decision.DENY);
        assertThat(v.getReason()).isNotBlank();
    }
}
