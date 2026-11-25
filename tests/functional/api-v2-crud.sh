#!/usr/bin/env bash
# Functional API v2.0 tests covering GET, POST, PUT, PATCH, and DELETE across all 4 services via Istio Gateway.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../lib/jwt.sh"
jwt_init

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0
jwt_preflight

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
  jwt_prepare "$method" "$url"
  url=$JWT_URL
  local tmp_body tmp_headers status
  tmp_body=$(mktemp)
  tmp_headers=$(mktemp)
  if [[ -n "$data" && -n "$extra_header" ]]; then
    status=$(curl -sS -H "Authorization: Bearer $JWT_TOKEN" -D "$tmp_headers" -o "$tmp_body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -H "$extra_header" -d "$data" "$url")
  elif [[ -n "$data" ]]; then
    status=$(curl -sS -H "Authorization: Bearer $JWT_TOKEN" -D "$tmp_headers" -o "$tmp_body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -d "$data" "$url")
  else
    status=$(curl -sS -H "Authorization: Bearer $JWT_TOKEN" -D "$tmp_headers" -o "$tmp_body" -w '%{http_code}' -X "$method" "$url")
  fi
  RESP_BODY=$(cat "$tmp_body")
  RESP_HEADERS=$(cat "$tmp_headers")
  RESP_STATUS="$status"
  rm -f -- "$tmp_body" "$tmp_headers"
}

tag="v2-$(date +%s)-$$"
customer_id=$CUSTOMER_ID

# ==============================================================================
# 1. PRODUCT SERVICE v2 (POST, GET list/filter/detail, PUT, PATCH, DELETE)
# ==============================================================================
request POST "$BASE_URL/api/v2/products" \
  "{\"name\":\"Mouse $tag\",\"description\":\"Wireless v2\",\"price\":150000,\"stock\":30}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/products creates product with description" "$RESP_BODY"
product_id=$(extract_id "$RESP_BODY")
assert_contains "$RESP_BODY" "Wireless v2" "Product v2 response includes description"

request GET "$BASE_URL/api/v2/products?name=$tag&page=0&size=5"
assert_status 200 "$RESP_STATUS" "GET /api/v2/products with name filter & pagination" "$RESP_BODY"
assert_contains "${RESP_HEADERS,,}" "x-total-count:" "GET /api/v2/products returns X-Total-Count header"

request GET "$BASE_URL/api/v2/products/$product_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/products/$product_id detail" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/products/$product_id" \
  "{\"name\":\"Mouse Pro $tag\",\"description\":\"Updated via PUT\",\"price\":200000,\"stock\":25}"
assert_status 200 "$RESP_STATUS" "PUT /api/v2/products/$product_id replaces product" "$RESP_BODY"
assert_contains "$RESP_BODY" "Updated via PUT" "PUT v2 updates description"

request PATCH "$BASE_URL/api/v2/products/$product_id" \
  '{"price":220000,"description":"Patched v2"}'
assert_status 200 "$RESP_STATUS" "PATCH /api/v2/products/$product_id partially updates product" "$RESP_BODY"
assert_contains "$RESP_BODY" "220000" "PATCH v2 updates price"

# Disposable product to test DELETE v2 (soft delete)
request POST "$BASE_URL/api/v2/products" \
  "{\"name\":\"Disposable $tag\",\"price\":10000,\"stock\":5}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/products creates disposable product" "$RESP_BODY"
del_product_id=$(extract_id "$RESP_BODY")

request DELETE "$BASE_URL/api/v2/products/$del_product_id"
assert_status 204 "$RESP_STATUS" "DELETE /api/v2/products/$del_product_id soft-deletes product" "$RESP_BODY"

request GET "$BASE_URL/api/v2/products/$del_product_id"
assert_status 404 "$RESP_STATUS" "GET /api/v2/products/$del_product_id returns 404 after delete" "$RESP_BODY"

# ==============================================================================
# 2. CART SERVICE v2 (POST, GET with unitPrice, PUT, PATCH, DELETE item & clear)
# ==============================================================================
request POST "$BASE_URL/api/v2/carts" "{\"customerId\":$customer_id}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/carts creates cart" "$RESP_BODY"
cart_id=$(extract_id "$RESP_BODY")

request POST "$BASE_URL/api/v2/carts/$cart_id/items" "{\"productId\":$product_id,\"quantity\":2}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/carts/$cart_id/items adds item" "$RESP_BODY"

request GET "$BASE_URL/api/v2/carts/$cart_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/carts/$cart_id returns cart with unitPrice" "$RESP_BODY"
assert_contains "$RESP_BODY" "unitPrice" "Cart v2 item exposes unitPrice"

request PUT "$BASE_URL/api/v2/carts/$cart_id/items/$product_id" '{"quantity":4}'
assert_status 200 "$RESP_STATUS" "PUT /api/v2/carts/$cart_id/items/$product_id sets quantity=4" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/carts/$cart_id/items/$product_id" '{"quantity":3}'
assert_status 200 "$RESP_STATUS" "PATCH /api/v2/carts/$cart_id/items/$product_id updates quantity=3" "$RESP_BODY"

request DELETE "$BASE_URL/api/v2/carts/$cart_id/items/$product_id"
assert_status 204 "$RESP_STATUS" "DELETE /api/v2/carts/$cart_id/items/$product_id removes single item" "$RESP_BODY"

# Re-add item, then test bulk clear DELETE /api/v2/carts/{cartId}/items
request POST "$BASE_URL/api/v2/carts/$cart_id/items" "{\"productId\":$product_id,\"quantity\":2}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/carts/$cart_id/items re-adds item for clear test" "$RESP_BODY"

request DELETE "$BASE_URL/api/v2/carts/$cart_id/items"
assert_status 204 "$RESP_STATUS" "DELETE /api/v2/carts/$cart_id/items clears all items" "$RESP_BODY"

# Prepare final item in cart for Order v2 tests
request POST "$BASE_URL/api/v2/carts/$cart_id/items" "{\"productId\":$product_id,\"quantity\":2}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/carts/$cart_id/items prepares item for Order v2" "$RESP_BODY"

# ==============================================================================
# 3. ORDER SERVICE v2 (POST, GET by customerId & detail, PATCH, POST cancel)
# ==============================================================================
request POST "$BASE_URL/api/v2/orders" "{\"cartId\":$cart_id,\"customerId\":$customer_id}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/orders creates order with items snapshot" "$RESP_BODY"
order_id=$(extract_id "$RESP_BODY")
assert_contains "$RESP_BODY" "items" "Order v2 response includes items snapshot"

request GET "$BASE_URL/api/v2/orders?customerId=$customer_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/orders?customerId=$customer_id lists orders" "$RESP_BODY"

request GET "$BASE_URL/api/v2/orders/$order_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/orders/$order_id returns order detail" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/orders/$order_id" \
  '{"shippingAddress":"Jl. Veteran No. 10, Malang"}'
assert_status 200 "$RESP_STATUS" "PATCH /api/v2/orders/$order_id updates shippingAddress" "$RESP_BODY"
assert_contains "$RESP_BODY" "Jl. Veteran No. 10, Malang" "Order v2 shippingAddress updated"

# Separate order to test POST /api/v2/orders/{orderId}/cancel (without active payment)
request POST "$BASE_URL/api/v2/orders" "{\"cartId\":$cart_id,\"customerId\":$customer_id}"
assert_status 201 "$RESP_STATUS" "POST /api/v2/orders creates order for cancel test" "$RESP_BODY"
cancel_order_id=$(extract_id "$RESP_BODY")

request POST "$BASE_URL/api/v2/orders/$cancel_order_id/cancel"
assert_status 200 "$RESP_STATUS" "POST /api/v2/orders/$cancel_order_id/cancel cancels order" "$RESP_BODY"
assert_contains "$RESP_BODY" "CANCELLED" "Cancelled order status is CANCELLED"

# ==============================================================================
# 4. PAYMENT SERVICE v2 (POST, GET by orderId & detail, PATCH reference)
# ==============================================================================
idem_key="v2-func-$(date +%s)-$$"
request POST "$BASE_URL/api/v2/payments" \
  "{\"orderId\":$order_id,\"method\":\"TRANSFER\"}" "Idempotency-Key: $idem_key"
assert_status 201 "$RESP_STATUS" "POST /api/v2/payments creates payment" "$RESP_BODY"
payment_id=$(extract_id "$RESP_BODY")

request GET "$BASE_URL/api/v2/payments?orderId=$order_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/payments?orderId=$order_id lists payment history" "$RESP_BODY"

request GET "$BASE_URL/api/v2/payments/$payment_id"
assert_status 200 "$RESP_STATUS" "GET /api/v2/payments/$payment_id returns payment detail" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/payments/$payment_id" \
  '{"transactionReference":"TRX-V2-001"}'
assert_status 200 "$RESP_STATUS" "PATCH /api/v2/payments/$payment_id updates transactionReference" "$RESP_BODY"
assert_contains "$RESP_BODY" "TRX-V2-001" "Payment v2 transactionReference updated"

# ==============================================================================
# 5. NEGATIVE & VALIDATION SCENARIOS API v2.0 (400, 404, 405, 409)
# ==============================================================================
# 5.1 Product v2 negative
request POST "$BASE_URL/api/v2/products" '{"price":350000,"stock":20}'
assert_status 400 "$RESP_STATUS" "POST /api/v2/products without name returns 400" "$RESP_BODY"

request POST "$BASE_URL/api/v2/products" '{"name":"   ","price":350000,"stock":20}'
assert_status 400 "$RESP_STATUS" "POST /api/v2/products with whitespace-only name returns 400" "$RESP_BODY"

request GET "$BASE_URL/api/v2/products/999999999"
assert_status 404 "$RESP_STATUS" "GET /api/v2/products/999999999 not found returns 404" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/products/$product_id" '{}'
assert_status 400 "$RESP_STATUS" "PUT /api/v2/products/{id} with empty body returns 400" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/products/999999999" '{"name":"Missing","price":10000,"stock":1}'
assert_status 404 "$RESP_STATUS" "PUT /api/v2/products/999999999 not found returns 404" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/products/$product_id" '{"price":-1}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/products/{id} with negative price returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/products/$product_id" '{}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/products/{id} with empty body returns 400" "$RESP_BODY"

# 5.2 Cart v2 negative
request POST "$BASE_URL/api/v2/carts" '{}'
assert_status 400 "$RESP_STATUS" "POST /api/v2/carts without customerId returns 400" "$RESP_BODY"

request GET "$BASE_URL/api/v2/carts/999999999"
assert_status 404 "$RESP_STATUS" "GET /api/v2/carts/999999999 not found returns 404" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/carts/$cart_id/items/$product_id" '{"quantity":0}'
assert_status 400 "$RESP_STATUS" "PUT /api/v2/carts/{id}/items/{productId} with quantity=0 returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/carts/$cart_id/items/$product_id" '{}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/carts/{id}/items/{productId} with empty body returns 400" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/carts/$cart_id/items/$product_id" '{"quantity":999999}'
assert_status 409 "$RESP_STATUS" "PUT /api/v2/carts/{id}/items/{productId} exceeding stock returns 409" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/carts/$cart_id/items/999999999" '{"quantity":1}'
assert_status 404 "$RESP_STATUS" "PUT /api/v2/carts/{id}/items/999999999 missing item returns 404" "$RESP_BODY"

# 5.3 Order v2 negative
request POST "$BASE_URL/api/v2/orders" "{\"customerId\":$customer_id}"
assert_status 400 "$RESP_STATUS" "POST /api/v2/orders without cartId returns 400" "$RESP_BODY"

request GET "$BASE_URL/api/v2/orders/999999999"
assert_status 404 "$RESP_STATUS" "GET /api/v2/orders/999999999 not found returns 404" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/orders/$order_id" '{}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/orders/{id} with empty body returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/orders/$order_id" '{"shippingAddress":"   "}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/orders/{id} with whitespace shippingAddress returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/orders/999999999" '{"shippingAddress":"Jl. Veteran No. 10"}'
assert_status 404 "$RESP_STATUS" "PATCH /api/v2/orders/999999999 not found returns 404" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/orders/$order_id" '{"shippingAddress":"Alamat Uji"}'
assert_status 405 "$RESP_STATUS" "PUT /api/v2/orders/{id} unsupported method returns 405" "$RESP_BODY"

request POST "$BASE_URL/api/v2/orders/$order_id/cancel"
assert_status 409 "$RESP_STATUS" "POST /api/v2/orders/{id}/cancel with PENDING payment returns 409" "$RESP_BODY"

# 5.4 Payment v2 negative & idempotency
request POST "$BASE_URL/api/v2/payments" "{\"orderId\":$order_id,\"method\":\"COD\"}"
assert_status 400 "$RESP_STATUS" "POST /api/v2/payments without Idempotency-Key returns 400" "$RESP_BODY"

request GET "$BASE_URL/api/v2/payments/999999999"
assert_status 404 "$RESP_STATUS" "GET /api/v2/payments/999999999 not found returns 404" "$RESP_BODY"

request POST "$BASE_URL/api/v2/payments" "{\"orderId\":$order_id,\"method\":\"TRANSFER\"}" "Idempotency-Key: $idem_key"
assert_status 201 "$RESP_STATUS" "POST /api/v2/payments replay with same key & payload returns 201" "$RESP_BODY"

request POST "$BASE_URL/api/v2/payments" "{\"orderId\":$order_id,\"method\":\"COD\"}" "Idempotency-Key: $idem_key"
assert_status 409 "$RESP_STATUS" "POST /api/v2/payments reuse key with different payload returns 409" "$RESP_BODY"
assert_contains "$RESP_BODY" "IDEMPOTENCY_KEY_REUSED" "Payment conflict returns IDEMPOTENCY_KEY_REUSED"

request POST "$BASE_URL/api/v2/payments" "{\"orderId\":$cancel_order_id,\"method\":\"COD\"}" "Idempotency-Key: v2-cancel-$tag"
assert_status 409 "$RESP_STATUS" "POST /api/v2/payments on CANCELLED order returns 409" "$RESP_BODY"
assert_contains "$RESP_BODY" "ORDER_NOT_PAYABLE" "Payment on CANCELLED order returns ORDER_NOT_PAYABLE"

request PATCH "$BASE_URL/api/v2/payments/$payment_id" '{}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/payments/{id} with empty body returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/payments/$payment_id" '{"transactionReference":"   "}'
assert_status 400 "$RESP_STATUS" "PATCH /api/v2/payments/{id} with whitespace reference returns 400" "$RESP_BODY"

request PATCH "$BASE_URL/api/v2/payments/999999999" '{"transactionReference":"TRX-UJI"}'
assert_status 404 "$RESP_STATUS" "PATCH /api/v2/payments/999999999 not found returns 404" "$RESP_BODY"

request PUT "$BASE_URL/api/v2/payments/$payment_id" '{"transactionReference":"TRX-UJI"}'
assert_status 405 "$RESP_STATUS" "PUT /api/v2/payments/{id} unsupported method returns 405" "$RESP_BODY"

printf 'All %d API v2.0 functional assertions passed.\n' "$passed"
