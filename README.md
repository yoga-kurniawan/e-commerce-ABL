# E-Commerce Microservices ABL

Backend e-commerce untuk project mata kuliah **Arsitektur Berbasis Layanan**, kelompok *dus bit gut genung*. Sistem dirancang sebagai empat aplikasi terpisah: **Product, Cart, Order, dan Payment**.

Struktur kode dirancang untuk memisahkan model domain, koordinator use case, serta adapter HTTP dan database. Kontrak API menjadi acuan implementasi dan pengujian setiap service.

**Status: tahap skeleton dan pengembangan.** Parent Maven serta empat module sudah lolos `./mvnw validate`; implementasi bisnis, database, dan tests belum dikonfirmasi selesai. Contoh request di bawah adalah panduan pengujian **setelah endpoint terkait diimplementasikan**, bukan hasil eksekusi yang sudah lulus.

## Daftar isi

- [Tech stack](#tech-stack)
- [Daftar service](#daftar-service)
- [Database per service](#database-per-service)
- [Konsep integrasi](#konsep-integrasi)
- [Struktur project](#struktur-project)
- [Persiapan development](#persiapan-development)
- [Menjalankan infrastruktur lokal](#menjalankan-infrastruktur-lokal)
- [Memeriksa database](#memeriksa-database)
- [Menjalankan service Spring Boot](#menjalankan-service-spring-boot)
- [Dokumentasi API](#dokumentasi-api)
- [Flow pengujian alur utama](#flow-pengujian-alur-utama)
- [Flow pengujian perubahan data](#flow-pengujian-perubahan-data)
- [Flow pengujian gagal](#flow-pengujian-gagal)
- [Pengujian dan bukti demo](#pengujian-dan-bukti-demo)
- [Status dan pekerjaan berikutnya](#status-dan-pekerjaan-berikutnya)
- [Dokumentasi lanjutan](#dokumentasi-lanjutan)

## Tech stack

| Kategori | Teknologi | Status/dasar |
|---|---|---|
| Backend | Java 17, Spring Boot 4.1.2 | Baseline project; parent telah divalidasi |
| API | REST, JSON, prefix `/api/v1` | Spesifikasi API |
| Dokumentasi API | OpenAPI 3.0.3, API versi 1.0.0 | Spesifikasi API |
| Arsitektur internal | Domain, use case, ports/adapters | Rancangan internal |
| Database | PostgreSQL 17, database terpisah per service | Pilihan rancangan; runtime belum dikonfirmasi |
| Persistence | Spring Data JPA/Hibernate dan Flyway | Dependency dan rancangan implementasi |
| Build | Maven multi-module dan Maven Wrapper | `validate` berhasil |
| Container | Docker Linux containers | Engine harus aktif pada Windows/Linux yang digunakan |
| Deployment | Kubernetes/Minikube, Istio, Helm | Template Helm aplikasi tersedia; cluster dan Istio ditunda |
| Pengujian HTTP/beban | curl, Postman, Siege | Skenario disiapkan; hasil bisnis/beban belum tersedia |

Autentikasi JWT, role ADMIN/USER, Kafka, DLQ, shipping, dan notification berada di luar cakupan kontrak empat service saat ini. Penambahannya memerlukan desain dan kontrak tersendiri.

## Daftar service

Port berikut merupakan konfigurasi **development langsung pada host**, bukan status bahwa prosesnya sudah berjalan.

| Service | Port lokal | Database | Fungsi | Operasi |
|---|---:|---|---|---:|
| `product-service` | 8081 | `product_db` | Katalog, harga, stok, pembaruan dan penghapusan produk | 6 |
| `cart-service` | 8082 | `cart_db` | Membaca Cart dan mengelola item | 5 |
| `order-service` | 8083 | `order_db` | Membuat/membaca pesanan dan mengubah atribut yang diizinkan | 3 |
| `payment-service` | 8084 | `payment_db` | Membuat/membaca permintaan pembayaran dan mengubah metadata yang diizinkan | 3 |
| **Total** | | | **9 path publik** | **17** |

Base URL publik yang direncanakan adalah `http://localhost:8080/api/v1` melalui pintu masuk API. Saat gateway belum berjalan, gunakan port service masing-masing. Dalam Kubernetes, aplikasi dapat memakai port container 8080 pada Pod berbeda.

## Database per service

Product menggunakan soft delete sesuai DEC-011: data terakhir tetap di tabel `products` dengan `active=false`, sementara lookup/list katalog hanya membaca produk aktif. Ini tidak menyediakan audit seluruh perubahan atau endpoint history. Migrasi V2 menambahkan status aktif tanpa mengubah migrasi V1. Implementasi HTTP masih dikerjakan bertahap.

```text
product-service → product_db
cart-service    → cart_db
order-service   → order_db
payment-service → payment_db
```

Tiap service menguasai data dan kredensialnya sendiri. Relasi lokal seperti Cart–CartItem atau Order–OrderItem dapat memakai foreign key. Referensi productId/orderId lintas layanan tidak dibuat sebagai JOIN atau foreign key lintas database.

Model tabel bisnis berikut masih perlu diwujudkan melalui migrasi dan entity:

| Database | Tabel yang direncanakan |
|---|---|
| product_db | products |
| cart_db | carts, cart_items |
| order_db | orders, order_items untuk rancangan snapshot |
| payment_db | payments |

Skema internal tersebut masih perlu diwujudkan melalui migrasi dan mapping entity. Detail version, timestamp, constraint, retention, dan payment attempt harus mengikuti [keputusan desain](docs/decisions.md).

## Konsep integrasi

Komunikasi awal menggunakan REST sinkron:

```text
Cart    → Product       : lookup informasi produk
Order   → Cart          : membaca item keranjang
Order   → Product       : memperoleh data snapshot/validasi checkout pada rancangan internal
Payment → Order         : membaca informasi pesanan
```

Rancangan Order ke Product diperlukan karena respons Cart awal tidak menyediakan nama/harga satuan produk secara lengkap. Policy harga checkout dan stock reservation belum selesai ditetapkan.

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
│   └── payment-service/
├── specs/openapi.yaml
├── docs/
│   ├── architecture.md
│   ├── business-rules.md
│   ├── development.md
│   ├── testing.md
│   └── decisions.md
├── deploy/              # Helm chart aplikasi; database dan Istio dilengkapi bertahap
├── scripts/             # Otomatisasi sesuai kebutuhan
└── tests/               # Pengujian lintas service
```

Lihat [architecture.md](docs/architecture.md) untuk struktur internal tiap module. Tree merupakan struktur tujuan; direktori yang ada belum membuktikan implementasi atau konfigurasi sudah lengkap.

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

Sebelum menjalankan aplikasi, siapkan database, migrasi, dan datasource sesuai [development.md](docs/development.md). `.env` tidak otomatis dibaca oleh Spring Boot. Gunakan environment terminal atau run configuration IDE secara eksplisit.

## Menjalankan infrastruktur lokal

Tahap sekarang menggunakan development lokal; cluster belum perlu dibuat. Periksa Docker:

```bash
docker info
docker ps -a
```

Contoh pembuatan PostgreSQL lokal tersedia pada [panduan database development](docs/development.md#4-contoh-menyiapkan-database-product). Rancangan empat container menggunakan port host berikut:

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

URL client antarlayanan juga perlu dikonfigurasi pada consumer; lihat [konfigurasi client](docs/development.md#6-url-dependency-dan-http-client). Environment di satu terminal tidak otomatis tersedia pada terminal lain.

## Dokumentasi API

Kontrak ada di [specs/openapi.yaml](specs/openapi.yaml). Buka file tersebut pada Swagger Editor/validator yang dipakai tim untuk memeriksa struktur endpoint dan schema.

Swagger UI di masing-masing aplikasi belum dikonfirmasi terpasang. URL `/swagger-ui.html` tidak diasumsikan tersedia. Kontrak statis ini sudah dapat dipakai sebagai acuan implementasi dan pengujian.

Beberapa bagian kontrak API masih perlu dilengkapi, terutama content respons tambahan dan atribut respons PATCH. Temuannya ada pada [business-rules.md](docs/business-rules.md#6-keterbatasan-kontrak-sumber). Perubahan kontrak dicatat di [decisions.md](docs/decisions.md).

## Flow pengujian alur utama

**Prasyarat:** empat endpoint bisnis sudah dibuat, database tersedia, dependency URL benar, dan tersedia Cart fixture kosong. Angka customerId 101/cartId 10 berikut hanya contoh data uji, bukan akun atau seed yang sudah disediakan. Tidak ada endpoint create Cart atau akun default dalam kontrak awal.

Jalankan dari terminal pengujian dengan `curl` dan `jq`. Semua contoh respons/status di bagian ini merupakan hasil yang diharapkan untuk fixture yang sesuai, bukan hasil pengukuran.

### 1. Tambahkan produk

```bash
PRODUCT_URL='http://localhost:8081/api/v1'
CART_URL='http://localhost:8082/api/v1'
ORDER_URL='http://localhost:8083/api/v1'
PAYMENT_URL='http://localhost:8084/api/v1'

PRODUCT_JSON=$(curl --fail --silent --show-error \
  -X POST "$PRODUCT_URL/products" \
  -H 'Content-Type: application/json' \
  --data '{"name":"Keyboard Mechanical","price":350000,"stock":20}')
printf '%s\n' "$PRODUCT_JSON" | jq
PRODUCT_ID=$(printf '%s\n' "$PRODUCT_JSON" | jq -er '.id')
```

Target HTTP: **201**, respons Product dengan ID dari server. Jangan mengharuskan ID selalu 1.

### 2. Baca produk yang baru dibuat

```bash
curl --fail --silent --show-error \
  "$PRODUCT_URL/products/$PRODUCT_ID" | jq
```

Target HTTP: **200**, atribut produk sesuai data yang disimpan.

### 3. Tambahkan item ke Cart fixture

Pastikan Cart ID berikut memang ada dan kosong sebelum menjalankan skenario:

```bash
CART_ID=10
curl --fail --silent --show-error \
  -X POST "$CART_URL/carts/$CART_ID/items" \
  -H 'Content-Type: application/json' \
  --data "{\"productId\":$PRODUCT_ID,\"quantity\":2}" | jq
```

Target HTTP: **201**. Untuk fixture tanpa perubahan harga, satu item quantity 2 dengan harga 350000 menghasilkan subtotal/total 700000. Penambahan item tidak otomatis membuktikan stok telah direservasi.

### 4. Buat pesanan

```bash
ORDER_JSON=$(curl --fail --silent --show-error \
  -X POST "$ORDER_URL/orders" \
  -H 'Content-Type: application/json' \
  --data "{\"cartId\":$CART_ID,\"customerId\":101}")
printf '%s\n' "$ORDER_JSON" | jq
ORDER_ID=$(printf '%s\n' "$ORDER_JSON" | jq -er '.id')
```

Target HTTP: **201**, Order dengan total sesuai policy checkout dan status awal sesuai keputusan implementasi yang disepakati.

### 5. Buat permintaan pembayaran

```bash
PAYMENT_JSON=$(curl --fail --silent --show-error \
  -X POST "$PAYMENT_URL/payments" \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":$ORDER_ID,\"method\":\"E_WALLET\"}")
printf '%s\n' "$PAYMENT_JSON" | jq
PAYMENT_ID=$(printf '%s\n' "$PAYMENT_JSON" | jq -er '.id')
```

Target HTTP: **201**, Payment dengan method yang valid dan amount sesuai aturan backend. Rancangan project mengambil amount dari Order. Contoh PENDING adalah permintaan yang belum diverifikasi, bukan bukti transfer uang berhasil.

### 6. Baca Order dan Payment

```bash
curl --fail --silent --show-error "$ORDER_URL/orders/$ORDER_ID" | jq
curl --fail --silent --show-error "$PAYMENT_URL/payments/$PAYMENT_ID" | jq
```

Target HTTP: **200** untuk resource yang sudah dibuat. Gunakan ID aktual yang diperoleh dari respons create.

## Flow pengujian perubahan data

Jalankan pada fixture/state yang mengizinkan perubahan. Contoh memakai variabel dari alur utama:

```bash
curl -i -X PATCH "$PRODUCT_URL/products/$PRODUCT_ID" \
  -H 'Content-Type: application/json' \
  --data '{"price":375000}'

curl -i -X PUT "$CART_URL/carts/$CART_ID/items/$PRODUCT_ID" \
  -H 'Content-Type: application/json' \
  --data '{"quantity":3}'

curl -i -X PATCH "$ORDER_URL/orders/$ORDER_ID" \
  -H 'Content-Type: application/json' \
  --data '{"shippingAddress":"Jl. Veteran No. 10, Malang"}'

curl -i -X PATCH "$PAYMENT_URL/payments/$PAYMENT_ID" \
  -H 'Content-Type: application/json' \
  --data '{"transactionReference":"TRX-2026-0001"}'
```

Target HTTP: **200** jika aturan resource mengizinkan. PATCH alamat/reference dapat menghasilkan **409** bila state transaksi menolak perubahan. Perubahan harga Product setelah create Order tidak seharusnya mengubah snapshot riwayat Order bila rancangan snapshot diterapkan.

## Flow pengujian gagal

### 1. Harga/stok Product tidak valid

```bash
curl -i -X POST "$PRODUCT_URL/products" \
  -H 'Content-Type: application/json' \
  --data '{"name":"Produk Uji","price":-1,"stock":-1}'
```

Target HTTP: **400**. Harga dan stok negatif melanggar schema input API.

### 2. Quantity Cart tidak valid

```bash
curl -i -X POST "$CART_URL/carts/$CART_ID/items" \
  -H 'Content-Type: application/json' \
  --data "{\"productId\":$PRODUCT_ID,\"quantity\":0}"
```

Target HTTP: **400**. Quantity input minimal 1.

### 3. Quantity update melebihi stok

Pada fixture Product dengan stok 20 dan Cart/item yang masih dapat diubah:

```bash
curl -i -X PUT "$CART_URL/carts/$CART_ID/items/$PRODUCT_ID" \
  -H 'Content-Type: application/json' \
  --data '{"quantity":21}'
```

Target HTTP: **409** untuk kondisi quantity melebihi stok. Jika state Cart sudah berubah, siapkan fixture terpisah agar penyebab kegagalan dapat dibedakan.

### 4. Method pembayaran tidak valid

```bash
curl -i -X POST "$PAYMENT_URL/payments" \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":$ORDER_ID,\"method\":\"UNKNOWN\"}"
```

Target HTTP: **400**. Method yang diizinkan adalah TRANSFER, E_WALLET, dan COD.

### 5. Resource tidak ditemukan dan operasi DELETE

Gunakan identifier fixture yang dipastikan tidak ada untuk menguji **404** pada GET detail. Untuk DELETE yang diizinkan, periksa **204 tanpa body**; Product dapat menghasilkan 409 jika policy penghapusan menolak keterkaitannya. PUT/DELETE Order dan Payment tidak termasuk kontrak awal.

Error JSON menggunakan atribut `code` dan `message`. Contoh:

```json
{
  "code": "NOT_FOUND",
  "message": "Data tidak ditemukan"
}
```

Rincian whitelist PATCH, null semantics, dan error dependency perlu diselaraskan pada kontrak sebelum ekspektasi tests diperketat.

## Pengujian dan bukti demo

Setelah datasource/profile pengujian siap:

```bash
./mvnw -pl services/product-service -am test
./mvnw verify
```

Keberhasilan command dengan nol tests tidak membuktikan fitur telah diuji. Integration tests hanya dijalankan otomatis jika lifecycle/plugin-nya dikonfigurasi. Lihat [testing.md](docs/testing.md) untuk matriks 17 operasi dan pencatatan hasil.

Bukti demo yang dapat dikumpulkan setelah implementasi tersedia:

1. Versi Java/Maven dan hasil build/test yang benar-benar dijalankan.
2. Kesiapan container/database sesuai environment.
3. Respons create/read/update/delete beserta status HTTP aktual.
4. Skenario validasi dan konflik yang ditolak.
5. Log aplikasi yang membantu menelusuri request tanpa password/token.
6. Data database sebelum/sesudah operasi yang diuji.

Tidak ada format console log khusus atau Swagger screenshot yang diasumsikan sudah diimplementasikan. Load test dikerjakan setelah endpoint stabil dengan dataset/resource/skenario yang dicatat.

## Status dan pekerjaan berikutnya

Status pengembangan per **29 Oktober 2026**:

| Bagian | Status |
|---|---|
| Empat skeleton Initializr dan parent/module Maven | Tersedia; `validate` berhasil |
| Struktur package domain/usecase/adapter | Mulai disiapkan; kelengkapan kelas bisnis belum diverifikasi |
| Kontrak OpenAPI | Sudah disalin ke project Ubuntu |
| Docker daemon | Periksa pada sistem operasi yang sedang digunakan |
| Database dan endpoint bisnis | Belum dikonfirmasi siap |
| Cluster/deployment | Dockerfile dan Helm chart aplikasi tersedia; runtime cluster belum diuji |
| Tests bisnis dan pengujian beban | Belum ada hasil yang dikonfirmasi |

Prioritas: putuskan isu terbuka, siapkan database/config development, implementasikan Product → Cart → Order → Payment, lalu uji kontrak dan integrasi. Stock reservation, idempotency, dan verifikasi pembayaran final memerlukan rancangan lanjutan.

## Dokumentasi lanjutan

- [Architecture](docs/architecture.md): pola arsitektur, tanggung jawab layer, dan alur data.
- [Business rules](docs/business-rules.md): aturan bisnis dan bagian kontrak yang perlu dilengkapi.
- [Development](docs/development.md): environment, database, migrasi, dan startup lokal.
- [Testing](docs/testing.md): skenario serta format hasil pengujian.
- [Decisions](docs/decisions.md): keputusan yang sudah dipilih dan yang masih terbuka.

Contoh ID dan nominal pada panduan ini adalah ilustrasi. Sesuaikan dengan fixture dan gunakan identifier yang dikembalikan aplikasi saat menjalankan pengujian.

## Build image dan template deployment

Empat Dockerfile tersedia pada tiap folder service dan Helm chart aplikasi di `deploy/helm/ecommerce/`. Command berikut sama pada Windows PowerShell dan Linux Bash, dari root repository dengan Docker Linux container engine aktif:

```console
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:0.1.0-local .
helm lint deploy/helm/ecommerce
helm template abl deploy/helm/ecommerce --namespace ecommerce --output-dir rendered
```

Lihat [panduan deployment Windows dan Linux](docs/deployment.md) untuk prasyarat, build semua image, konfigurasi database/Secret, dan langkah cluster nanti. Template belum membuktikan workload atau API bisnis berjalan.
