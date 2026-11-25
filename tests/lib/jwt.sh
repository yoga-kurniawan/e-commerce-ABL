#!/usr/bin/env bash
# Shared authentication for legacy HTTP suites. Source from a Bash script.
JWT_REPO_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)

jwt_fail() { printf 'JWT setup: %s\n' "$*" >&2; return 1; }

jwt_refresh() {
  local role=$1 token_var exp_var auto_var account token metadata expires
  if [[ "$role" == admin ]]; then
    token_var=ADMIN_TOKEN; exp_var=JWT_ADMIN_EXP; auto_var=JWT_ADMIN_AUTO; account=${JWT_ADMIN_ACCOUNT:-api-admin}
  else
    token_var=CUSTOMER_TOKEN; exp_var=JWT_CUSTOMER_EXP; auto_var=JWT_CUSTOMER_AUTO; account=${JWT_CUSTOMER_ACCOUNT:-customer-a}
  fi
  expires=${!exp_var:-0}
  if (( expires > $(date +%s) + 60 )); then return; fi
  token=${!token_var:-}
  if [[ -z "$token" || ${!auto_var:-false} == true ]]; then
    token=$(python3 "$JWT_REPO_ROOT/scripts/auth-token.py" "$account") || return 1
    printf -v "$auto_var" '%s' true
  elif [[ "$expires" != 0 ]]; then
    jwt_fail "$token_var hampir kedaluwarsa; login ulang atau gunakan auto-login lokal."; return 1
  fi
  metadata=$(printf '%s' "$token" | python3 -c '
import base64,json,sys,time
try:
 t=sys.stdin.read().strip().split(".")[1]; c=json.loads(base64.urlsafe_b64decode(t+"="*(-len(t)%4)))
 role=sys.argv[1]; roles=c.get("realm_access",{}).get("roles",[]); exp=c["exp"]
 assert isinstance(exp,int) and exp>time.time()+60
 if role=="admin": assert "ADMIN" in roles
 else:
  assert "CUSTOMER" in roles and "ADMIN" not in roles
  cid=str(c["customer_id"]); assert cid.isascii() and cid.isdigit() and 0<int(cid)<=9223372036854775807
 print(exp)
except Exception:
 sys.exit("JWT metadata/role/expiry tidak sesuai. Token tidak ditampilkan; backend tetap memverifikasi signature.")
' "$role") || return 1
  printf -v "$token_var" '%s' "$token"
  printf -v "$exp_var" '%s' "$metadata"
}

jwt_init() {
  [[ -z ${ADMIN_TOKEN:-} ]] && JWT_ADMIN_AUTO=true || JWT_ADMIN_AUTO=false
  [[ -z ${CUSTOMER_TOKEN:-} ]] && JWT_CUSTOMER_AUTO=true || JWT_CUSTOMER_AUTO=false
  if [[ ${1:-both} != customer ]]; then jwt_refresh admin || return 1; fi
  jwt_refresh customer || return 1
  local claim
  claim=$(printf '%s' "$CUSTOMER_TOKEN" | python3 -c 'import base64,json,sys;t=sys.stdin.read().split(".")[1];print(int(json.loads(base64.urlsafe_b64decode(t+"="*(-len(t)%4)))["customer_id"]))') || return 1
  [[ -z ${CUSTOMER_ID:-} || "$CUSTOMER_ID" == "$claim" ]] || { jwt_fail 'CUSTOMER_ID berbeda dengan claim token.'; return 1; }
  CUSTOMER_ID=$claim
}

jwt_prepare() {
  local method=$1 url=$2 role=customer service path
  if [[ "$url" == */api/v[12]/products* && "$method" != GET && "$method" != HEAD ]]; then role=admin; fi
  jwt_refresh "$role" || return 1
  if [[ "$role" == admin ]]; then JWT_TOKEN=$ADMIN_TOKEN; else JWT_TOKEN=$CUSTOMER_TOKEN; fi
  JWT_URL=$url
  # Optional direct-backend URLs for isolated checks; default remains the Istio gateway.
  for service in product cart order payment; do
    if [[ "$url" == */api/v[12]/${service}s* ]]; then
      local setting="${service^^}_URL"
      if [[ -n ${!setting:-} ]]; then
        path="/api/${url#*/api/}"; JWT_URL="${!setting%/}$path"
      fi
      break
    fi
  done
}

jwt_preflight() {
  jwt_prepare GET "${BASE_URL:-http://127.0.0.1:18080}/api/v1/products" || return 1
  local status
  status=$(curl -sS --connect-timeout 5 --max-time 20 -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $JWT_TOKEN" "$JWT_URL") || return 1
  [[ "$status" == 200 ]] || { jwt_fail "Preflight Product HTTP $status; hentikan sebelum mutasi cluster. Pastikan image JWT dan token benar."; return 1; }
  status=$(curl -sS --connect-timeout 5 --max-time 20 -o /dev/null -w '%{http_code}' "$JWT_URL") || return 1
  [[ "$status" == 401 ]] || { jwt_fail "Tanpa JWT harus 401, mendapat $status. Pastikan image backend terbaru sudah dipasang."; return 1; }
}
