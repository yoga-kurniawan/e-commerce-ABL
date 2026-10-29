#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
service="${1:-}"
case "$service" in
  product|cart|order|payment) ;;
  *) echo "Usage: bash scripts/run-local-service.sh {product|cart|order|payment}" >&2; exit 2 ;;
esac
if [[ ! -f "$ROOT/.env" ]]; then
  echo 'Missing .env: copy .env.example to .env and fill database passwords.' >&2
  exit 1
fi
# .env is a trusted developer-owned Bash-compatible configuration file.
set -a
source "$ROOT/.env"
set +a
prefix="${service^^}"
for suffix in DB_NAME DB_USER DB_PASSWORD DB_PORT SERVICE_PORT; do
  key="${prefix}_${suffix}"
  [[ -n "${!key:-}" ]] || { echo "Missing $key in .env" >&2; exit 1; }
done
for name in PRODUCT_SERVICE_PORT CART_SERVICE_PORT ORDER_SERVICE_PORT; do
  value="${!name:-}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( 10#$value >= 1 && 10#$value <= 65535 )) || {
    echo "Invalid $name in .env" >&2; exit 1;
  }
done
port_key="${prefix}_SERVICE_PORT"
db_port_key="${prefix}_DB_PORT"
db_name_key="${prefix}_DB_NAME"
user_key="${prefix}_DB_USER"
password_key="${prefix}_DB_PASSWORD"
for key in "$port_key" "$db_port_key"; do
  value="${!key}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( 10#$value >= 1 && 10#$value <= 65535 )) || {
    echo "Invalid $key in .env" >&2; exit 1;
  }
done
printf -v client_config '{"clients":{"product-url":"http://127.0.0.1:%s","cart-url":"http://127.0.0.1:%s","order-url":"http://127.0.0.1:%s"}}' \
  "$PRODUCT_SERVICE_PORT" "$CART_SERVICE_PORT" "$ORDER_SERVICE_PORT"
cd "$ROOT"
exec env \
  SERVER_PORT="${!port_key}" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST:-127.0.0.1}:${!db_port_key}/${!db_name_key}" \
  SPRING_DATASOURCE_USERNAME="${!user_key}" \
  SPRING_DATASOURCE_PASSWORD="${!password_key}" \
  SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
  SPRING_JPA_OPEN_IN_VIEW=false \
  SPRING_FLYWAY_ENABLED=true \
  SPRING_APPLICATION_JSON="$client_config" \
  bash "$ROOT/mvnw" -pl "services/$service-service" spring-boot:run
