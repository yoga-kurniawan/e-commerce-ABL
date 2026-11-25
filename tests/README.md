# Automated Test Suites (`tests/`)

Suite lama kini mengirim JWT pada request bisnis. Product writes menggunakan
ADMIN, sedangkan Cart/Order/Payment menggunakan CUSTOMER dengan customerId yang
diambil dari claim token. `jwt-e2e.py` dan regression test deployment yang sudah
mendukung perilakunya tidak diubah.

| Suite | Cakupan | Infrastruktur |
| --- | --- | --- |
| `scripts/deploy-local-test.sh` | Helper deployment, command doubles | Tidak perlu cluster/JWT |
| `scripts/jwt-script-test.py` | Guard JWT, fault cleanup dan pemisahan penolakan mesh/backend | Command doubles, tanpa cluster/login |
| `scripts/k6-jwt-test.cjs` | Role, refresh dan kegagalan token k6 | Node.js, tanpa cluster/login |
| `functional/api-v2-crud.sh` | CRUD/negative contracts V2 | Empat backend JWT + gateway |
| `integration/e2e-happy-path.sh` | Product → Cart → Order → Payment | Empat backend JWT + gateway |
| `integration/e2e-negative-contracts.sh` | Validasi, duplicate keys, idempotensi, 404 | Empat backend JWT + gateway |
| `security/jwt-e2e.py` | JWT dan ownership V1/V2, DB sementara | Keycloak, Docker, empat JAR hasil build |
| `security/istio-mesh-security.sh` | STRICT, SPIFFE RBAC, JWT, isolasi actuator | Minikube + Istio + empat backend JWT |
| `resilience/zero-downtime-rollout.sh` | Respons 200 selama restart Product | Minikube + JWT + kapasitas dua replika |
| `resilience/downstream-failover.sh` | Cart down → Order 503 → recovery | Minikube + JWT |
| `load/order-checkout-k6.js` | Load checkout dengan tiga Product | k6 + empat backend JWT + gateway |

## Prasyarat untuk suite HTTP lama

1. Gunakan image dari branch JWT terbaru, bukan hanya checkout source-nya.
   Deployment dapat dibangun melalui `bash scripts/deploy-local.sh up payment`.
   Helper autentikasi mengharuskan Product menerima JWT valid (200) dan menolak
   request tanpa JWT (401), agar image lama tidak memberi kesan tes JWT lulus.
2. Jalankan Keycloak lokal sesuai [panduan JWT](../docs/guides/jwt-authentication.md).
   Pertahankan `bash scripts/auth-local.sh gateway` di terminal terpisah (18090).
3. Jalankan `bash scripts/deploy-local.sh gateway` di terminal terpisah (18080).
4. Shell suites memerlukan Bash 4+, Python 3, curl. Mesh/fault suites juga memakai
   minikube/kubectl. Node.js diperlukan hanya untuk regression test k6 tanpa load.

**Tes fault dan rollout benar-benar mengubah replika/restart workload.** Pakai
cluster pengujian. Sebelum mutasi, JWT dicek; jumlah replika awal disimpan dan
kembali dipulihkan pada exit. Script tidak menganggap kegagalan login sebagai
bukti failover. Tetap sediakan resource yang cukup untuk rollout.

## Menjalankan shell suites

```bash
# Auto-login lokal: api-admin untuk fixture Product, customer-a untuk transaksi.
# Token diperbarui sebelum kedaluwarsa menggunakan kredensial lokal yang sudah ada.
bash tests/functional/api-v2-crud.sh
bash tests/integration/e2e-happy-path.sh
bash tests/integration/e2e-negative-contracts.sh
bash tests/security/istio-mesh-security.sh
bash tests/resilience/zero-downtime-rollout.sh
bash tests/resilience/downstream-failover.sh
```

Opsional gunakan token sendiri, misalnya dari identity provider di lingkungan tim:

```bash
export ADMIN_TOKEN=$(python3 scripts/auth-token.py api-admin)
export CUSTOMER_TOKEN=$(python3 scripts/auth-token.py customer-b)
bash tests/integration/e2e-happy-path.sh
```

