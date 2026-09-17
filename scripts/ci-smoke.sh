#!/usr/bin/env bash
#
# RentAgent 集成冒烟断言脚本（CI 门禁用）
#
# 与 scripts/smoke.sh 的区别：本脚本对每一步做退出码断言（失败累计，最终 exit 1），
# 可直接作为流水线的集成测试门禁；smoke.sh 是人工排障用的打印版，两者流程一致。
#
# 前置：真实 MySQL 8（已执行 docker/mysql-init/01_schema.sql，空库由应用 DataInitializer 自动灌种子数据）
#       + Redis + 运行中的后端。不配置 AI Key 时后端自动降级规则引擎，本脚本按规则引擎断言。
# 依赖：curl、jq、GNU date（date -d）
# 环境变量：BASE_URL（默认 http://localhost:8080）
#
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}/api/v1"
PASS=0
FAIL=0

trap 'printf "\033[31m冒烟脚本中断：后端可能未就绪或接口异常，请检查应用日志\033[0m\n"' ERR

# ── 输出与断言工具 ──────────────────────────────────────────────
say() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
no() { printf '  \033[31m✗\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }

# expect_eq <说明> <实际值> <期望值>
expect_eq() {
  if [[ "$2" == "$3" ]]; then ok "$1（$3）"; else no "$1：期望 $3，实际 $2"; fi
}

# expect_true <说明> <0|1>
expect_true() {
  if [[ "$2" == "0" ]]; then ok "$1"; else no "$1"; fi
}

# expect_match <说明> <文本> <正则>
expect_match() {
  if [[ "$2" =~ $3 ]]; then ok "$1"; else no "$1：实际 $2"; fi
}

# expect_ge <说明> <数值> <下限>
expect_ge() {
  if [[ "$2" =~ ^-?[0-9]+$ ]] && (($2 >= $3)); then ok "$1（$2 ≥ $3）"; else no "$1：期望 ≥ $3，实际 $2"; fi
}

code_of() { jq -r '.code // "无响应体"' <<<"$1"; }
data_of() { jq -r '.data // empty' <<<"$1"; }
login() { req POST /auth/login - "$(jq -nc --arg u "$1" '{username:$u,password:"123456"}')"; }

# req <方法> <路径> <token|-> [JSON 体]
req() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  # -m：模型类接口单次调用数秒到数十秒，给 60s 安全网，避免脚本卡死
  local args=(-sS -m 60 -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [[ "$token" != "-" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

# req_q <路径> <token|-> <k=v>... ：查询串含中文时交由 curl 做 URL 编码
req_q() {
  local path="$1" token="$2"
  shift 2
  local args=(-sS -G "$BASE$path")
  [[ "$token" != "-" ]] && args+=(-H "Authorization: Bearer $token")
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  curl "${args[@]}"
}

command -v jq >/dev/null || { echo "缺少 jq"; exit 2; }
date -d '+1 day' +%F >/dev/null 2>&1 || { echo "date 需支持 -d（GNU coreutils）"; exit 2; }

# 打印结论并按门禁约定退出（失败 1）
finish() {
  printf '\n\033[1m集成冒烟结果：通过 %d 条，失败 %d 条\033[0m\n' "$PASS" "$FAIL"
  if ((FAIL > 0)); then
    printf '\033[31m存在失败断言，流水线判定不通过\033[0m\n'
    exit 1
  fi
  printf '\033[32m全部断言通过\033[0m\n'
}

# 关键前置值守卫：缺失说明上游步骤已失败，立即给结论，避免后续 jq 抛解析错误掩盖真实原因
require_id() { # require_id <说明> <值>
  if [[ -z "$2" || "$2" == "null" ]]; then
    no "$1 未取得，后续依赖用例无法执行（原因见上方失败断言）"
    finish
  fi
}

# 等待后端完成演示种子写入（本地与 CI 通用）。
# 必要性：DataInitializer 是 CommandLineRunner + @Transactional，Web 端口与数据库连接池都先于种子提交就绪，
# 空库首启时存在「接口能响应、但账号尚未落库」的窗口（本机实测约 0.6s，CI 冷容器下可达数秒）。
# 此时登录会拿到 1002 并产生空 token，后续用例连锁失败——所以门禁必须先等数据。
# 判据用不鉴权的 /houses（查库）：demo 库为空即视为未就绪。
wait_for_seed() {
  local tries="${SEED_WAIT_TRIES:-45}" total i
  printf '  等待后端与演示数据就绪（空库首启需先灌种子数据）'
  for ((i = 1; i <= tries; i++)); do
    total=$(curl -sS -m 5 "$BASE/houses?size=1" 2>/dev/null | jq -r '.data.total // empty' 2>/dev/null || true)
    if [[ "$total" =~ ^[0-9]+$ ]] && ((total >= 1)); then
      printf '\r\033[32m✓\033[0m 后端与演示数据就绪（第 %d 次探测，演示房源 %s 套）\n' "$i" "$total"
      return 0
    fi
    printf '.'
    sleep 2
  done
  printf '\n\033[31m✗\033[0m 等待演示数据超时（%d 秒内 /houses 仍为空）\n' "$((tries * 2))"
  printf '  排查：数据库须为空库且已执行 docker/mysql-init/01_schema.sql；后端日志应出现「演示种子数据写入完成」。\n'
  exit 2
}

# 大模型接入前置检查（本系统不做任何模拟/规则兜底：没接上真实模型就不该跑 AI 用例）
wait_for_model() {
  local engine code
  engine=$(curl -sS -m 15 "$BASE/ai/engine" 2>/dev/null | jq -r '.data.engine // empty' 2>/dev/null || true)
  if [[ -n "$engine" && "$engine" != "none" ]]; then
    printf '  \033[32m✓\033[0m 大模型通道就绪（%s）\n' "$engine"
    return 0
  fi
  code=$(curl -sS -m 15 "$BASE/ai/engine" 2>/dev/null | jq -r '.code // empty' 2>/dev/null || true)
  printf '\n\033[31m✗\033[0m 未接入可用大模型（GET /ai/engine → code=%s）\n' "${code:-无响应}"
  printf '  本系统已移除全部模拟实现：AI 对话与四项 AI 分析必须接真实模型，缺模型即报 4001。\n'
  printf '  本地：用 server/run-dev.sh 启动，或自行 export AI_API_KEY / AGGREGATOR_API_KEY 等凭据。\n'
  printf '  CI：仓库 Settings → Secrets and variables → Actions 配置 AGGREGATOR_API_KEY\n'
  printf '      （模型与端点可选：AGGREGATOR_MODEL / AGGREGATOR_BASE_URL / AI_BACKEND）。\n'
  exit 2
}

wait_for_seed
wait_for_model

# ── 1. 登录与鉴权基线 ──────────────────────────────────────────
say "1. 登录与鉴权基线"
T_TENANT=$(login xiaochen | jq -r '.data.token // empty')
T_WANG=$(login wanglandlord | jq -r '.data.token // empty')
T_LI=$(login lilandlord | jq -r '.data.token // empty')
T_ADMIN=$(login admin | jq -r '.data.token // empty')
for pair in "租客小陈:$T_TENANT" "未实名房东:$T_WANG" "已实名房东:$T_LI" "管理员:$T_ADMIN"; do
  expect_true "${pair%%:*}登录成功并签发 token" "$([[ -n "${pair##*:}" ]] && echo 0 || echo 1)"
done
expect_eq "禁用账号 historyuser 登录被拒" "$(code_of "$(login historyuser)")" "1004"
# 用户名每次运行唯一：避免在同一 Redis 上重复运行触发「5 次失败锁定」（1003）而掩盖本断言
expect_eq "密码错误与用户不存在返回同一错误码" "$(code_of "$(login "nobody-$RANDOM$RANDOM")" || true)" "1002"

HTTP_BAD=$(curl -s -o /tmp/ci_smoke_badtoken.json -w '%{http_code}' "$BASE/users/me" -H 'Authorization: Bearer not-a-jwt')
expect_eq "非法 token 返回 HTTP 401" "$HTTP_BAD" "401"
expect_eq "非法 token 响应体错误码" "$(code_of "$(cat /tmp/ci_smoke_badtoken.json)")" "1002"

expect_eq "未登录访问需登录接口（无角色注解，靠上下文兜底）" "$(code_of "$(req GET /landlord/houses -)")" "1007"
expect_eq "未登录访问管理员接口被角色拦截器拒绝" "$(code_of "$(req GET /admin/dashboard -)")" "1002"
expect_eq "租客越权访问后台审计" "$(code_of "$(req GET /admin/chats "$T_TENANT")")" "1007"

# ── 2. FR-05/07 房源发布与审核（实名门禁 + 状态机） ─────────────
say "2. FR-05/07 房源发布与审核"
expect_eq "未实名房东发布房源被拒" "$(code_of "$(req POST /houses "$T_WANG" \
  "$(jq -nc '{title:"冒烟测试房源",community:"凌水小镇",city:"大连市",district:"甘井子区",address:"测试路 1 号",layout:"1室1厅",area:50,rent:2200,depositType:"押一付三",lng:121.52,lat:38.87}')")")" "1008"

expect_eq "面积非法被参数校验拦截" "$(code_of "$(req POST /houses "$T_LI" \
  "$(jq -nc '{title:"非法房源",community:"凌水小镇",city:"大连市",district:"甘井子区",address:"测试路 2 号",layout:"1室1厅",area:0.5,rent:2200,depositType:"押一付三",lng:121.52,lat:38.87}')")")" "1000"

HOUSE_RESP=$(req POST /houses "$T_LI" "$(jq -nc '{title:"冒烟测试房源 近地铁精装一居",community:"凌水小镇",city:"大连市",district:"甘井子区",address:"黄浦路 50 号",layout:"1室1厅",area:45,rent:2100,depositType:"押一付三",facilities:["近地铁","精装修"],description:"冒烟与集成测试专用房源，近软件园地铁站，精装修家电齐全，周边配套成熟，适合上班族长期租住。",lng:121.5268,lat:38.8718,images:[]}')")
HID=$(data_of "$HOUSE_RESP" | jq -r '.id // empty')
require_id "发布房源返回的 houseId" "$HID"
expect_eq "已实名房东发布房源" "$(code_of "$HOUSE_RESP")" "0"
expect_eq "新发布房源初始为待审核" "$(data_of "$HOUSE_RESP" | jq -r '.status')" "0"
expect_eq "待审核房源对匿名访客不可见" "$(code_of "$(req GET "/houses/$HID" -)")" "2001"
expect_eq "非管理员不可审核房源" "$(code_of "$(req PATCH "/admin/houses/$HID/audit" "$T_LI" '{"pass":true,"reason":""}')")" "1007"
expect_eq "管理员审核通过" "$(code_of "$(req PATCH "/admin/houses/$HID/audit" "$T_ADMIN" '{"pass":true,"reason":""}')")" "0"
expect_eq "已通过但未上架，匿名仍不可见" "$(code_of "$(req GET "/houses/$HID" -)")" "2001"
expect_eq "房东上架（已通过 → 已上架）" "$(code_of "$(req PATCH "/houses/$HID/status" "$T_LI" '{"action":"online"}')")" "0"
expect_eq "重复上架被状态机拒绝" "$(code_of "$(req PATCH "/houses/$HID/status" "$T_LI" '{"action":"online"}')")" "2002"
expect_eq "非房主不可上下架他人房源" "$(code_of "$(req PATCH "/houses/$HID/status" "$T_WANG" '{"action":"offline"}')")" "1007"
expect_eq "上架后匿名可见详情" "$(code_of "$(req GET "/houses/$HID" -)")" "0"

# ── 3. FR-09/10 检索、地图与 FR-25 收藏 ─────────────────────────
say "3. FR-09/10 检索、地图与 FR-25 收藏"
SEARCH=$(req_q /houses - "district=高新园区" "rentMax=2500" "layout=1室" "sort=rent_asc" "size=20")
expect_eq "多条件筛选（行政区 + 租金上限 + 户型前缀 + 租金升序）" "$(code_of "$SEARCH")" "0"
expect_true "FR-09 筛选结果完全匹配条件" \
  "$(data_of "$SEARCH" | jq -r 'if (.list | length) > 0
      and ([.list[] | select(.house.district != "高新园区" or (.house.rent | tonumber) > 2500 or (.house.layout | startswith("1室") | not))] | length) == 0
      then 0 else 1 end')"
expect_true "FR-09 按租金升序返回" \
  "$(data_of "$SEARCH" | jq -r 'if ([.list[].house.rent | tonumber]) as $r | $r == ($r | sort) then 0 else 1 end')"
expect_true "FR-09 分页 size 上限生效" \
  "$(data_of "$SEARCH" | jq -r 'if (.list | length) <= 20 and (.total >= (.list | length)) then 0 else 1 end')"

KEYWORD=$(req_q /houses - "keyword=凌水小镇" "size=20")
expect_true "FR-09 关键词命中标题/小区/地址" \
  "$(data_of "$KEYWORD" | jq -r 'if (.list | length) > 0
      and ([.list[] | select((.house.community + .house.title + .house.address) | contains("凌水小镇") | not)] | length) == 0
      then 0 else 1 end')"

FACILITY=$(req_q /houses - "facilities=近地铁" "size=20")
expect_true "FR-09 设施筛选（JSON_CONTAINS）结果匹配" \
  "$(data_of "$FACILITY" | jq -r 'if (.list | length) > 0
      and ([.list[] | select(if (.house.facilities | type) == "string"
                then ((.house.facilities | fromjson | index("近地铁")) == null)
                else ((.house.facilities | index("近地铁")) == null) end)] | length) == 0
      then 0 else 1 end')"

MAP=$(req_q /houses/map - "district=高新园区")
expect_true "FR-10 地图找房返回带坐标的房源列表" \
  "$(data_of "$MAP" | jq -r 'if length > 0 and ([.[] | select(.lng == null or .lat == null)] | length) == 0 then 0 else 1 end')"

expect_eq "FR-25 收藏房源" "$(code_of "$(req POST "/favorites/$HID" "$T_TENANT")")" "0"
expect_eq "FR-25 重复收藏幂等成功" "$(code_of "$(req POST "/favorites/$HID" "$T_TENANT")")" "0"
expect_ge "FR-25 收藏列表可见" "$(req GET /favorites "$T_TENANT" | jq -r '.data.total')" "1"
expect_eq "FR-25 取消收藏" "$(code_of "$(req DELETE "/favorites/$HID" "$T_TENANT")")" "0"
expect_eq "FR-25 取消后收藏列表不含该房源" \
  "$(req GET /favorites "$T_TENANT" | jq -r --argjson h "$HID" '[.data.list[] | select(.id == $h)] | length')" "0"

# ── 3. FR-17 看房预约（时段唯一 + 权限/状态机） ─────────────────
say "4. FR-17 看房预约"
APPT_TIME=$(date -d "+$((RANDOM % 20 + 2)) day +$((RANDOM % 600)) minute" '+%Y-%m-%dT%H:%M:00')
APPT_RESP=$(req POST /appointments "$T_TENANT" "$(jq -nc --argjson h "$HID" --arg t "$APPT_TIME" '{houseId:$h,appointmentTime:$t,remark:"想看看房"}')")
APPT_ID=$(data_of "$APPT_RESP" | jq -r '.id // empty')
require_id "创建预约返回的 appointmentId" "$APPT_ID"
expect_eq "租客创建预约" "$(code_of "$APPT_RESP")" "0"
expect_eq "预约初始为待确认" "$(data_of "$APPT_RESP" | jq -r '.status')" "0"
expect_eq "同房源同时段重复预约冲突" "$(code_of "$(req POST /appointments "$T_TENANT" "$(jq -nc --argjson h "$HID" --arg t "$APPT_TIME" '{houseId:$h,appointmentTime:$t}')")")" "3001"
expect_eq "不能预约自己的房源" "$(code_of "$(req POST /appointments "$T_LI" "$(jq -nc --argjson h "$HID" --arg t "$APPT_TIME" '{houseId:$h,appointmentTime:$t}')")")" "1000"
expect_eq "非房东不可确认预约" "$(code_of "$(req PATCH "/appointments/$APPT_ID" "$T_WANG" '{"action":"confirm"}')")" "1007"
expect_eq "房东确认预约" "$(code_of "$(req PATCH "/appointments/$APPT_ID" "$T_LI" '{"action":"confirm"}')")" "0"
expect_eq "已确认预约不可重复确认" "$(code_of "$(req PATCH "/appointments/$APPT_ID" "$T_LI" '{"action":"confirm"}')")" "3002"
expect_eq "已确认预约可取消" "$(code_of "$(req PATCH "/appointments/$APPT_ID" "$T_TENANT" '{"action":"cancel"}')")" "0"
expect_eq "已取消预约不可再确认" "$(code_of "$(req PATCH "/appointments/$APPT_ID" "$T_LI" '{"action":"confirm"}')")" "3002"

# ── 4. FR-18/19 在线签约 → 订单 → 账单 ─────────────────────────
say "5. FR-18/19 签约、订单与账单"
SD=$(date -d '+1 day' '+%F')
ED=$(date -d '+1 day +12 month' '+%F')
BODY_C=$(jq -nc --argjson h "$HID" --arg s "$SD" --arg e "$ED" '{houseId:$h,startDate:$s,endDate:$e}')
expect_eq "租期非法（起止相同）被拒" "$(code_of "$(req POST /contracts "$T_TENANT" "$(jq -nc --argjson h "$HID" --arg s "$SD" '{houseId:$h,startDate:$s,endDate:$s}')")")" "1000"

CONTRACT_RESP=$(req POST /contracts "$T_TENANT" "$BODY_C")
CID=$(data_of "$CONTRACT_RESP" | jq -r '.id // empty')
require_id "创建合同返回的 contractId" "$CID"
expect_eq "租客发起签约" "$(code_of "$CONTRACT_RESP")" "0"
expect_eq "合同初始为待租客确认" "$(data_of "$CONTRACT_RESP" | jq -r '.status')" "0"
expect_eq "同房源同租客不可重复发起" "$(code_of "$(req POST /contracts "$T_TENANT" "$BODY_C")")" "3003"
expect_eq "房东不可抢先签署（须租客先签）" "$(code_of "$(req PATCH "/contracts/$CID" "$T_LI" '{"action":"sign"}')")" "3003"
expect_eq "租客签署后待房东确认" "$(req PATCH "/contracts/$CID" "$T_TENANT" '{"action":"sign"}' | jq -r '.data.status')" "1"

SIGN=$(req PATCH "/contracts/$CID" "$T_LI" '{"action":"sign"}')
OID=$(data_of "$SIGN" | jq -r '.orderId // empty')
require_id "合同生效派生订单 orderId" "$OID"
expect_eq "房东签署后合同生效" "$(data_of "$SIGN" | jq -r '.status')" "2"
expect_true "合同生效派生订单 id" "$([[ -n "$OID" && "$OID" != "null" ]] && echo 0 || echo 1)"
expect_eq "生效后房源置为已出租" "$(req GET "/houses/$HID" - | jq -r '.data.house.status')" "5"
expect_eq "已生效合同不可再次签署" "$(code_of "$(req PATCH "/contracts/$CID" "$T_LI" '{"action":"sign"}')")" "3003"

BILLS=$(req GET "/orders/$OID/bills" "$T_TENANT")
expect_eq "账单期数 = 租期整月数（起租日至 +12 月为 12 期）" "$(data_of "$BILLS" | jq -r 'length')" "12"
expect_eq "首期账单期为 1" "$(data_of "$BILLS" | jq -r '.[0].periodNo')" "1"
expect_eq "首期账单到期日为起租日" \
  "$(data_of "$BILLS" | jq -r --arg sd "$SD" '.[0].dueDate | tostring | if startswith($sd) then "ok" else . end')" "ok"
expect_eq "首期账单金额等于月租金" \
  "$(data_of "$BILLS" | jq -r 'if (.[0].amount | tonumber) == 2100 then "ok" else (.[0].amount | tostring) end')" "ok"
expect_eq "非订单相关方不可查看账单" "$(code_of "$(req GET "/orders/$OID/bills" "$T_WANG")")" "3004"
expect_eq "房东（非承租人）不可支付账单" "$(code_of "$(req PATCH "/bills/$(data_of "$BILLS" | jq -r '.[0].id')/pay" "$T_LI")")" "1007"

BILL1=$(data_of "$BILLS" | jq -r '.[0].id')
require_id "首期账单 id" "$BILL1"
expect_eq "租客支付首期账单" "$(code_of "$(req PATCH "/bills/$BILL1/pay" "$T_TENANT")")" "0"
expect_eq "已支付账单不可重复支付" "$(code_of "$(req PATCH "/bills/$BILL1/pay" "$T_TENANT")")" "1000"

# ── 5. FR-20 评价门禁（在租不可评 → 退租后可评 → 一单一评） ─────
say "6. FR-20 评价门禁与退租"
REVIEW_BODY=$(jq -nc --argjson o "$OID" '{leaseOrderId:$o,houseScore:5,landlordScore:5,content:"房东很好沟通，房源与描述一致"}')
expect_eq "在租中订单不可评价" "$(code_of "$(req POST /reviews "$T_TENANT" "$REVIEW_BODY")")" "3005"
expect_eq "评分越界被参数校验拦截" "$(code_of "$(req POST /reviews "$T_TENANT" "$(jq -nc --argjson o "$OID" '{leaseOrderId:$o,houseScore:6,landlordScore:5}')")")" "1000"
expect_eq "租客退租" "$(code_of "$(req PATCH "/contracts/$CID" "$T_TENANT" '{"action":"terminate"}')")" "0"
expect_eq "退租后合同状态为已退租" "$(req GET "/contracts/$CID" "$T_TENANT" | jq -r '.data.status')" "3"
expect_eq "退租后房源重新上架" "$(req GET "/houses/$HID" - | jq -r '.data.house.status')" "3"
expect_eq "已退租订单可评价" "$(code_of "$(req POST /reviews "$T_TENANT" "$REVIEW_BODY")")" "0"
expect_eq "同一订单不可重复评价" "$(code_of "$(req POST /reviews "$T_TENANT" "$REVIEW_BODY")")" "3005"
expect_ge "房源评价列表回填" "$(req GET "/houses/$HID/reviews" - | jq -r '.data.total')" "1"

# ── 6. FR-11/14/15/16 AI 与推荐（规则引擎） ─────────────────────
say "7. FR-11 推荐与 FR-08/14/15/16 AI 能力（真实模型）"
REC1=$(req GET '/recommendations?limit=10' "$T_TENANT" | jq -c '[.data[].id]')
REC2=$(req GET '/recommendations?limit=10' "$T_TENANT" | jq -c '[.data[].id]')
expect_true "FR-11 推荐位返回房源" "$([[ "$REC1" != "[]" ]] && echo 0 || echo 1)"
expect_eq "FR-11 同一用户 24 小时内推荐列表前 10 项重合（验收口径 ≥60%）" "$REC1" "$REC2"

expect_match "AI 引擎标识为真实模型（<协议>:<模型>）" \
  "$(req GET /ai/engine - | jq -r '.data.engine')" '^[a-z0-9-]+:.+$'

INTERP=$(req POST "/contracts/$CID/interpret" "$T_TENANT")
expect_eq "FR-14 合同智能解读（真模型）" "$(code_of "$INTERP")" "0"
expect_ge "FR-14 逐条解读条款数" "$(data_of "$INTERP" | jq -r '.items | length')" "5"
expect_ge "FR-14 风险条款命中数（示范合同含押金不退/违约金等样例）" \
  "$(data_of "$INTERP" | jq -r '[.items[] | select(.risk)] | length')" "1"
expect_true "FR-14 每条解读都有通俗解释且标注风险布尔值" \
  "$(data_of "$INTERP" | jq -r 'if ([.items[] | select((.explanation | length) < 10 or (.risk | type) != "boolean")] | length) == 0 then 0 else 1 end')"
expect_true "FR-14/NFR-05 解读附带免责声明" \
  "$(data_of "$INTERP" | jq -r 'if .disclaimer | test("仅供参考") then 0 else 1 end')"
expect_match "FR-14 解读结果标注真实引擎" "$(data_of "$INTERP" | jq -r '.model')" '^[a-z0-9-]+:.+$'
expect_eq "FR-14 非合同双方不可解读" "$(code_of "$(req POST "/contracts/$CID/interpret" "$T_WANG")")" "1007"

PRICING=$(req POST "/ai/houses/$HID/pricing-suggestion" "$T_LI")
expect_eq "FR-15 房东获取智能定价建议（真模型）" "$(code_of "$PRICING")" "0"
expect_ge "FR-15 定价样本量（同小区不足 3 条时回退同区域同户型）" "$(data_of "$PRICING" | jq -r '.sampleCount')" "1"
expect_true "FR-15 区间与依据均返回，且 low ≤ high" \
  "$(data_of "$PRICING" | jq -r 'if (.low | type == "number") and (.high | type == "number")
      and (.low <= .high) and (.basis | length) > 0 then 0 else 1 end')"
expect_match "FR-15 定价结果标注真实引擎" "$(data_of "$PRICING" | jq -r '.model')" '^[a-z0-9-]+:.+$'
expect_eq "FR-15 非房源所有者不可获取定价建议" "$(code_of "$(req POST "/ai/houses/$HID/pricing-suggestion" "$T_WANG")")" "1007"

DETECT=$(req POST "/admin/houses/$HID/fake-detect" "$T_ADMIN")
expect_eq "FR-16 管理员执行虚假房源检测（真模型）" "$(code_of "$DETECT")" "0"
expect_true "FR-16 风险分在 0~100 且有疑点数组与处置建议" \
  "$(data_of "$DETECT" | jq -r 'if (.riskScore | type == "number") and .riskScore >= 0 and .riskScore <= 100
      and (.suspicions | type == "array") and (.suggestion | length) > 0 then 0 else 1 end')"
expect_match "FR-16 检测结果标注真实引擎" "$(data_of "$DETECT" | jq -r '.model')" '^[a-z0-9-]+:.+$'

FILL=$(req POST /ai/assist-fill "$T_LI" '{"title":"近地铁精装高层两居","community":"软件园公寓","layout":"2室1厅","imageFileName":"IMG_0021.jpg"}')
expect_eq "FR-08 房源信息智能识别填充（真模型）" "$(code_of "$FILL")" "0"
expect_true "FR-08 返回描述、朝向、楼层与设施标签" \
  "$(data_of "$FILL" | jq -r 'if (.description | length) >= 20 and (.orientation | length) > 0
      and (.floorDesc | length) > 0 and (.facilities | type == "array") and (.facilities | length) > 0 then 0 else 1 end')"

# ── 7. FR-21 通知与数据看板 ────────────────────────────────────
say "8. FR-21 通知与数据看板"
expect_ge "房东未读通知数（审核/预约/签约/账单/评价触发）" "$(req GET /notifications/unread-count "$T_LI" | jq -r '.data')" "1"
NID=$(req GET '/notifications?onlyUnread=true&size=1' "$T_LI" | jq -r '.data.list[0].id // empty')
require_id "未读通知 id" "$NID"
expect_eq "FR-21 标记通知已读" "$(code_of "$(req PATCH "/notifications/$NID/read" "$T_LI")")" "0"
expect_eq "FR-21 标记他人通知已读被拒" "$(code_of "$(req PATCH "/notifications/$NID/read" "$T_WANG")")" "1007"
DASH=$(req GET '/admin/dashboard?granularity=day' "$T_ADMIN")
expect_eq "管理员读取数据看板" "$(code_of "$DASH")" "0"
expect_true "看板含用户/房源/在租/订单/AI 指标" \
  "$(data_of "$DASH" | jq -r 'if has("userCount") and has("houseCount") and has("rentedCount") and has("orderCount") and has("chatCount") and has("toolCallCount") then 0 else 1 end')"

# ── 8. FR-12/13 AI 对话、解析口径与工具留痕（NFR-05） ───────────
say "9. FR-12/13 AI 对话（真实模型）与工具留痕"
SID=$(req POST /ai/sessions "$T_TENANT" '{"scene":1}' | jq -r '.data.id // empty')
require_id "AI 会话 id" "$SID"
curl -sS -m 180 -N -X POST "$BASE/ai/sessions/$SID/messages" \
  -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' \
  -d "$(jq -nc '{content:"预算2500以内，要一居室，近地铁"}')" >/tmp/ci_smoke_sse.txt
DELTA_HITS=$(grep -c '"delta"' /tmp/ci_smoke_sse.txt || true)
DONE_HITS=$(grep -c '"messageId"' /tmp/ci_smoke_sse.txt || true)
expect_true "SSE 流式消息返回 delta 与 done 事件" \
  "$([[ "$DELTA_HITS" -ge 1 && "$DONE_HITS" -ge 1 ]] && echo 0 || echo 1)"

# 逐字 delta 需按序拼接后才能校验整段回答
REPLY=$(sed -n 's/^data: *//p' /tmp/ci_smoke_sse.txt | jq -r '.delta // empty' 2>/dev/null | tr -d '\n')
expect_true "FR-12 助手返回非空回答（真实模型流式输出）" "$([[ ${#REPLY} -ge 20 ]] && echo 0 || echo 1)"
expect_true "FR-12 回答未走失败兜底文案" \
  "$([[ "$REPLY" == *"智能助手调用失败"* ]] && echo 1 || echo 0)"

TRACE=$(req GET "/admin/chats/$SID" "$T_ADMIN")
expect_eq "管理员查看会话轨迹" "$(code_of "$TRACE")" "0"
expect_eq "会话轮次统计（1 轮用户消息）" "$(data_of "$TRACE" | jq -r '.stats.roundCount')" "1"
expect_ge "工具调用留痕（真实模型调用工具）" "$(data_of "$TRACE" | jq -r '.stats.toolCallCount')" "1"
expect_true "FR-12 模型实际调用了 searchHouses 工具（而非凭空作答）" \
  "$(data_of "$TRACE" | jq -r 'if ([.stats.tools[].name] | index("searchHouses")) != null then 0 else 1 end')"
expect_ge "审计视图全站会话数" "$(req GET '/admin/chats?size=1' "$T_ADMIN" | jq -r '.data.total')" "1"
expect_eq "非会话所有者不可读取会话历史" "$(code_of "$(req GET "/ai/sessions/$SID/history" "$T_WANG")")" "4002"

# FR-13 智能客服：知识库命中附来源（NFR-05），未命中礼貌转人工
SID2=$(req POST /ai/sessions "$T_TENANT" '{"scene":2}' | jq -r '.data.id')
curl -sS -m 180 -N -X POST "$BASE/ai/sessions/$SID2/messages" \
  -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' \
  -d "$(jq -nc '{content:"押金怎么退，什么情况会被扣"}')" >/tmp/ci_smoke_sse_kb.txt
KB_REPLY=$(sed -n 's/^data: *//p' /tmp/ci_smoke_sse_kb.txt | jq -r '.delta // empty' 2>/dev/null | tr -d '\n')
expect_true "FR-13 客服返回非空回答" "$([[ ${#KB_REPLY} -ge 20 ]] && echo 0 || echo 1)"
KB_TRACE=$(req GET "/admin/chats/$SID2" "$T_ADMIN")
expect_true "FR-13 模型实际调用了 searchKnowledge 工具" \
  "$(data_of "$KB_TRACE" | jq -r 'if ([.stats.tools[].name] | index("searchKnowledge")) != null then 0 else 1 end')"
expect_ge "NFR-05 客服回答附带知识库来源引用" \
  "$(sed -n 's/^data: *//p' /tmp/ci_smoke_sse_kb.txt | jq -rs '[.[] | select(.citations != null) | .citations | length] | max // 0' 2>/dev/null | tr -d '\n')" "1"

# 该问法刻意与知识库无 2 字滑窗关键词交集（KbService 按 2 字窗口检索），保证走「未命中转人工」分支
SID3=$(req POST /ai/sessions "$T_TENANT" '{"scene":2}' | jq -r '.data.id')
curl -sS -m 180 -N -X POST "$BASE/ai/sessions/$SID3/messages" \
  -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' \
  -d "$(jq -nc '{content:"比特币可以直接用来交房租吗"}')" >/tmp/ci_smoke_sse_miss.txt
MISS_REPLY=$(sed -n 's/^data: *//p' /tmp/ci_smoke_sse_miss.txt | jq -r '.delta // empty' 2>/dev/null | tr -d '\n')
expect_true "FR-13 知识库未命中时礼貌转人工" "$([[ "$MISS_REPLY" == *"人工客服"* ]] && echo 0 || echo 1)"

# ── 9. FR-03 实名认证（含身份证脱敏回归） ───────────────────────
say "10. FR-03 实名认证与身份证脱敏"
PHONE="199$(printf '%08d' $(( $(date +%s) % 100000000 )))"
expect_eq "获取演示验证码" "$(code_of "$(req POST /auth/captcha - "$(jq -nc --arg p "$PHONE" '{phone:$p}')")")" "0"
REG=$(req POST /auth/register - "$(jq -nc --arg p "$PHONE" '{phone:$p,captcha:"246810",password:"123456",role:2,nickname:"冒烟房东"}')")
T_NEW=$(data_of "$REG" | jq -r '.token // empty')
NEW_UID=$(data_of "$REG" | jq -r '.userId // empty')
require_id "新注册用户 userId" "$NEW_UID"
NEW_PHONE="$PHONE"
expect_eq "注册房东并自动登录" "$(code_of "$REG")" "0"
expect_eq "未提交实名时查询返回空" "$(req GET /users/me/realname "$T_NEW" | jq -c '.data')" "null"
expect_eq "提交实名认证" "$(code_of "$(req POST /users/me/realname "$T_NEW" '{"realName":"李建国","idCardNo":"210102198001011234"}')")" "0"
MASKED=$(req GET /users/me/realname "$T_NEW" | jq -r '.data.maskedIdCard')
expect_match "身份证号脱敏展示（AES-GCM 解密回归）" "$MASKED" '^[0-9]{4}\*{10}[0-9Xx]{4}$'

# ── 11. FR-22/23 用户管理与内容审核 ────────────────────────────
say "11. FR-22/23 用户管理与内容审核"
ADMIN_ID=$(req GET '/admin/users?keyword=admin&size=5' "$T_ADMIN" | jq -r '[.data.list[] | select(.username == "admin")][0].id // empty')
require_id "管理员用户 id" "$ADMIN_ID"
expect_true "FR-22 后台用户检索命中管理员账号" "$([[ -n "$ADMIN_ID" && "$ADMIN_ID" != "null" ]] && echo 0 || echo 1)"
expect_eq "FR-22 不可禁用管理员账号" "$(code_of "$(req PATCH "/admin/users/$ADMIN_ID/status?enabled=false" "$T_ADMIN")")" "1007"
expect_eq "FR-22 禁用普通用户" "$(code_of "$(req PATCH "/admin/users/$NEW_UID/status?enabled=false" "$T_ADMIN")")" "0"
expect_eq "FR-22 禁用后该账号无法登录" "$(code_of "$(login "$NEW_PHONE")" || true)" "1004"
HTTP_DISABLED=$(curl -s -o /tmp/ci_smoke_disabled.json -w '%{http_code}' "$BASE/users/me" -H "Authorization: Bearer $T_NEW")
expect_eq "FR-22 已签发 token 在禁用后立即失效" "$HTTP_DISABLED" "401"
expect_eq "FR-22 禁用不存在的用户" "$(code_of "$(req PATCH '/admin/users/99999999/status?enabled=false' "$T_ADMIN")")" "1006"

REPORT=$(req POST /reports "$T_TENANT" "$(jq -nc --argjson h "$HID" '{targetType:1,targetId:$h,reason:"疑似虚假房源，租金明显偏低"}')")
RID=$(data_of "$REPORT" | jq -r '.id // empty')
require_id "举报 id" "$RID"
expect_eq "FR-23 租客提交举报" "$(code_of "$REPORT")" "0"
expect_ge "FR-23 管理员举报列表含该举报" "$(req GET '/admin/reports?status=-1&size=20' "$T_ADMIN" | jq -r '.data.total')" "1"
expect_eq "FR-23 管理员处理举报" "$(code_of "$(req PATCH "/admin/reports/$RID/handle" "$T_ADMIN" '{"remark":"已核实处理"}')")" "0"
expect_eq "FR-23 已处理举报不可重复处理" "$(code_of "$(req PATCH "/admin/reports/$RID/handle" "$T_ADMIN" '{"remark":"再次处理"}')")" "1000"
expect_eq "FR-23 处理不存在的举报" "$(code_of "$(req PATCH '/admin/reports/99999999/handle' "$T_ADMIN" '{"remark":"x"}')")" "1006"
expect_ge "FR-23 审核留痕（操作日志）" "$(req GET '/admin/audits?size=20' "$T_ADMIN" | jq -r '.data.total')" "1"

# ── 结果 ──────────────────────────────────────────────────────
finish
