#!/usr/bin/env bash
# AgentToolGateway PreToolUse Hook（Claude Code）
#
# 配置方法（项目 .claude/settings.json 或全局 ~/.claude/settings.json）：
# {
#   "hooks": {
#     "PreToolUse": [
#       { "matcher": "*", "hooks": [
#         { "type": "command",
#           "command": "\"C:\\Program Files\\Git\\usr\\bin\\bash.exe\" \"C:\\Users\\17229\\Desktop\\proj\\AgentToolGateway\\scripts\\agent-tool-gateway-hook.sh\"" }
#       ]}
#     ]
#   }
# }
#
# 协议映射：exit 0 = 放行；exit 2 = 阻止调用，stderr 内容回喂给模型

GATEWAY="${ATG_URL:-http://localhost:8090}"
API_KEY="${ATG_API_KEY:-dev-local-key-001}"

# jq 兼容：PATH 找不到时尝试 winget 安装位置
JQ="$(command -v jq || true)"
[ -z "$JQ" ] && JQ="$LOCALAPPDATA/Microsoft/WinGet/Links/jq.exe"
[ -f "$JQ" ] || JQ="$(ls "$LOCALAPPDATA"/Microsoft/WinGet/Packages/jqlang.jq_*/jq.exe 2>/dev/null | head -1)"

input=$(cat)

tool_name=$("$JQ" -r '.tool_name // empty' <<<"$input")
session_id=$("$JQ" -r '.session_id // empty' <<<"$input")
tool_input=$("$JQ" -c '.tool_input // {}' <<<"$input")

body=$("$JQ" -n \
  --arg s "$session_id" \
  --arg t "$tool_name" \
  --argjson a "$tool_input" \
  '{sessionId: $s, toolName: $t, toolArgs: $a}')

http_code=$(curl -s -m 5 -o /tmp/atg_response.json -w "%{http_code}" \
  -X POST "$GATEWAY/v1/toolcalls/check" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d "$body")

if [ "$http_code" != "200" ]; then
  echo "gateway rejected request (http $http_code)" >&2
  exit 2
fi

decision=$("$JQ" -r '.decision' /tmp/atg_response.json)
reason=$("$JQ" -r '.reason // empty' /tmp/atg_response.json)

case "$decision" in
  DENY)
    echo "$reason" >&2
    exit 2
    ;;
  *)
    exit 0
    ;;
esac
