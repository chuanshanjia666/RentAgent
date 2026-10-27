#!/usr/bin/env bash
#
# 反向门禁：校验「未配置模型时 AI 能力一律硬报错（4001）」
#
# 背景（2026-09-17 用户口径）：系统已移除全部模拟/规则兜底，AI 对话与四项 AI 分析必须接真实模型，
# 没有真实模型就直接报错。本脚本专门验证这条约束：任何一个 AI 接口在无模型时若仍返回正常结果，
# 就说明有兜底实现被重新引入，门禁判定失败（exit 1）。
#
# 用法：在同一套 MySQL/Redis 上另起一个**不注入任何 AI 凭据**的实例（端口与主实例错开）：
#   SERVER_PORT=8081 java -jar server/target/rentagent-server-*.jar &
#   BASE_URL=http://localhost:8081 bash scripts/ci-no-model-check.sh
#
# 依赖：curl、jq
#
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8081}/api/v1"
PASS=0
FAIL=0

say() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
no() { printf '  \033[31m✗\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }

# expect_err <说明> <响应体>：无模型时必须是 4001，若返回 0 说明存在兜底
expect_err() {
  local code
  code=$(jq -r '.code // "无响应体"' <<<"$2")
  if [[ "$code" == "4001" ]]; then
    ok "$1（4001）"
  elif [[ "$code" == "0" ]]; then
    no "$1：无模型却返回成功（code=0），说明存在模拟/兜底实现"
  else
    no "$1：期望 4001，实际 $code"
  fi
}

code_of() { jq -r '.code // "无响应体"' <<<"$1"; }

req() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  local args=(-sS -m 30 -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [[ "$token" != "-" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

login() { req POST /auth/login - "$(jq -nc --arg u "$1" '{username:$u,password:"123456"}')"; }

finish() {
  printf '\n\033[1m无模型硬报错校验：通过 %d 条，失败 %d 条\033[0m\n' "$PASS" "$FAIL"
  if ((FAIL > 0)); then
    printf '\033[31m存在「无模型却有结果」的接口，判定不通过\033[0m\n'
    exit 1
  fi
  printf '\033[32m全部接口在无模型时均如实报错\033[0m\n'
}

command -v jq >/dev/null || { echo "缺少 jq"; exit 2; }

# 等待该实例起监听（本实例无模型，/ai/engine 返回业务错误，HTTP 仍是 200）
for i in $(seq 1 60); do
  if curl -sS -m 3 "$BASE/ai/engine" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

say "无模型实例：AI 能力必须硬报错（4001）"
T_LI=$(login lilandlord | jq -r '.data.token // empty')
if [[ -z "$T_LI" ]]; then
  printf '  \033[31m✗\033[0m 无法登录（种子数据未就绪？），无法继续校验\n'
  exit 2
fi
HID=$(req GET '/landlord/houses?size=1' "$T_LI" | jq -r '.data.list[0].id // empty')

expect_err "GET /ai/engine 报告无可用模型" "$(req GET /ai/engine -)"
expect_err "FR-15 智能定价在无模型时报错" \
  "$(req POST "/ai/houses/${HID:-0}/pricing-suggestion" "$T_LI")"
expect_err "FR-08 智能识别填充在无模型时报错" \
  "$(req POST /ai/assist-fill "$T_LI" '{"title":"近地铁两居","community":"软件园公寓","layout":"2室1厅"}')"

SID=$(req POST /ai/sessions "$T_LI" '{"scene":1}' | jq -r '.data.id // empty')
if [[ -n "$SID" ]]; then
  ok "会话创建本身不依赖模型（可用于前端先建会话、发消息时再报错）"
  expect_err "FR-12 发送消息在无模型时报错（非静默降级）" \
    "$(curl -sS -m 30 -X POST "$BASE/ai/sessions/$SID/messages" \
      -H "Authorization: Bearer $T_LI" -H 'Content-Type: application/json' -d '{"content":"预算2500以内，要一居室"}')"
else
  no "创建会话失败（预期不依赖模型）"
fi

CID=$(req GET '/contracts?size=1' "$T_LI" | jq -r '.data.list[0].id // empty')
if [[ -n "$CID" ]]; then
  expect_err "FR-14 合同解读在无模型时报错" "$(req POST "/contracts/$CID/interpret" "$T_LI")"
else
  printf '  \033[33m—\033[0m FR-14 跳过（该账号暂无合同可解读）\n'
fi

finish
