#!/usr/bin/env bash
# oa-test 开票+认领冒烟（需已执行 phase2a/菜单 SQL 且新 jar 已接管）
# 用法：BASE=https://oa-test.3dmgame.com/admin-api USER=admin PASS=admin123 ./scripts/oa-test-invoice-claim-smoke.sh
set -euo pipefail

BASE="${BASE:-https://oa-test.3dmgame.com/admin-api}"
USER="${USER:-admin}"
PASS="${PASS:-admin123}"
TENANT="${TENANT:-1}"

login() {
  local u="$1" p="$2"
  curl -sS -m 20 -X POST "$BASE/system/auth/login" \
    -H "Content-Type: application/json" -H "tenant-id: $TENANT" \
    -d "{\"username\":\"$u\",\"password\":\"$p\"}" | python3 -c \
    'import sys,json; d=json.load(sys.stdin); assert d.get("code")==0, d; print(d["data"]["accessToken"])'
}

TOKEN=$(login "$USER" "$PASS")
auth=(-H "Authorization: Bearer $TOKEN" -H "tenant-id: $TENANT" -H "Content-Type: application/json")

echo "[1] invoice page"
code=$(curl -sS -m 20 -o /tmp/sm1.json -w '%{http_code}' \
  "${auth[@]}" "$BASE/finance/invoice-application/page?pageNo=1&pageSize=5")
body=$(cat /tmp/sm1.json)
echo "HTTP $code $body" | head -c 300; echo
python3 - <<'PY'
import json
d=json.load(open('/tmp/sm1.json'))
if d.get('code')==404 or '不存在' in str(d.get('msg','')):
    raise SystemExit('FAIL: invoice API 404 — 新包未部署')
if d.get('code')!=0:
    raise SystemExit(f"FAIL: invoice page {d}")
print('PASS invoice page')
PY

echo "[2] claim source invoice page"
curl -sS -m 20 -o /tmp/sm2.json "${auth[@]}" \
  "$BASE/finance/receipt-claim/source-invoice-application-page?pageNo=1&pageSize=5"
python3 - <<'PY'
import json
d=json.load(open('/tmp/sm2.json'))
if d.get('code')!=0:
    raise SystemExit(f"FAIL: source invoice {d}")
print('PASS source-invoice-application-page')
PY

echo "[3] createAndStart requires BO data — listing BO"
curl -sS -m 20 -o /tmp/sm3.json "${auth[@]}" \
  "$BASE/finance/business-order/page?pageNo=1&pageSize=3"
python3 - <<'PY'
import json
d=json.load(open('/tmp/sm3.json'))
assert d.get('code')==0, d
lst=d['data']['list']
print('BO count', d['data']['total'])
if not lst:
    raise SystemExit('SKIP createAndStart: no business order')
open('/tmp/sm_bo_id','w').write(str(lst[0]['id']))
print('use BO', lst[0]['id'], lst[0].get('orderNo'))
PY

BO_ID=$(cat /tmp/sm_bo_id)
echo "[3b] create customer company (购方档案)"
TAX="SM$(date +%s | tail -c 10)"
curl -sS -m 20 -o /tmp/sm3b.json -X POST "${auth[@]}" \
  "$BASE/finance/customer-company/create" \
  -d "{\"name\":\"冒烟购方\",\"taxNo\":\"$TAX\"}"
python3 - <<'PY'
import json
d=json.load(open('/tmp/sm3b.json'))
if d.get('code')!=0:
    raise SystemExit(f"FAIL create customer company: {d}")
open('/tmp/sm_cc_id','w').write(str(d['data']))
print('PASS customerCompanyId=', d.get('data'))
PY
CC_ID=$(cat /tmp/sm_cc_id)
echo "[4] createAndStart"
curl -sS -m 30 -o /tmp/sm4.json -X POST "${auth[@]}" \
  "$BASE/finance/invoice-application/create-and-start" \
  -d "{\"customerCompanyId\":$CC_ID,\"invoiceCompany\":\"冒烟公司\",\"invoiceType\":\"专票\",\"lines\":[{\"businessOrderId\":$BO_ID,\"amount\":1.00}]}"
python3 - <<'PY'
import json
d=json.load(open('/tmp/sm4.json'))
print(d)
if d.get('code')!=0:
    # 流程未发布时会失败 — 记录为 BPM 阻塞
    raise SystemExit(f"FAIL/BPM?: createAndStart {d}")
print('PASS createAndStart appId=', d.get('data'))
open('/tmp/sm_app_id','w').write(str(d['data']))
PY

echo "SMOKE partial done. 审批通过后继续认领 confirm 链路。"
echo "APP_ID=$(cat /tmp/sm_app_id 2>/dev/null || true)"
