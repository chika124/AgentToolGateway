# AgentToolGateway 项目计划

> 面向 Agent 工具调用的语义权限控制与安全策略研究
> 目标：构建一个通用的 Agent 工具网关，对 Agent 的 tool_call 做鉴权、语义权限拦截、边界控制与脱敏。

## 1. 总体架构

核心原则：**决策核心与接入协议分离**。接入层可替换（Hook / MCP 代理 / SDK 中间件），策略引擎不变。

```
接入层（可替换）                    决策核心（不变）
──────────────────────            ─────────────────────────────
① Hook 适配器（v1，Claude Code     PolicyEngine.decide(ctx)
   PreToolUse → REST）                  ├─ 身份鉴权（API key / mTLS）
② MCP 代理适配器（v2，网关作为       │    （Agent 身份 → 策略集）
   MCP server，包装真实工具）           ├─ 策略匹配（YAML 规则引擎）
③ SDK 中间件适配器（远期）             │    工具名 + 参数 JSONPath/正则
                                       ├─ 语义意图判断（模型/embedding）
                                       ├─ 边界控制（路径/网络/资源白名单）
                                       ├─ 脱敏/改写（PII、密钥检测）
                                       └─ 审计（全量 tool_call 日志）
                                              │
                                    verdict = ALLOW / DENY(reason) / MODIFY(newArgs)
                                              │
                            ┌─────────────────┴────────────────┐
                       Hook 模式：verdict 返回 Agent，    MCP 模式：网关自己执行工具，
                       Agent 本地执行（deny 时 reason       参数与响应双向过策略引擎
                       喂回模型形成反馈闭环）                （响应侧脱敏防数据外泄）
```

### 关键设计决策

- **`ToolCallContext` 从第一天就预留全字段**：`agentId / sessionId / toolName / toolArgs / history / toolResult`。Hook 模式只填前几个，MCP 阶段平滑填充其余字段，不改模型。
- **策略引擎提供两个入口**：`decide(ctx)`（调用前）与 `decideOnResult(ctx)`（响应侧，MCP 阶段启用，v1 先留接口）。
- **deny 不是终点**：拒绝原因回喂给模型，形成"策略反馈闭环"——这是安全策略研究的实验场（观察模型被拒后的行为改换）。
- **Spring Cloud Gateway 定位**：v1 不用（纯策略服务，REST 接口即可）；MCP 阶段用于转发流式响应/SSE。

## 2. 语义权限的三个层级（研究核心，逐步演进）

| 层级 | 能力 | 实现 |
|---|---|---|
| L1 规则引擎 | 工具名 + 参数 JSONPath/正则匹配。如 `Bash.command` 匹配 `rm -rf|sudo` → deny | 纯代码，v1 落地 |
| L2 语义意图 | 参数内容过小模型/embedding 做意图分类：`Bash("rm -rf /")` vs `Bash("ls -la")` 语义不同 | 调用 LLM API，v2 |
| L3 上下文感知 | 结合会话历史判断风险链：如"读密钥文件后尝试 curl 外网"→ 数据外泄风险 | 需维护会话状态，v3 |

## 3. 版本路线

### v1 — Hook 模式跑通闭环（当前阶段）

**网关侧（Spring Boot，本项目）**

- [ ] `POST /v1/toolcalls/check` 接口：入参 = `ToolCallContext`，出参 = `Verdict`
- [ ] 身份鉴权：API key（header），Agent 身份 → 绑定策略集
- [ ] L1 规则引擎：策略 YAML 声明式配置（见 §4 策略 DSL）
- [ ] 边界控制：路径白名单、URL 域名白名单、资源类参数约束
- [ ] 脱敏改写：tool_args 中 PII/密钥的规则检测与掩码（返回 MODIFY verdict）
- [ ] 审计日志：全量 tool_call + verdict 落库/落文件，支持查询回放
- [ ] `decideOnResult(ctx)` 接口占位（v1 空实现）

**Agent 侧（示例，非本项目代码）**

- [ ] Claude Code PreToolUse hook 示例（shell 脚本：读 stdin JSON → curl 网关 → exit code 映射 verdict：0=allow / 2=deny+reason / modify 场景返回更新后 JSON）

**验收标准**：在 Claude Code 中配置 hook，敏感命令被拦截并回喂原因，参数中的密钥被掩码后放行。

