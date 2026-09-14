#!/bin/bash
B=http://localhost:8080/api/v1
j() { jq -r "$1"; }
T_TENANT=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"xiaochen","password":"123456"}' | j .data.token)
T_WANG=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"wanglandlord","password":"123456"}' | j .data.token)
T_LI=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"lilandlord","password":"123456"}' | j .data.token)
T_ADMIN=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' | j .data.token)

echo "1) 未实名房东发布 → 应报 1008"
curl -s -X POST $B/houses -H "Authorization: Bearer $T_WANG" -H 'Content-Type: application/json' -d '{"title":"测试","community":"测试小区","city":"大连市","district":"甘井子区","address":"测试路1号","layout":"1室1厅","area":50,"rent":2000,"depositType":"押一付三","lng":121.5,"lat":38.9}' | j '"code=\(.code) msg=\(.message)"'

echo "2) 已实名房东发布 → code=0, status=0"
HID=$(curl -s -X POST $B/houses -H "Authorization: Bearer $T_LI" -H 'Content-Type: application/json' -d '{"title":"冒烟测试房源 近地铁精装","community":"软件园公寓","city":"大连市","district":"高新园区","address":"黄浦路50号","layout":"1室1厅","area":45,"rent":2100,"depositType":"押一付三","facilities":["近地铁","精装修"],"description":"冒烟测试用房源，近软件园地铁站，精装修家电齐全。","lng":121.54,"lat":38.85}' | j .data.id)
echo "  houseId=$HID"

echo "3) 管理员审核通过 + 房东上架"
curl -s -X PATCH $B/admin/houses/$HID/audit -H "Authorization: Bearer $T_ADMIN" -H 'Content-Type: application/json' -d '{"pass":true,"reason":""}' | j '"audit code=\(.code)"'
curl -s -X PATCH $B/houses/$HID/status -H "Authorization: Bearer $T_LI" -H 'Content-Type: application/json' -d '{"action":"online"}' | j '"online code=\(.code)"'

echo "4) 预约：正常创建 + 同时段重复 → 应报 3001"
APPT_TIME=$(date -d '+2 day 10:00' '+%Y-%m-%dT10:00:00')
curl -s -X POST $B/appointments -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"appointmentTime\":\"$APPT_TIME\",\"remark\":\"想看看房\"}" | j '"create code=\(.code)"'
curl -s -X POST $B/appointments -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"appointmentTime\":\"$APPT_TIME\"}" | j '"dup code=\(.code) msg=\(.message)"'

echo "5) 签约全链路：生成→租客签→房东签→订单+账单"
SD=$(date -d '+1 day' '+%F'); ED=$(date -d '+13 month' '+%F')
CID=$(curl -s -X POST $B/contracts -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"startDate\":\"$SD\",\"endDate\":\"$ED\"}" | j .data.id)
echo "  contractId=$CID"
curl -s -X PATCH $B/contracts/$CID -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' -d '{"action":"sign"}' | j '"tenant sign → status=\(.data.status)"'
SIGN=$(curl -s -X PATCH $B/contracts/$CID -H "Authorization: Bearer $T_LI" -H 'Content-Type: application/json' -d '{"action":"sign"}')
echo "  landlord sign → status=$(echo $SIGN | j .data.status), orderId=$(echo $SIGN | j .data.orderId)"
OID=$(echo $SIGN | j .data.orderId)
echo "  账单期数: $(curl -s $B/orders/$OID/bills -H "Authorization: Bearer $T_TENANT" | j '.data | length')，房源状态: $(curl -s $B/houses/$HID | j .data.house.status)"

echo "6) 在租订单评价 → 应报 3005"
curl -s -X POST $B/reviews -H "Authorization: Bearer $T_TENANT" -H 'Content-Type: application/json' -d "{\"leaseOrderId\":$OID,\"houseScore\":5,\"landlordScore\":5,\"content\":\"很好\"}" | j '"code=\(.code) msg=\(.message)"'

echo "7) AI 定价建议（房东）+ 虚假检测（管理员）"
curl -s -X POST $B/ai/houses/$HID/pricing-suggestion -H "Authorization: Bearer $T_LI" | j '"定价: \(.data.low)-\(.data.high) n=\(.data.sampleCount) (\(.data.model))"'
curl -s -X POST $B/admin/houses/$HID/fake-detect -H "Authorization: Bearer $T_ADMIN" | j '"检测: risk=\(.data.riskScore) (\(.data.suggestion))"'

echo "8) 通知与看板"
echo "  房东未读: $(curl -s $B/notifications/unread-count -H "Authorization: Bearer $T_LI" | j .data)"
curl -s "$B/admin/dashboard?granularity=day" -H "Authorization: Bearer $T_ADMIN" | j '"看板: 用户=\(.data.userCount) 房源=\(.data.houseCount) 在租=\(.data.rentedCount) 订单=\(.data.orderCount)"'
