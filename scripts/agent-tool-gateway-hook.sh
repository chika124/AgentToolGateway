# Agent 侧接入示例：Claude Code PreToolUse Hook
#
# 安装方法（Claude Code 的 settings.json）：
#   {
#     "hooks": {
#       "PreToolUse": [
#         {
#           "matcher": "*",
#           "hooks": [
#             { "type": "command", "command": "bash /path/to/agent-tool-gateway-hook.sh" }
#           ]
#         }
#       ]
#     }
#   }
#
# 说明：hook 从 stdin 收到 PreToolUse 的 JSON（含 tool_name / tool_input / session_id），
# 调用网关 /v1/toolcalls/check，并把 verdict 映射回 hook 协议：
#   exit 0  → 放行
#   exit 2  → 拒绝（stderr 的 reason 会回喂给模型）
#   MODIFY  → 当前版本按放行处理（Claude Code hook 的 exit 0 + 无操作）
#             如需改写参数，可用 JSON 输出协议输出 updatedInput

GATEWAY="${ATG_URL:-http://localhost:8090}"
API_KEY="${ATG_API_KEY:-dev-local-key-001}"

input=$(cat)
tool_name=$(echo "$input" | jq -r '.tool_name // empty')
session_id=$(echo "$input" | jq -r '.session_id // empty')
tool_input=$(echo "$input" | jq -c '.tool_input // {}')

body=$(jq -n \
  --arg s "$session_id" \
  --arg t "$tool_name" \
  --argjson a "$tool_input" \
  '{sessionId: $s, toolName: $t, toolArgs: $a}')

http_code=$(curl -s -o /tmp/atg_response.json -w "%{http_code}" \
  -X POST "$GATEWAY/v1/toolcalls/check" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d "$body")

if [ "$http_code" != "200" ]; then
  echo "gateway rejected request (http $http_code)" >&2
  exit 2
fi

decision=$(jq -r '.decision' /tmp/atg_response.json)
reason=$(jq -r '.reason // empty' /tmp/atg_response.json)

case "$decision" in
  DENY)
    echo "$reason" >&2
    exit 2
    ;;
  *)
    exit 0
    ;;
esac