### v2 — MCP 代理模式（通用性跃迁）

- [ ] 网关作为 MCP server（Spring AI MCP server starter），暴露的工具 = 真实工具的包装
- [ ] MCP client / RestClient 转发真实工具调用；流式响应走 Spring Cloud Gateway
- [ ] 响应侧脱敏启用：`decideOnResult()` 对工具返回内容过 PII/exfiltration 检测
- [ ] Hook 适配器保留，与 MCP 适配器共存，共用同一 PolicyEngine
- [ ] L2 语义意图判断接入（小模型分类，策略规则可声明"对该工具启用语义判断"）

**验收标准**：任意支持 MCP 的 Agent（Claude Code、Cursor、自研 Agent）零改造接入，获得同样的策略管控。

### v3 — 研究深化

- [ ] L3 上下文感知：会话内风险链检测（读敏感数据 → 外发）
- [ ] 策略反馈闭环实验：统计 deny 后模型的行为改换路径，评估策略的可绕过性
- [ ] 策略推荐：基于审计日志半自动生成策略
- [ ] SDK 中间件模式（Java/Python），覆盖不走 MCP 的自研 Agent

## 4. 策略 DSL（v1 草案，YAML 声明式）

```yaml
agents:
  - id: claude-code-dev          # Agent 身份，绑定 API key
    policies:
      - tool: "Bash"
        rules:
          - field: "$.command"          # JSONPath 定位参数
            deny: "\\brm\\s+-rf\\b|sudo"   # 正则 → DENY
            reason: "禁止破坏性/提权命令"
          - field: "$.command"
            allow: "^ls |^cat |^git "       # 白名单模式：仅允许匹配项
      - tool: "Read"
        rules:
          - field: "$.file_path"
            deny: "(\\.env|id_rsa|credentials)"   # 敏感文件
      - tool: "WebFetch"
        rules:
          - field: "$.url"
            allow_domain: "internal\\.corp\\.com"  # 边界控制：域名白名单
      - tool: "*"
        sanitize:                    # 脱敏：命中则 MODIFY
          - pattern: "sk-[A-Za-z0-9]{20,}"   # API key
            replace: "sk-***REDACTED***"
          - pattern: "\\b\\d{17}[\\dXx]\\b"  # 身份证号
            replace: "[ID_REDACTED]"
```

要点：
- 策略按 Agent 身份隔离；支持 `tool: "*"` 全局规则
- 规则类型：`deny` / `allow`（白名单）/ `allow_domain`（边界）/ `sanitize`（脱敏→MODIFY）
- L2 阶段在此 DSL 上扩展 `semantic: {intent: "destructive", action: deny}` 之类声明

## 5. 技术栈

| 组件 | 选型 | 说明 |
|---|---|---|
| 网关框架 | Spring Boot 4.1 (webmvc) | 已有骨架 |
| 路由转发（v2） | Spring Cloud Gateway (webmvc) | pom 已预留 |
| 策略匹配 | 自研（JSONPath + 正则） | v1 轻量实现 |
| 审计存储 | 先文件（JSONL），后可换 SQLite/Postgres | 不早期过度设计 |
| MCP（v2） | Spring AI MCP server/client starter | |
| 语义判断（v2+） | Claude API（Haiku 级小模型做意图分类） | |

## 6. 目录结构规划

```
com.chika.agent.agenttoolgateway
├── api/            # REST 接入层：ToolCallCheckController
├── core/           # 决策核心：ToolCallContext, Verdict, PolicyEngine
├── policy/         # 策略加载与匹配：YAML 加载、JSONPath/正则规则
├── sanitize/       # 脱敏引擎
├── audit/          # 审计日志
├── auth/           # 身份鉴权（API key → Agent 身份）
└── mcp/            # v2: MCP 代理适配器（暂空）
```

## 7. 风险与开放问题

- **Hook 模式的信任边界**：Hook 在 Agent 本地，理论上可被绕过（Agent 配置可改）。研究场景可接受；生产场景靠 MCP 代理模式解决（网关持有工具凭据，Agent 拿不到真实工具）。
- **语义判断的延迟与成本**：L2 每次调用过模型会增加延迟，需要缓存/降级策略（规则先行，规则模糊时才升级到模型）。
- **策略 DSL 表达力边界**：正则难以表达复杂语义，这是 L1→L2 的演进动力，也是研究点本身。
