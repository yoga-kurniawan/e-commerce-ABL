#!/usr/bin/env bash
# End-to-end happy path test: Product -> Cart -> Order -> Payment via Istio Gateway.
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

# 1. Create Product
request POST "$BASE_URL/api/v1/products" '{"name":"E2E Mechanical Keyboard","price":350000,"stock":20}'
assert_status 201 "$RESP_STATUS" "POST /api/v1/products creates product" "$RESP_BODY"
product_id=$(extract_id "$RESP_BODY")
[[ -n "$product_id" ]] || fail "Failed to extract product_id from: $RESP_BODY"

# 2. Get Product
request GET "$BASE_URL/api/v1/products/$product_id"
assert_status 200 "$RESP_STATUS" "GET /api/v1/products/$product_id returns product" "$RESP_BODY"
assert_contains "$RESP_BODY" "E2E Mechanical Keyboard" "Product payload contains name"

# 3. Create Cart
request POST "$BASE_URL/api/v1/carts" '{"customerId":101}'
assert_status 201 "$RESP_STATUS" "POST /api/v1/carts creates cart" "$RESP_BODY"
cart_id=$(extract_id "$RESP_BODY")
[[ -n "$cart_id" ]] || fail "Failed to extract cart_id from: $RESP_BODY"

# 4. Add Item to Cart
request POST "$BASE_URL/api/v1/carts/$cart_id/items" "{\"productId\":$product_id,\"quantity\":2}"
assert_status 201 "$RESP_STATUS" "POST /api/v1/carts/$cart_id/items adds item" "$RESP_BODY"

# 5. Get Cart and verify total
request GET "$BASE_URL/api/v1/carts/$cart_id"
assert_status 200 "$RESP_STATUS" "GET /api/v1/carts/$cart_id returns cart" "$RESP_BODY"
assert_contains "$RESP_BODY" "700000" "Cart total matches 2 * 350000 = 700000"

# 6. Create Order
request POST "$BASE_URL/api/v1/orders" "{\"cartId\":$cart_id,\"customerId\":101}"
assert_status 201 "$RESP_STATUS" "POST /api/v1/orders creates order" "$RESP_BODY"
order_id=$(extract_id "$RESP_BODY")
[[ -n "$order_id" ]] || fail "Failed to extract order_id from: $RESP_BODY"
assert_contains "$RESP_BODY" "PENDING" "Order initial status is PENDING"
assert_contains "$RESP_BODY" "700000" "Order total matches 700000"

# 7. Get Order
request GET "$BASE_URL/api/v1/orders/$order_id"
assert_status 200 "$RESP_STATUS" "GET /api/v1/orders/$order_id returns order" "$RESP_BODY"

# 8. Create Payment
idem_key="e2e-happy-$(date +%s)-$$"
request POST "$BASE_URL/api/v1/payments" "{\"orderId\":$order_id,\"method\":\"COD\"}" "Idempotency-Key: $idem_key"
assert_status 201 "$RESP_STATUS" "POST /api/v1/payments creates payment" "$RESP_BODY"
payment_id=$(extract_id "$RESP_BODY")
[[ -n "$payment_id" ]] || fail "Failed to extract payment_id from: $RESP_BODY"
assert_contains "$RESP_BODY" "700000" "Payment amount matches 700000"
assert_contains "$RESP_BODY" "PENDING" "Payment initial status is PENDING"

# 9. Get Payment
request GET "$BASE_URL/api/v1/payments/$payment_id"
assert_status 200 "$RESP_STATUS" "GET /api/v1/payments/$payment_id returns payment" "$RESP_BODY"

printf 'All %d E2E happy path assertions passed.\n' "$passed"
