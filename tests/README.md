# Automated Test Suites (`tests/`)

Direktori ini berisi suite pengujian terpusat untuk memverifikasi helper deployment, alur bisnis lintas-service melalui Istio Ingress Gateway, keamanan service mesh, ketahanan rollout/failover, dan beban transaksi dengan `k6`.

## Struktur Direktori

| Direktori / File | Cakupan | Ketergantungan Cluster |
|---|---|---|
| `tests/scripts/deploy-local-test.sh` | Regression test `scripts/deploy-local.sh` menggunakan command doubles | Tidak perlu cluster |
| `tests/integration/e2e-happy-path.sh` | Alur penuh `Product -> Cart -> Order -> Payment` | Perlu cluster + Gateway `18080` |
| `tests/integration/e2e-negative-contracts.sh` | Validasi kontrak negatif, duplicate JSON key, idempotency, dan `404` | Perlu cluster + Gateway `18080` |
| `tests/security/istio-mesh-security.sh` | Verifikasi mTLS `STRICT`, L7 `AuthorizationPolicy`, dan isolasi `/actuator/*` | Perlu Minikube + Istio + Gateway `18080` |
| `tests/resilience/zero-downtime-rollout.sh` | Uji rolling restart `abl-product-service` tanpa response non-`200` | Perlu Minikube + Gateway `18080` |
| `tests/resilience/downstream-failover.sh` | Uji fast-fail `503` saat `abl-cart-service` down dan recovery `200 OK` | Perlu Minikube + Gateway `18080` |
| `tests/load/order-checkout-k6.js` | Uji beban paralel `GET /api/v1/products` dan `POST /api/v1/orders` | Perlu `k6` + cluster + Gateway `18080` |

## Prasyarat

1. **Untuk `tests/scripts/deploy-local-test.sh`:**
   - Bash 4+ (`bash -n` dan eksekusi lokal tanpa cluster).
2. **Untuk suite `integration/`, `security/`, `resilience/`, dan `load/`:**
   - Cluster Minikube (`ecommerce-abl`) berjalan dengan seluruh 4 service aktif (`bash scripts/deploy-local.sh up payment`).
   - Port-forward Istio Ingress Gateway aktif di `http://127.0.0.1:18080` pada terminal terpisah:
     ```bash
     bash scripts/deploy-local.sh gateway
     ```
   - Tool host: `curl`, `minikube`, `kubectl` (via `minikube kubectl`), dan `k6` (khusus load test).

## Cara Menjalankan

```bash
# 1. Uji helper deployment (tanpa cluster)
bash tests/scripts/deploy-local-test.sh

# 2. Uji E2E happy path & negative contracts
bash tests/integration/e2e-happy-path.sh
bash tests/integration/e2e-negative-contracts.sh

# 3. Uji keamanan Istio service mesh
bash tests/security/istio-mesh-security.sh

# 4. Uji ketahanan rollout & failover
bash tests/resilience/zero-downtime-rollout.sh
bash tests/resilience/downstream-failover.sh

# 5. Uji beban dengan k6 (lokal atau via Docker)
k6 run tests/load/order-checkout-k6.js
# atau tanpa install k6 di host:
docker run --rm -i --network=host grafana/k6 run - < tests/load/order-checkout-k6.js
```

Variabel lingkungan opsional yang didukung:
- `BASE_URL` (default: `http://127.0.0.1:18080`)
- `MINIKUBE_PROFILE` (default: `ecommerce-abl`)
- `KUBE_CONTEXT` (default: `ecommerce-abl`)
- `NAMESPACE` (default: `ecommerce`)
