# E-Commerce Microservices ABL

Backend e-commerce untuk project mata kuliah **Arsitektur Berbasis Layanan**, kelompok *dus bit gut genung*. Sistem dirancang sebagai empat aplikasi terpisah: **Product, Cart, Order, dan Payment**, dilengkapi modul keamanan bersama (`security-support`).

Struktur kode menerapkan *Hexagonal Architecture* (memisahkan model domain, koordinator use case, serta adapter HTTP dan database). Keempat service mendukung **API v1.0 dan v2.0** dengan kompatibilitas mundur, database PostgreSQL 17 + Flyway terpisah per service, pengamanan **JWT RS256 (Keycloak)**, serta orkestrasi **Kubernetes (Minikube) + Helm + Istio Service Mesh**.

- Panduan deployment Minikube & Istio: [deploy/README.md](deploy/README.md)
- Panduan autentikasi JWT & Keycloak: [docs/guides/jwt-authentication.md](docs/guides/jwt-authentication.md)
- Panduan pengujian otomatis: [tests/README.md](tests/README.md)

## Daftar isi

- [Tech stack](#tech-stack)
- [Daftar service](#daftar-service)
- [Database per service](#database-per-service)
- [Konsep integrasi](#konsep-integrasi)
- [Struktur project](#struktur-project)
- [Quick start Minikube + Istio + Keycloak](#quick-start-minikube--istio--keycloak)
- [Persiapan development](#persiapan-development)
- [Menjalankan infrastruktur lokal](#menjalankan-infrastruktur-lokal)
- [Memeriksa database](#memeriksa-database)
- [Menjalankan service Spring Boot](#menjalankan-service-spring-boot)
- [Dokumentasi API](#dokumentasi-api)
- [Flow pengujian alur utama](#flow-pengujian-alur-utama)
- [Flow pengujian perubahan data](#flow-pengujian-perubahan-data)
- [Flow pengujian gagal](#flow-pengujian-gagal)
- [Pengujian dan bukti demo](#pengujian-dan-bukti-demo)
- [Status implementasi dan verifikasi](#status-implementasi-dan-verifikasi)
- [Dokumentasi lanjutan](#dokumentasi-lanjutan)

## Tech stack

| Kategori | Teknologi | Status/dasar |
|---|---|---|
| Backend | Java 17, Spring Boot 4.1.2 | Keempat service (Product, Cart, Order, Payment) + `security-support` terimplementasi penuh |
| API | REST, JSON, URL path versioning `/api/v1` dan `/api/v2` | Product, Cart, Order, Payment v1 & v2 tersedia dan terverifikasi |
| Dokumentasi API | OpenAPI 3.0.3, API versi 1.0.0 dan 2.0.0 | Kontrak pada `specs/openapi.yaml` dan `specs/openapi-v2.yaml` |
| Arsitektur internal | Domain, use case, ports/adapters (Hexagonal) | Terimplementasi penuh pada keempat service |
| Keamanan & Auth | Keycloak, Spring Security OAuth2 Resource Server (JWT RS256), Istio mTLS `STRICT` & L7 RBAC | Validasi token, role `ADMIN`/`CUSTOMER`, kepemilikan `customer_id`, dan token relay aktif |
| Database | PostgreSQL 17, database terpisah per service | Teruji dengan Testcontainers & StatefulSet Minikube |
| Persistence | Spring Data JPA/Hibernate dan Flyway | Migrasi Flyway & entity JPA tervalidasi |
| Build | Maven multi-module dan Maven Wrapper | Multi-module build (`./mvnw -B -ntp verify`) |
| Container | Docker Linux containers (multi-stage build) | 4 Dockerfile teruji dan termuat ke Minikube |
| Deployment | Kubernetes/Minikube, Istio, Helm | Helm umbrella chart teruji di Minikube WSL2: mTLS `STRICT`, L7 RBAC, Outlier Detection CB |
| Pengujian HTTP/beban | `curl`, bash/python automated suite (`tests/`), Grafana `k6` | Functional, Integration E2E, Security Mesh & JWT, Resilience, Load, Regression |

Fitur asynchronous eventing (Kafka, DLQ), shipping, dan notification berada di luar cakupan implementasi saat ini.

## Daftar service

Port berikut merupakan konfigurasi **development langsung pada host**, bukan status bahwa prosesnya sudah berjalan.

| Service | Port lokal | Database | Fungsi | Operasi v1 | Operasi v2 |
|---|---:|---|---|---:|---:|
| `product-service` | 8081 | `product_db` | Katalog, harga, stok, pembaruan dan penghapusan produk | 6 | 6 |
| `cart-service` | 8082 | `cart_db` | Membuat/membaca Cart dan mengelola item | 6 | 7 |
| `order-service` | 8083 | `order_db` | Membuat/membaca pesanan, mengubah atribut yang diizinkan, dan membatalkan | 3 | 5 |
| `payment-service` | 8084 | `payment_db` | Membuat/membaca permintaan pembayaran dan mengubah metadata yang diizinkan | 3 | 4 |
| **Total** | | | **v1: 10 path, v2: 11 path** | **18** | **22** |

Versi 2.0.0 (DEC-020–DEC-023) bersifat *backward-compatible*: seluruh operasi v1 tetap tersedia di `/api/v2` dengan makna yang sama, ditambah field baru dan empat operasi baru (`GET /orders?customerId=`, `GET /payments?orderId=`, `DELETE /carts/{cartId}/items`, dan `POST /orders/{orderId}/cancel`). `GET /products` mendukung query parameter opsional `name`, `page`, `size` serta header `X-Total-Count`. `/api/v1` tetap didukung penuh pada keempat service.

Base URL publik melalui Istio Ingress Gateway adalah `http://127.0.0.1:18080/api/v1` dan `http://127.0.0.1:18080/api/v2`. Saat menjalankan service langsung di host tanpa gateway, gunakan port masing-masing (`8081–8084`).

## Database per service

Product menggunakan soft delete sesuai DEC-011: data tetap tersimpan di tabel `products` dengan `active=false`, sementara lookup/list katalog hanya membaca produk aktif.

```text
product-service → product_db
cart-service    → cart_db
order-service   → order_db
payment-service → payment_db
```

Tiap service menguasai data dan kredensialnya sendiri. Relasi lokal seperti Cart–CartItem atau Order–OrderItem memakai foreign key internal, sedangkan referensi `productId`/`orderId` lintas layanan tidak dibuat sebagai JOIN atau foreign key lintas database.

| Database | Tabel utama |
|---|---|
| `product_db` | `products` |
| `cart_db` | `carts`, `cart_items` |
| `order_db` | `orders`, `order_items` |
| `payment_db` | `payments` |

Detail version, timestamp, constraint, dan idempotency mengikuti [keputusan desain](docs/architecture/decisions.md).

## Konsep integrasi

Komunikasi awal menggunakan REST sinkron:

```text
Cart    → Product       : lookup informasi produk
Order   → Cart          : membaca item keranjang
Order   → Product       : memperoleh data snapshot/validasi checkout pada rancangan internal
Payment → Order         : membaca informasi pesanan
```

Harga checkout mengikuti harga yang disimpan saat produk masuk Cart (OPEN-003). Lookup Product dapat digunakan untuk data produk/validasi, tetapi tidak menggantikan harga Cart dengan harga katalog terbaru. Cara membawa harga satuan tersimpan dari Cart ke Order dan stock reservation masih perlu diselesaikan.

Panggilan antarlayanan dilakukan melalui REST adapter yang mengimplementasikan output port. Timeout/kegagalan dependency harus ditangani secara eksplisit. Transaksi SQL satu service tidak otomatis membatalkan perubahan service lain.

## Struktur project

```text
ABL-Project/
├── README.md
├── .gitignore
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/
├── services/
│   ├── product-service/
│   ├── cart-service/
│   ├── order-service/
│   ├── payment-service/
│   └── security-support/
├── specs/
│   ├── openapi.yaml
│   ├── openapi-v2.yaml
│   └── payment-simulator.openapi.yaml
├── docs/
│   ├── architecture/
│   ├── guides/
│   ├── testing/
│   └── archive/
├── deploy/              # Helm umbrella chart, Istio mesh, Keycloak (auth/), & k8s DB manifests
├── scripts/             # Otomatisasi deploy-local.sh, auth-local.sh, auth-token.py, run-local-service.sh
└── tests/               # Pengujian functional, integration, security, resilience, load (k6), & scripts
```

Lihat [architecture.md](docs/architecture/architecture.md) untuk struktur internal tiap module.

## Quick start Minikube + Istio + Keycloak

Prasyarat di WSL2/Linux: **Docker (Linux containers)**, **Minikube**, **Helm 3**, **istioctl**, **Python 3**, dan **openssl**.

```bash
# 1. Inisialisasi cluster Minikube, Istio control plane, dan namespace ecommerce
bash scripts/deploy-local.sh init

# 2. Pasang Keycloak (namespace identity) dan buka port-forward di terminal terpisah (18090)
bash scripts/auth-local.sh up
bash scripts/auth-local.sh gateway
```

Pada terminal utama baru (biarkan `auth-local.sh gateway` tetap berjalan):

```bash
# 3. Build 4 image service, deploy 4 PostgreSQL StatefulSet, Helm chart, dan Istio Gateway
bash scripts/deploy-local.sh up payment

# 4. Buka Istio Ingress Gateway di port 18080 (terminal terpisah)
bash scripts/deploy-local.sh gateway
```

Verifikasi cluster dan smoke check melalui gateway:

```bash
bash scripts/deploy-local.sh status
AUTH_TOKEN=$(python3 scripts/auth-token.py api-admin) bash scripts/deploy-local.sh check
```

Untuk deployment per tahap (`product`, `cart`, `order`, `payment`), opsi `--only=<service>`, atau `--skip-build`, lihat [deploy/README.md](deploy/README.md).

## Persiapan development

Dari Ubuntu/WSL:

```bash
cd ~/ABL-Project
java -version
./mvnw -version
./mvnw validate
```

Pastikan Maven memakai Java yang sesuai baseline project. `validate` memeriksa konfigurasi Maven; pemeriksaan kompilasi dapat dilakukan terpisah:

```bash
./mvnw -pl services/product-service -am compile
```

Sebelum menjalankan aplikasi, siapkan database, migrasi, dan datasource sesuai [development.md](docs/guides/development.md). `.env` tidak otomatis dibaca oleh Spring Boot. Gunakan environment terminal atau run configuration IDE secara eksplisit.

## Menjalankan infrastruktur lokal

Tahap sekarang menggunakan development lokal; cluster belum perlu dibuat. Periksa Docker:

```bash
docker info
docker ps -a
```

Contoh pembuatan PostgreSQL lokal tersedia pada [panduan database development](docs/guides/development.md#3-validasi-dan-jalankan-database). Rancangan empat container menggunakan port host berikut:

| Container lokal contoh | Port host → container |
|---|---|
| abl-product-db | 127.0.0.1:5433 → 5432 |
| abl-cart-db | 127.0.0.1:5434 → 5432 |
| abl-order-db | 127.0.0.1:5435 → 5432 |
| abl-payment-db | 127.0.0.1:5436 → 5432 |

Tabel adalah konfigurasi yang dituju, bukan daftar container yang sudah terbukti berjalan. Compose database lokal tersedia pada `compose.local.yaml`; Helm chart aplikasi tersedia pada `deploy/helm/ecommerce/`.

## Memeriksa database

Jika memakai nama container dan kredensial lokal contoh pada panduan development, masuk ke Product DB:

```bash
docker exec -it abl-product-db psql -U product_user -d product_db
```

Di dalam `psql`:

```sql
SELECT current_database();
```

```text
\dt
\q
```

`\dt` menampilkan tabel dan `\q` keluar dari psql. Ganti container/user/database untuk memeriksa service lain. Tabel bisnis baru muncul setelah migrasi terkait ditulis dan dijalankan; tidak ada seed bisnis yang diasumsikan tersedia.

## Menjalankan service Spring Boot

Jalankan dari root repository pada **terminal berbeda untuk setiap service**. Contoh berikut memakai environment datasource standar Spring Boot. Database serta migrasi yang sesuai harus siap terlebih dahulu.

### Product Service

```bash
read -r -s -p 'Password DB Product: ' DB_PASSWORD; printf '\n'
SERVER_PORT=8081 \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/product_db' \
SPRING_DATASOURCE_USERNAME=product_user \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
./mvnw -pl services/product-service spring-boot:run
```

### Cart Service

```bash
read -r -s -p 'Password DB Cart: ' DB_PASSWORD; printf '\n'
SERVER_PORT=8082 \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5434/cart_db' \
SPRING_DATASOURCE_USERNAME=cart_user \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
./mvnw -pl services/cart-service spring-boot:run
```

### Order Service

```bash
read -r -s -p 'Password DB Order: ' DB_PASSWORD; printf '\n'
SERVER_PORT=8083 \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5435/order_db' \
SPRING_DATASOURCE_USERNAME=order_user \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
./mvnw -pl services/order-service spring-boot:run
```

### Payment Service

```bash
read -r -s -p 'Password DB Payment: ' DB_PASSWORD; printf '\n'
SERVER_PORT=8084 \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5436/payment_db' \
SPRING_DATASOURCE_USERNAME=payment_user \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
./mvnw -pl services/payment-service spring-boot:run
```

URL client antarlayanan juga perlu dikonfigurasi pada consumer; lihat [konfigurasi client](docs/guides/development.md#5-jalankan-service). Environment di satu terminal tidak otomatis tersedia pada terminal lain.

## Dokumentasi API

| Versi | Prefix | Kontrak | Status |
|---|---|---|---|
| 1.0.0 | `/api/v1` | [specs/openapi.yaml](specs/openapi.yaml) | Diimplementasikan dan tetap didukung |
| 2.0.0 | `/api/v2` | [specs/openapi-v2.yaml](specs/openapi-v2.yaml) | Diimplementasikan dan diuji pada seluruh 4 service |

Buka file tersebut pada Swagger Editor/validator yang dipakai tim untuk memeriksa struktur endpoint dan schema. Ringkasan perubahan 1.0.0 → 2.0.0 ada pada `info.description` kontrak v2 dan [DEC-020–DEC-023](docs/architecture/decisions.md#dec-020-api-versi-200-dengan-url-path-versioning).

Swagger UI di masing-masing aplikasi belum dikonfirmasi terpasang. URL `/swagger-ui.html` tidak diasumsikan tersedia. Kontrak statis ini sudah dapat dipakai sebagai acuan implementasi dan pengujian.

Beberapa bagian kontrak API masih perlu dilengkapi, terutama content respons tambahan dan atribut respons PATCH. Temuannya ada pada [business-rules.md](docs/architecture/business-rules.md#6-keterbatasan-kontrak-sumber). Perubahan kontrak dicatat di [decisions.md](docs/architecture/decisions.md).

## Flow pengujian alur utama

**Prasyarat:** Minikube + Istio Gateway (`18080`) dan Keycloak (`18090`) sudah berjalan. Mutasi Product membutuhkan role `ADMIN`, sedangkan Cart/Order/Payment menggunakan token `CUSTOMER` (`customer-a` memiliki `customer_id=101`) serta header `Idempotency-Key` pada `POST /payments`.

Jalankan dari terminal pengujian dengan `curl` dan `jq`:

### 1. Ambil token dan tambahkan produk (`ADMIN`)

```bash
BASE_URL='http://127.0.0.1:18080/api/v1'
ADMIN_TOKEN=$(python3 scripts/auth-token.py api-admin)
CUSTOMER_TOKEN=$(python3 scripts/auth-token.py customer-a)

PRODUCT_JSON=$(curl --fail --silent --show-error \
  -X POST "$BASE_URL/products" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"name":"Keyboard Mechanical","price":350000,"stock":20}')
printf '%s\n' "$PRODUCT_JSON" | jq
PRODUCT_ID=$(printf '%s\n' "$PRODUCT_JSON" | jq -er '.id')
```

Target HTTP: **201**, respons Product dengan ID dari server.

### 2. Baca produk yang baru dibuat

```bash
curl --fail --silent --show-error \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "$BASE_URL/products/$PRODUCT_ID" | jq
```

Target HTTP: **200**, atribut produk sesuai data yang disimpan.

### 3. Buat Cart kosong, lalu tambahkan item (`CUSTOMER`)

```bash
CART_JSON=$(curl --fail --silent --show-error \
  -X POST "$BASE_URL/carts" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"customerId":101}')
printf '%s\n' "$CART_JSON" | jq
CART_ID=$(printf '%s\n' "$CART_JSON" | jq -er '.id')

curl --fail --silent --show-error \
  -X POST "$BASE_URL/carts/$CART_ID/items" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "{\"productId\":$PRODUCT_ID,\"quantity\":2}" | jq
```

Target HTTP: **201** untuk create Cart dan tambah item (subtotal/total 700000).

### 4. Buat pesanan (`CUSTOMER`)

```bash
ORDER_JSON=$(curl --fail --silent --show-error \
  -X POST "$BASE_URL/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "{\"cartId\":$CART_ID,\"customerId\":101}")
printf '%s\n' "$ORDER_JSON" | jq
ORDER_ID=$(printf '%s\n' "$ORDER_JSON" | jq -er '.id')
```

Target HTTP: **201**, Order dengan status awal `PENDING`.

### 5. Buat permintaan pembayaran (`CUSTOMER` + `Idempotency-Key`)

```bash
PAYMENT_JSON=$(curl --fail --silent --show-error \
  -X POST "$BASE_URL/payments" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: demo-pay-$ORDER_ID" \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":$ORDER_ID,\"method\":\"E_WALLET\"}")
printf '%s\n' "$PAYMENT_JSON" | jq
PAYMENT_ID=$(printf '%s\n' "$PAYMENT_JSON" | jq -er '.id')
```

Target HTTP: **201**, Payment dengan status `PENDING`.

### 6. Baca Order dan Payment

```bash
curl --fail --silent --show-error -H "Authorization: Bearer $CUSTOMER_TOKEN" "$BASE_URL/orders/$ORDER_ID" | jq
curl --fail --silent --show-error -H "Authorization: Bearer $CUSTOMER_TOKEN" "$BASE_URL/payments/$PAYMENT_ID" | jq
```

Target HTTP: **200** untuk resource yang sudah dibuat.

## Flow pengujian perubahan data

Contoh memakai variabel dan token dari alur utama:

```bash
curl -i -X PATCH "$BASE_URL/products/$PRODUCT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"price":375000}'

curl -i -X PUT "$BASE_URL/carts/$CART_ID/items/$PRODUCT_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"quantity":3}'

curl -i -X PATCH "$BASE_URL/orders/$ORDER_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"shippingAddress":"Jl. Veteran No. 10, Malang"}'

curl -i -X PATCH "$BASE_URL/payments/$PAYMENT_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"transactionReference":"TRX-2026-0001"}'
```

Target HTTP: **200** jika aturan resource mengizinkan.

## Flow pengujian gagal

### 1. Harga/stok Product tidak valid

```bash
curl -i -X POST "$BASE_URL/products" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"name":"Produk Uji","price":-1,"stock":-1}'
```

Target HTTP: **400**. Harga dan stok negatif melanggar schema input API.

### 2. Quantity Cart tidak valid

```bash
curl -i -X POST "$BASE_URL/carts/$CART_ID/items" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "{\"productId\":$PRODUCT_ID,\"quantity\":0}"
```

Target HTTP: **400**. Quantity input minimal 1.

### 3. Quantity update melebihi stok

Pada fixture Product dengan stok 20:

```bash
curl -i -X PUT "$BASE_URL/carts/$CART_ID/items/$PRODUCT_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"quantity":21}'
```

Target HTTP: **409** untuk kondisi quantity melebihi stok.

### 4. Method pembayaran tidak valid

```bash
curl -i -X POST "$BASE_URL/payments" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: demo-invalid-$ORDER_ID" \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":$ORDER_ID,\"method\":\"UNKNOWN\"}"
```

Target HTTP: **400**. Method yang diizinkan adalah `TRANSFER`, `E_WALLET`, `COD`, dan `VA_BCA` (lihat kontrak OpenAPI).

### 5. Resource tidak ditemukan dan operasi DELETE

Gunakan ID yang tidak ada untuk menguji **404**. Operasi `DELETE` yang diizinkan mengembalikan **204 tanpa body** (`DELETE /products/{id}` dengan `ADMIN_TOKEN`, atau `DELETE /carts/{cartId}/items/{productId}` dengan `CUSTOMER_TOKEN`). Request tanpa token mengembalikan **401**, sedangkan akses lintas `customer_id` atau role yang tidak sesuai mengembalikan **403**.

Error JSON menggunakan atribut `code` dan `message`. Contoh:

```json
{
  "code": "NOT_FOUND",
  "message": "Data tidak ditemukan"
}
```

Rincian whitelist PATCH, null semantics, dan error dependency perlu diselaraskan pada kontrak sebelum ekspektasi tests diperketat.

## Pengujian dan bukti demo

Menjalankan seluruh unit test dan integration test Java (Testcontainers PostgreSQL 17):

```bash
./mvnw -B -ntp verify
```

Untuk menjalankan suite pengujian otomatis pada direktori `tests/` (`functional/`, `integration/`, `security/`, `resilience/`, `load/`, dan `scripts/`), lihat panduan lengkap di [tests/README.md](tests/README.md).

## Status implementasi dan verifikasi

| Bagian | Status |
|---|---|
| Multi-module Maven (`security-support` + 4 services) | Tersedia; `./mvnw -B -ntp verify` lulus pada seluruh 5 modul |
| Struktur package domain/usecase/adapter (Hexagonal) | Terimplementasi penuh pada seluruh 4 service |
| Kontrak OpenAPI v1 & v2 | Diimplementasikan dan terverifikasi pada seluruh 4 service |
| Keamanan aplikasi (Keycloak + JWT RS256 + ownership + token relay) | Terimplementasi via `security-support` dan terverifikasi oleh `tests/security/jwt-e2e.py` |
| Docker multi-stage build | 4 Dockerfile teruji dan dimuat ke image Minikube |
| Cluster/deployment Minikube & Istio | Helm umbrella chart teruji di Minikube WSL2: mTLS `STRICT`, L7 RBAC, Outlier Detection CB, Gateway `18080` |
| Automated test suites (`tests/`) | Functional v2, Integration E2E & negative, Security Mesh & JWT, Resilience rollout & failover, Load k6, & Script regression |

## Dokumentasi lanjutan

- [Deploy Minikube & Istio](deploy/README.md): langkah `deploy-local.sh init`, `up`, `status`, `gateway`, dan `check`.
- [Panduan JWT & Keycloak](docs/guides/jwt-authentication.md): setup `auth-local.sh`, role `ADMIN`/`CUSTOMER`, kepemilikan `customer_id`, dan token relay.
- [Panduan Suite Pengujian](tests/README.md): cara menjalankan seluruh suite di `tests/`.
- [Architecture](docs/architecture/architecture.md): pola arsitektur, tanggung jawab layer, dan alur data.
- [Business rules](docs/architecture/business-rules.md): aturan bisnis dan batasan kontrak.
- [Development](docs/guides/development.md): environment, database, migrasi, dan startup lokal tanpa Kubernetes.
- [Testing](docs/testing/testing.md): matriks operasi dan pencatatan hasil pengujian.
- [Decisions](docs/architecture/decisions.md): keputusan arsitektur dan kontrak API.

Contoh ID dan nominal pada panduan ini adalah ilustrasi. Sesuaikan dengan fixture dan gunakan identifier yang dikembalikan aplikasi saat menjalankan pengujian.

## Build image dan template deployment

Empat Dockerfile tersedia pada tiap folder service dan Helm chart aplikasi di `deploy/helm/ecommerce/`. Command berikut sama pada Windows PowerShell dan Linux Bash, dari root repository dengan Docker Linux container engine aktif:

```console
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:0.2.0-local .
helm lint deploy/helm/ecommerce
helm template abl deploy/helm/ecommerce --namespace ecommerce --output-dir rendered
```

Lihat [panduan deployment Windows dan Linux](docs/guides/deployment.md) untuk prasyarat, build semua image, konfigurasi database/Secret, dan langkah cluster nanti. Template belum membuktikan workload atau API bisnis berjalan.
