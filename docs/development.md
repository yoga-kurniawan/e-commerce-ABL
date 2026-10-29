# Panduan development lokal

[Kembali ke README](../README.md) · [Arsitektur](architecture.md) · [Pengujian](testing.md)

## 1. Ruang lingkup

Panduan ini untuk developer yang sudah membuat skeleton Initializr, parent Maven, dan struktur package APS V3 pada `~/ABL-Project`. Cluster Kubernetes ditunda. Contoh database lokal berikut adalah pilihan pengembangan, bukan perintah yang dinyatakan sudah dijalankan.

Laporan menjelaskan fungsi/API; Java 17, Spring Boot 4.1.2, Maven, PostgreSQL, dan organisasi adapter adalah baseline teknis dari diskusi project.

## 2. Pemeriksaan awal

Jalankan dari root project Linux:

```bash
cd ~/ABL-Project
java -version
./mvnw -version
./mvnw validate
```

Periksa Java yang digunakan Maven, bukan hanya hasil `java -version`. Baseline source/runtime project adalah Java 17. Parent root mengunci Spring Boot 4.1.2; empat child mewarisi konfigurasi parent dengan relativePath `../../pom.xml`.

Command generator memakai ID yang ditawarkan Initializr, tetapi versi dependency Maven mengikuti koordinat artifact. Parent root project ini memakai `4.1.2`, bukan menyalin ID API `.RELEASE` secara otomatis.

Untuk mengompilasi Product beserta parent:

```bash
./mvnw -pl services/product-service -am compile
```

Compile tidak memulai Spring context/database dan tidak menjalankan test suite. Build pertama dapat mengunduh dependency Maven.

## 3. Konfigurasi lokal yang direncanakan

| Service | Port HTTP host | Database | Username lokal contoh | Port DB host |
|---|---:|---|---|---:|
| Product | 8081 | product_db | product_user | 5433 |
| Cart | 8082 | cart_db | cart_user | 5434 |
| Order | 8083 | order_db | order_user | 5435 |
| Payment | 8084 | payment_db | payment_user | 5436 |

Ini konfigurasi pengembangan dengan empat instance PostgreSQL terpisah. Port dalam container PostgreSQL tetap 5432. Tiap service hanya memiliki akses database miliknya; gunakan password berbeda per instance. Kredensial nyata tidak disimpan di Git.

## 4. Contoh menyiapkan database Product

Pastikan Docker aktif dan periksa apakah container sudah pernah dibuat:

```bash
docker info
docker ps -a --filter name=abl-product-db
```

Jika container belum ada, contoh berikut membuat database Product dengan persistent volume. Password dimasukkan secara interaktif:

```bash
read -r -s -p 'Password PostgreSQL Product lokal: ' PRODUCT_DB_PASSWORD
printf '\n'
export PRODUCT_DB_PASSWORD

docker run --detach \
  --name abl-product-db \
  --publish 127.0.0.1:5433:5432 \
  --volume abl-product-data:/var/lib/postgresql/data \
  --env POSTGRES_DB=product_db \
  --env POSTGRES_USER=product_user \
  --env POSTGRES_PASSWORD="$PRODUCT_DB_PASSWORD" \
  postgres:17
```

Jika container sudah ada dan berhenti, jalankan container yang sama dengan `docker start abl-product-db`; jangan membuat ulang atau menghapus volumenya hanya karena name conflict.

Periksa kesiapan:

```bash
docker logs --tail 30 abl-product-db
docker exec abl-product-db pg_isready -U product_user -d product_db
```

`pg_isready` memeriksa kesiapan server; keberhasilan startup aplikasi dan query tetap diperlukan untuk memastikan koneksi/kredensial benar. Pola yang sama dapat digunakan untuk tiga database lain dengan mengganti nama, port, volume, database, user, dan password.

Mengganti environment password pada container yang memakai data lama tidak otomatis mengubah password role di database yang sudah diinisialisasi. Jangan menghapus volume berisi data untuk menyelesaikan masalah kredensial tanpa meninjau penyebabnya.

## 5. Menjalankan service dengan environment Spring Boot

Initializr menghasilkan `application.properties` pada project pengguna. File tersebut boleh dipertahankan. Contoh berikut menggunakan nama environment standar Spring Boot agar tidak bergantung pada placeholder custom yang belum ditulis.

Terminal Product, setelah database dan migrasi yang diperlukan tersedia:

```bash
export SERVER_PORT=8081
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/product_db'
export SPRING_DATASOURCE_USERNAME='product_user'
export SPRING_DATASOURCE_PASSWORD="$PRODUCT_DB_PASSWORD"
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
export SPRING_JPA_OPEN_IN_VIEW=false
export SPRING_FLYWAY_ENABLED=true

./mvnw -pl services/product-service spring-boot:run
```

`PRODUCT_DB_PASSWORD` pada contoh harus sudah berisi password yang benar pada terminal tersebut. Jika menggunakan terminal baru, masukkan kembali nilainya secara aman. Environment yang diekspor pada satu terminal tidak otomatis tersedia pada terminal lain.

Untuk Cart/Order/Payment, gunakan terminal terpisah dan ganti port serta JDBC/kredensial sesuai tabel. Jangan menjalankan empat JVM di host dengan port sama.

