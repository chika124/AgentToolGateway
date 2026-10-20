package com.chika.agent.agenttoolgateway.domain.service;

import com.chika.agent.agenttoolgateway.domain.model.ToolCallContext;
import com.chika.agent.agenttoolgateway.domain.model.Verdict;
/**
 * 决策核心接口。接入层（Hook / MCP 代理 / SDK 中间件）只依赖此接口，
 * 策略实现可替换，接入协议演进不影响决策核心。
 */
public interface PolicyEngine {

    /**
     * 调用前决策：鉴权后对 toolArgs 做策略匹配、边界控制与脱敏改写。
     */
    Verdict decide(ToolCallContext ctx);

    /**
     * 响应侧决策（v2 MCP 代理模式启用，v1 空实现）：
     * 对工具执行返回的内容做脱敏/exfiltration 检测。
     */
    default Verdict decideOnResult(ToolCallContext ctx) {
        return Verdict.allow();
    }
}
