#!/usr/bin/env bash
# Negative contract, validation, duplicate JSON key, and idempotency tests via Istio Gateway.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0

pass() {
  ((passed += 1))
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_status() {
  local expected="$1" actual="$2" label="$3" body="${4:-}"
  if [[ "$actual" != "$expected" ]]; then
    printf 'Response body: %s\n' "$body" >&2
    fail "$label (expected HTTP $expected, got $actual)"
  fi
  pass "$label (HTTP $actual)"
}

assert_contains() {
  local haystack="$1" needle="$2" label="$3"
  if ! grep -Fq -- "$needle" <<<"$haystack"; then
    printf 'Response body: %s\n' "$haystack" >&2
    fail "$label (missing '$needle')"
  fi
  pass "$label"
}

extract_id() {
  local body="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$body"
  else
    sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' <<<"$body" | head -n1
  fi
}

request() {
  local method="$1" url="$2" data="${3:-}" extra_header="${4:-}"
  local tmp_body status
  tmp_body=$(mktemp)
  if [[ -n "$data" && -n "$extra_header" ]]; then
    status=$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -H "$extra_header" -d "$data" "$url")
  elif [[ -n "$data" ]]; then
    status=$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -d "$data" "$url")
  else
    status=$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" "$url")
  fi
  RESP_BODY=$(cat "$tmp_body")
  RESP_STATUS="$status"
  rm -f -- "$tmp_body"
}

# 1. Blank product name -> 400
request POST "$BASE_URL/api/v1/products" '{"name":"   ","price":10000,"stock":5}'
assert_status 400 "$RESP_STATUS" "Blank product name rejected" "$RESP_BODY"

# 2. Float stock -> 400
request POST "$BASE_URL/api/v1/products" '{"name":"Float Stock Item","price":10000,"stock":1.5}'
assert_status 400 "$RESP_STATUS" "Float product stock rejected" "$RESP_BODY"

# 3. Duplicate JSON key on Product -> 400
request POST "$BASE_URL/api/v1/products" '{"name":"First","name":"Second","price":10000,"stock":5}'
assert_status 400 "$RESP_STATUS" "Duplicate JSON key on Product rejected" "$RESP_BODY"

# 4. Duplicate JSON key on Order -> 400
request POST "$BASE_URL/api/v1/orders" '{"cartId":1,"cartId":2,"customerId":101}'
assert_status 400 "$RESP_STATUS" "Duplicate JSON key on Order rejected" "$RESP_BODY"

# 5. Missing Idempotency-Key header on Payment -> 400
request POST "$BASE_URL/api/v1/payments" '{"orderId":1,"method":"COD"}'
assert_status 400 "$RESP_STATUS" "POST /api/v1/payments without Idempotency-Key rejected" "$RESP_BODY"

# 6. Prepare valid Order for Payment idempotency replay & conflict tests
request POST "$BASE_URL/api/v1/products" '{"name":"Idempotency Fixture","price":50000,"stock":10}'
assert_status 201 "$RESP_STATUS" "Fixture product created" "$RESP_BODY"
product_id=$(extract_id "$RESP_BODY")

request POST "$BASE_URL/api/v1/carts" '{"customerId":202}'
assert_status 201 "$RESP_STATUS" "Fixture cart created" "$RESP_BODY"
cart_id=$(extract_id "$RESP_BODY")

request POST "$BASE_URL/api/v1/carts/$cart_id/items" "{\"productId\":$product_id,\"quantity\":1}"
assert_status 201 "$RESP_STATUS" "Fixture item added to cart" "$RESP_BODY"

request POST "$BASE_URL/api/v1/orders" "{\"cartId\":$cart_id,\"customerId\":202}"
assert_status 201 "$RESP_STATUS" "Fixture order created" "$RESP_BODY"
order_id=$(extract_id "$RESP_BODY")

idem_key="neg-contract-$(date +%s)-$$"
request POST "$BASE_URL/api/v1/payments" "{\"orderId\":$order_id,\"method\":\"COD\"}" "Idempotency-Key: $idem_key"
assert_status 201 "$RESP_STATUS" "Initial Payment with Idempotency-Key succeeds" "$RESP_BODY"
first_payment_id=$(extract_id "$RESP_BODY")

# 7. Idempotency replay with identical payload returns same payment ID
request POST "$BASE_URL/api/v1/payments" "{\"orderId\":$order_id,\"method\":\"COD\"}" "Idempotency-Key: $idem_key"
assert_status 201 "$RESP_STATUS" "Idempotency replay returns original status 201" "$RESP_BODY"
replay_payment_id=$(extract_id "$RESP_BODY")
[[ "$replay_payment_id" == "$first_payment_id" ]] || fail "Replay returned different payment ID ($replay_payment_id != $first_payment_id)"
pass "Idempotency replay returned same payment ID ($first_payment_id)"

# 8. Idempotency key reused with different payload -> 409 IDEMPOTENCY_KEY_REUSED
request POST "$BASE_URL/api/v1/payments" "{\"orderId\":$order_id,\"method\":\"TRANSFER\"}" "Idempotency-Key: $idem_key"
assert_status 409 "$RESP_STATUS" "Idempotency key reuse with different payload rejected" "$RESP_BODY"
assert_contains "$RESP_BODY" "IDEMPOTENCY_KEY_REUSED" "Conflict body contains IDEMPOTENCY_KEY_REUSED"

# 9. Non-existent resources -> 404
request GET "$BASE_URL/api/v1/products/999999999"
assert_status 404 "$RESP_STATUS" "Non-existent product returns 404" "$RESP_BODY"

request GET "$BASE_URL/api/v1/payments/999999999"
assert_status 404 "$RESP_STATUS" "Non-existent payment returns 404" "$RESP_BODY"

printf 'All %d negative contract assertions passed.\n' "$passed"
