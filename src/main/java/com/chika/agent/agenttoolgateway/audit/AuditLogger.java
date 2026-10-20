package com.chika.agent.agenttoolgateway.audit;

import com.chika.agent.agenttoolgateway.core.ToolCallContext;
import com.chika.agent.agenttoolgateway.core.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计：全量 tool_call + verdict 以 JSONL 追加到文件。
 * v1 文件实现足够；后续可换 SQLite/Postgres。
 */
@Slf4j
@Component
public class AuditLogger {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path auditFile;

    public AuditLogger(@Value("${agenttoolgateway.audit.file:logs/toolcall-audit.jsonl}") String file) {
        this.auditFile = Paths.get(file);
    }

    public void record(ToolCallContext ctx, Verdict verdict) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("agentId", ctx.getAgentId());
        entry.put("sessionId", ctx.getSessionId());
        entry.put("tool", ctx.getToolName());
        entry.put("args", ctx.getToolArgs());
        entry.put("decision", verdict.getDecision().name());
        entry.put("reason", verdict.getReason());
        entry.put("modifiedArgs", verdict.getModifiedArgs());
        try {
            Files.createDirectories(auditFile.getParent());
            Files.writeString(auditFile,
                    objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("审计写入失败", e);
        }
    }
}