Jika memilih placeholder custom seperti `DB_URL`, tulis pemetaannya pada konfigurasi terlebih dahulu, misalnya `spring.datasource.url=${DB_URL}`. Mengekspor `DB_URL` saja tidak otomatis mengisi property datasource pada skeleton Initializr yang belum memiliki pemetaan itu.

File `.env`/`.env.example` juga tidak dibaca otomatis oleh Spring Boot. Gunakan environment terminal, run configuration IDE, atau mekanisme pemuatan yang dipilih secara eksplisit.

## 6. URL dependency dan HTTP client

Untuk rancangan client APS V3, tetapkan property yang dibaca konfigurasi HTTP. Contoh dalam `application.properties` consumer:

```properties
clients.product-url=${PRODUCT_SERVICE_URL:http://localhost:8081}
clients.cart-url=${CART_SERVICE_URL:http://localhost:8082}
clients.order-url=${ORDER_SERVICE_URL:http://localhost:8083}
```

| Consumer | Dependency pada rancangan |
|---|---|
| Cart | Product |
| Order | Cart; Product untuk snapshot/validasi yang dibutuhkan |
| Payment | Order |

Adapter REST mengimplementasikan port lookup; use case tidak memanggil RestClient secara langsung. Tambahkan `spring-boot-starter-restclient` pada consumer bila memakai auto-configured RestClient.Builder. Gunakan timeout dan bedakan resource 404 dari koneksi/timeout/5xx.

Saat nanti berjalan dalam Kubernetes, ganti URL melalui environment menjadi alamat Kubernetes Service. `localhost` dalam Pod bukan alamat service lain.

## 7. Migrasi dan mapping

Lokasi migrasi setiap module:

```text
services/<service>-service/src/main/resources/db/migration/
```

- Jika belum ada migrasi, mulailah dari `V1__...sql`.
- Jika V1 sudah diterapkan, tambahkan V2 dan seterusnya. Skeleton yang dibuat sendiri tidak otomatis memiliki migrasi bootstrap starter lain.
- Flyway mengelola skema; Hibernate `ddl-auto=validate` memeriksa entity JPA terhadap skema.
- Tulis domain dan JPA entity terpisah sesuai APS V3. Domain tidak memakai annotation JPA.
- Gunakan nama tabel `orders` untuk Order. Foreign key hanya antarentity lokal pada database layanan yang sama.
- Tentukan tipe SQL, precision, version/locking, cascade, dan retention berdasarkan keputusan desain. Laporan belum menentukan semua detail tersebut.

Tidak memakai `ddl-auto=update` sebagai pengganti migrasi yang dapat ditelusuri. Validation berhasil pada skeleton tanpa entity bisnis belum membuktikan mapping bisnis sudah benar.

## 8. Urutan pengerjaan

1. Tinjau kontrak dan [keputusan terbuka](decisions.md).
2. Tulis domain dan invariant, command, serta output port.
3. Tulis migrasi, JPA entity, repository, mapper, dan persistence adapter.
4. Implementasikan koordinator use case dan alternatifnya.
5. Implementasikan REST adapter dependency bila diperlukan.
6. Tulis DTO, mapper HTTP, controller/API, dan error handler.
7. Jalankan tests komponen serta HTTP; catat hasil aktual.

Mulai dari Product, lalu Cart, Order, dan Payment. Consumer dapat diuji dengan mock/stub port saat dependency belum selesai; ini tidak menggantikan pengujian integrasi aktual.

## 9. Memeriksa HTTP

Jika Actuator terpasang dan aplikasi berhasil start, contoh pemeriksaan proses Product:

```bash
curl --fail --silent --show-error http://localhost:8081/actuator/health
```

Endpoint bisnis baru diperiksa setelah controller terkait dibuat:

```bash
curl -i http://localhost:8081/api/v1/products
```

Port 8080 pada OpenAPI adalah contoh pintu masuk publik. Tanpa gateway lokal, panggil port service masing-masing. Jangan memanggil 8080 lalu menyimpulkan service 8081 gagal hanya karena belum ada proses ingress/gateway.

## 10. Tests, Git, dan dokumentasi

Jalankan `./mvnw verify` setelah konfigurasi tests siap. Generated context test Initializr dapat gagal bila datasource belum tersedia. Gunakan strategi tests pada [testing](testing.md), tanpa menganggap skip tests sebagai bukti keberhasilan fitur.

`.gitignore` mengecualikan build output dan konfigurasi lokal, tetapi tidak menghapus file yang sudah terlanjur tracked. Pertahankan Maven Wrapper dalam repository. Saat menambah aturan atau endpoint, update OpenAPI dan dokumentasi yang terkait serta catat keputusan pada [decisions](decisions.md).

## 11. Tahap berikutnya

Dockerfile, image tags, manifests database, Helm chart, dan konfigurasi Istio dikerjakan saat memasuki deployment. Folder `deploy/`, `scripts/`, dan `tests/` root adalah tempat hasil pekerjaan tersebut; dokumen ini tidak mengklaim file deployment atau script sudah tersedia.

Pengembangan domain/unit test tidak mensyaratkan cluster aktif. Pengujian repository dan startup aplikasi tetap membutuhkan database atau konfigurasi test yang sesuai.
