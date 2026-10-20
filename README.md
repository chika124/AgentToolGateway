# AgentToolGateway

> 面向 Agent 工具调用的语义权限控制与安全策略研究
> 目标：构建一个通用的 Agent 工具网关，对 Agent 的 tool_call 做鉴权、语义权限拦截、边界控制与脱敏，最终以**凭证签发模式**实现集团级强制接入。

## 1. 核心理念

Agent（LLM 驱动的自主智能体）可以发起任意工具调用，这是最大的安全敞口。传统 ACL 只能回答"Bash 工具允不允许"，回答不了"Bash 里执行的 `rm -rf` 和 `ls` 该不该区别对待"。本项目在**工具调用层**引入网关，做三件事：

1. **鉴权拦截**：Agent 身份（API key → AgentId）→ 绑定策略集
2. **语义权限**：不按工具名一刀切，而是对调用参数做语义级裁决（`Bash("rm -rf /")` ≠ `Bash("ls")`）
3. **脱敏改写**：tool_call 参数中的敏感数据（API key、身份证号等）在放行前被掩码改写

## 2. 总体架构：决策核心与接入协议分离

```
接入层（可替换）                       决策核心（不变）
──────────────────────────           ─────────────────────────────
① Hook 适配器（v1，研究脚手架）        PolicyEngine.decide(ctx)
② 凭证签发模式（v2，集团化主形态）          ├─ 身份鉴权（API key → AgentId → 策略集）
③ MCP 代理模式（v2+，可选形态）             │    （Agent 身份 → 策略集）
                                           ├─ 策略匹配（YAML 规则引擎）
                                           │    工具名 + 参数 JSONPath/正则
                                           ├─ 语义意图判断（模型/embedding，v3）
                                           ├─ 边界控制（路径/网络/资源白名单）
                                           ├─ 脱敏/改写（PII、密钥检测）
                                           └─ 审计（全量 tool_call 日志）
                                                  │
                                       verdict = ALLOW / DENY(reason) / MODIFY(newArgs)
```

**核心原则**：`PolicyEngine` 是纯决策核心，接入层（Hook / 凭证签发 / MCP 代理）只是外壳，演进接入形态不动策略代码。

### 2.1 接入形态演进

| 形态 | 网关在数据链路上 | 每个 Agent 接入成本 | 强制性 | 定位 |
|---|---|---|---|---|
| **Hook**（v1） | 不在 | 改配置+装脚本 | 可绕过（本地信任） | 研究脚手架，验证策略引擎 |
| **凭证签发**（v2 主线） | 不在（只签凭证） | 调一次网关拿凭证 | 密码学强制 | 集团级主形态 |
| **MCP 代理**（v2+ 可选） | 在（全流量过网关） | 配 MCP 地址，零改造 | 凭据收口 | 需要网关统一管控流量的场景 |

### 2.2 凭证签发模式（v2 主形态）

Agent 不持有任何工具凭据，网关是"签证官"，工具是"边检"：

```
Agent ──① tool_call──► 网关（鉴权 + 语义策略 + 脱敏）
Agent ◄─② 凭证(短期签名 JWT)── 网关

Agent ──③ 带凭证调用──► 工具（只验凭证，不认识 Agent）
工具 ◄──④ 本地验签：参数指纹匹配则放行，否则拒绝
```

凭证与本次调用内容密码学绑定：

- `argsHash`：凭证绑定参数指纹，Agent 拿"查 A 表"的凭证改去查 B 表 → 验签失败
- `jti`：单次有效，防重放
- `exp`：短 TTL（默认 60s），撤销窗口极小
- MODIFY 场景下网关对**脱敏后的参数**计算指纹签发 → 原始敏感数据密码学上就送不出去

工具侧一次性接入验签 SDK/Filter（改造有限的、集团管控的工具端，而不是无限的 Agent 端）。

## 3. 语义权限三层级（研究核心）

| 层级 | 能力 | 实现 | 版本 |
|---|---|---|---|
| L1 规则引擎 | 工具名 + 参数 JSONPath/正则。`Bash.command` 匹配 `rm -rf` → DENY | 纯代码 | v1 已实现 |
| L2 语义意图 | 参数内容过小模型做意图分类 | LLM API | v3 |
| L3 上下文感知 | 会话内风险链检测（读密钥 → 尝试外发） | 会话状态 | v3 |