Token CUSTOMER harus benar-benar role CUSTOMER (bukan ADMIN), agar pengujian tidak
melewati pemeriksaan kepemilikan melalui hak admin. CUSTOMER_ID otomatis mengikuti
claim `customer_id`; jika diisi manual tetapi berbeda, script berhenti. Token yang
diberikan lewat environment tidak bisa diperbarui sendiri oleh script: login ulang
jika hampir kedaluwarsa. Token/password tidak ditampilkan dalam output.

Variabel shell:

| Variabel | Default / arti |
| --- | --- |
| `BASE_URL` | `http://127.0.0.1:18080` |
| `ADMIN_TOKEN`, `CUSTOMER_TOKEN` | Opsional; jika kosong auto-login lokal |
| `JWT_ADMIN_ACCOUNT` | `api-admin` (auto-login) |
| `JWT_CUSTOMER_ACCOUNT` | `customer-a`; bisa `customer-b` |
| `CUSTOMER_ID` | Otomatis dari JWT, wajib cocok bila ditentukan |
| `MINIKUBE_PROFILE`, `KUBE_CONTEXT` | `ecommerce-abl` |
| `NAMESPACE` | `ecommerce` |

Untuk functional/integration pada backend langsung, opsional tentukan
`PRODUCT_URL`, `CART_URL`, `ORDER_URL`, `PAYMENT_URL` (base URL tanpa `/api`).
Contoh port mode manual adalah 19081–19084. Ini menguji aplikasi, bukan Istio.
Gunakan database uji: script membuat fixture baru dan tidak otomatis menghapus
seluruh transaksi setelah selesai. Jangan pakai override backend langsung saat
menguji fault/mesh pada gateway cluster.

## Load test k6

```bash
export ADMIN_TOKEN=$(python3 scripts/auth-token.py api-admin)
export CUSTOMER_TOKEN=$(python3 scripts/auth-token.py customer-a)
k6 run tests/load/order-checkout-k6.js
# Docker, bila host networking tersedia:
docker run --rm -i --network=host -e BASE_URL -e ADMIN_TOKEN -e CUSTOMER_TOKEN \
  grafana/k6 run - < tests/load/order-checkout-k6.js
```

Mode token cocok untuk default test 40 detik selama token masih berlaku. Untuk tes
lebih lama, jangan set ADMIN_TOKEN/CUSTOMER_TOKEN; sediakan `K6_ADMIN_PASSWORD`
dan `K6_CUSTOMER_PASSWORD` melalui environment. Username default `api-admin` dan
`customer-a`, dapat diubah dengan `K6_ADMIN_USER`/`K6_CUSTOMER_USER`. Setiap VU
menyimpan token sendiri dan login ulang menjelang exp. `AUTH_URL` default endpoint
Keycloak localhost:18090; `AUTH_CLIENT_ID` default `ecommerce-cli`.
Jangan commit password atau menyalinnya ke source test.

Fixture Product dibuat dengan ADMIN hanya saat setup. Request checkout memakai
CUSTOMER dan customerId claim-nya; tiga item tetap menguji lookup Product paralel.
Counter `jwt_auth_failures` wajib nol, sehingga kegagalan refresh/role/expiry tidak
bisa membuat load test terlihat sukses hanya karena tidak ada request bisnis.

## Regression checks tanpa deployment

```bash
bash tests/scripts/deploy-local-test.sh
python3 tests/scripts/jwt-script-test.py
node tests/scripts/k6-jwt-test.cjs
```

Mesh test memeriksa pod probe benar-benar Ready, mengirim token lewat stdin
(bukan Pod spec), lalu membedakan GET yang diizinkan + JWT → 200, GET diizinkan
mesh tanpa JWT → 401 backend, dan operasi terlarang dari identitas Cart → 403
**dengan body Envoy RBAC** meskipun JWT-nya ADMIN. HTTP 403 dari backend saja tidak
cukup untuk menyatakan Istio RBAC berhasil. Transport rejection non-mesh tetap
konsisten dengan STRICT/network isolation; bukan bukti eksklusif penyebab mTLS.