## 4. 项目结构（DDD 四层）

```
com.chika.agent.agenttoolgateway
├── interfaces/          接入层（对外协议）
│   ├── ToolCallCheckController     REST Controller，只做协议转换
│   └── dto/                        ToolCheckRequest / ToolCheckResponse
├── application/         应用层（用例编排）
│   └── ToolCallCheckService        鉴权 → 构建上下文 → 策略决策
├── domain/              领域层（核心业务）
│   ├── model/                     ToolCallContext / Verdict / AgentPolicySet
│   └── service/                    PolicyEngine 接口 / RulePolicyEngine / SanitizeEngine
└── infrastructure/      基础设施层
    ├── auth/                       ApiKeyAuthenticator / GatewayProperties
    ├── yaml/                       PolicyLoader（policies.yaml）
    └── audit/                      AuditLogger（JSONL 审计）
```

依赖方向：`interfaces → application → domain ← infrastructure`。

## 5. 快速开始

### 启动网关

```bash
./mvnw spring-boot:run
# 监听 8090 端口
```

### 调用检查接口

```bash
curl -X POST http://localhost:8090/v1/toolcalls/check \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-local-key-001" \
  -d '{
    "sessionId": "test-session",
    "toolName": "Bash",
    "toolArgs": {"command": "rm -rf /tmp/x"}
  }'
```

响应（DENY，reason 会回喂给模型形成策略反馈闭环）：

```json
{"decision": "DENY", "reason": "禁止破坏性/提权命令", "modifiedArgs": null}
```

敏感参数命中脱敏规则时返回 MODIFY 与改写后的参数：

```json
{"decision": "MODIFY", "reason": "参数已脱敏处理",
 "modifiedArgs": {"content": "token is sk-***REDACTED*** done"}}
```

### 策略配置（`src/main/resources/policies.yaml`）

```yaml
agents:
  - id: claude-code-dev
    policies:
      - tool: "Bash"
        rules:
          - field: "$.command"
            deny: "\\brm\\s+-rf\\b|\\bsudo\\b"     # 正则命中 → DENY
            denyReason: "禁止破坏性/提权命令"
          - field: "$.command"
            allow: "^(ls|cat|git)\\b"               # 白名单模式：不匹配 → DENY
      - tool: "WebFetch"
        rules:
          - field: "$.url"
            allowDomain: "internal\\.corp\\.com"     # 域名边界
      - tool: "*"
        sanitize:                                    # 命中 → MODIFY
          - pattern: "sk-[A-Za-z0-9]{20,}"
            replace: "sk-***REDACTED***"
```

决策顺序：`deny` → `allow` 白名单 → `allowDomain` → `sanitize`。未配置策略的 Agent 默认放行（生产应改为默认拒绝）。

### Agent 侧 Hook 接入（v1 研究形态）

Claude Code 的 `settings.json`：

```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "*",
        "hooks": [{ "type": "command",
                    "command": "bash /path/to/scripts/agent-tool-gateway-hook.sh" }] }
    ]
  }
}
```

hook 将网关 verdict 映射回 Claude Code 协议：`exit 0` 放行、`exit 2` + stderr reason 阻止调用并把拒绝原因喂回模型。

### 审计日志

全量 tool_call + 裁决结果追加写入 `logs/toolcall-audit.jsonl`（JSONL），含时间戳、Agent、会话、参数、决策与原因。

## 6. 路线图

- **v1（当前）**：Hook 模式闭环——REST 检查接口 + API key 鉴权 + L1 规则引擎 + 边界控制 + 脱敏改写 + JSONL 审计 ✅
- **v2**：凭证签发模式——`POST /v1/toolcalls/authorize` 签发与调用内容绑定的短期 JWT（argsHash + jti + exp）；工具侧验签 SDK/Spring Filter 示例；MODIFY 升级为"对脱敏后参数签发"
- **v3**：研究深化——L2 语义意图分类（小模型）、L3 会话风险链检测、策略反馈闭环实验（统计 deny 后模型行为改换路径，评估策略可绕过性）、策略推荐

详细计划见 [PLAN.md](PLAN.md)。

## 7. 技术栈

Spring Boot 4.1（webmvc）· Lombok · Jackson · JsonPath · SnakeYAML；v2 计划引入 jjwt（凭证签发）与 MCP（可选代理形态）。
