# Hasil pengujian Product Service

Tanggal: 29 Oktober 2026, 18:34 WIB. Branch: `feat/product-service`.
Basis commit: `f125214`, ditambah perubahan tahap 10 yang belum dikomit pada saat pengujian.

Perintah dari root repository:

```bash
./mvnw -B -ntp -pl services/product-service -am verify
```

Hasil aktual: **BUILD SUCCESS**, 80 tests, 0 failures, 0 errors, 0 skipped. JAR Spring Boot berhasil dibuat di `services/product-service/target/product-service-0.1.0-SNAPSHOT.jar`.

## Environment

- Maven Wrapper 3.10.0, Spring Boot 4.1.2.
- Java runtime 25.0.4.1; kompilasi dengan `release 17`. Runtime Java 17 belum diuji tersendiri.
- Docker Desktop 29.7.2, Testcontainers 2.0.5, PostgreSQL 17.11 dari image `postgres:17`.
- Database dan port dibuat sementara oleh Testcontainers. Tidak memakai database development maupun fixture dari service lain.

## Cakupan dan bukti

| Kelompok test | Jumlah | Hasil |
|---|---:|---|
| Domain Product | 6 | Lulus |
| Representasi command PATCH | 4 | Lulus |
| Enam use case dengan fake port | 14 | Lulus |
| Persistence mapper | 3 | Lulus |
| Persistence adapter dengan PostgreSQL | 3 | Lulus |
| Schema, constraint, dan upgrade V1 ke V2 | 4 | Lulus |
| Spring Data repository dengan PostgreSQL | 3 | Lulus |
| DTO JSON, validation, dan HTTP mapper | 21 | Lulus |
| Controller melalui MockMvc dan PostgreSQL | 16 | Lulus |
| Exception handler | 4 | Lulus |
| Context startup dan lifecycle melalui HTTP nyata | 2 | Lulus |

`ProductApplicationTests` menjalankan Tomcat pada port acak, kemudian memakai Java HttpClient untuk POST, GET daftar/detail, PUT, PATCH, dan DELETE. Test memeriksa status 201/200/204, header Location, format JSON, harga desimal, field PATCH yang tidak dikirim, serta body DELETE yang kosong.

Skenario gagal memeriksa 400 untuk input negatif/null, PATCH kosong, JSON rusak, path bukan integer, dan stok pecahan. Resource hilang dan produk nonaktif menghasilkan 404. Setelah soft delete, baris database tetap ada dengan `active=false`, sementara daftar produk tidak menampilkannya. Test handler memeriksa respons 500 generik tanpa detail exception internal.

Laporan otomatis tersimpan di `services/product-service/target/surefire-reports/`; direktori build tidak dikomit.

## Perbaikan dari pengujian gabungan

1. Context test bawaan gagal karena tidak memiliki datasource. Test kini menyediakan PostgreSQL terisolasi dan tetap menjalankan context aplikasi penuh.
2. Kasus stok `1.5` awalnya menghasilkan 201 karena dipotong menjadi `1` oleh deserialisasi JSON. `spring.jackson.deserialization.accept-float-as-int=false` kini menolak pecahan dengan 400, dibuktikan melalui MockMvc dan HTTP nyata.

## Batas hasil

- Ini verifikasi Product terisolasi; Cart, Order, Payment, integrasi lintas service, load test, dan deployment cluster belum diverifikasi.
- Database development belum dimigrasikan oleh test. Jalankan service dengan datasource lokal untuk menerapkan Flyway pada database development.
- Kondisi 409 pada DELETE belum diterapkan karena policy soft delete tidak melakukan pengecekan transaksi lintas service; lihat DEC-011.
- Penolakan field JSON asing, batas nama/skala harga, optimistic locking, audit perubahan lengkap, dan reaktivasi belum ditetapkan sebagai fitur.
- Warning metadata repository Flyway HTTP 401 muncul, tetapi pengujian dan packaging tetap berhasil. Stack trace pada test 500 merupakan exception yang sengaja dipicu.

Untuk mengulang seluruh verifikasi Product, aktifkan Docker lalu jalankan perintah di atas. Compose database lokal tidak diperlukan untuk tests.

## Regresi Location di belakang gateway

Product mengembalikan Location berupa path relatif `/api/v1/products/{id}`.
Klien me-resolve path tersebut terhadap origin request sehingga host, skema, dan
port publik tetap sesuai pada gateway maupun akses langsung. Format ini sesuai
[RFC 9110 bagian 10.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.2).
`ProductLocationTest` memeriksa tiga origin publik dengan backend tanpa port
publik, query string, dan header forwarded yang tidak dipercaya.
`ProductApplicationTests` juga mengikuti Location hasil POST melalui HTTP nyata.

Sebelum perbaikan, tiga kasus regresi gagal karena Location absolut mengikuti
backend dan menyertakan query request. Setelah perbaikan, seluruh test Product
berhasil dijalankan dengan PostgreSQL Testcontainers pada 5 November 2026.

## Verifikasi Product V2 pada 16 November 2026

Branch `feat/product-v2`, baseline `d1f8e91`. Perintah:

```bash
./mvnw -o -pl services/product-service -am verify
bash tests/scripts/deploy-local-test.sh
helm lint deploy/helm/ecommerce -f deploy/helm/ecommerce/stages/product.yaml
```

Hasil: **119 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
JAR `services/product-service/target/product-service-0.2.0-SNAPSHOT.jar` berhasil
terbentuk. Pada mesin ini Docker Desktop diakses melalui DOCKER_HOST yang
mengarah ke socket desktop-linux; database test bersifat sementara.

Tambahan terhadap 83 test V1 sebelumnya: 2 test domain description, 1 test
upgrade skema V2 ke V3, dan 33 kasus HTTP V2. Test V1 tetap lulus. Cakupan baru
meliputi description null/absent/empty dan maksimum 1000, PUT V1 menjaga
description, PUT V2 mengosongkannya saat absen/null, filter contains literal,
page/size default, X-Total-Count sebelum paginasi, offset page besar, soft delete,
serta mengikuti Location V2 melalui HTTP nyata. Harga pecahan dan status aktif
baris lama dipertahankan saat migrasi.

Pemeriksaan helper deployment: **14 orchestration checks passed** memakai
command doubles, bukan cluster nyata. Helm lint lulus. Route Product mencakup
V1 serta V2. Pengujian ini tidak memperbarui image pada cluster yang pernah
dideploy; build dan rollout V2 harus dijalankan sebelum pengujian gateway V2.
Lihat [panduan Product V2](../archive/product-service-v2.md).

## Verifikasi gateway Product V2 pada cluster lokal

Pada 16 November 2026, image `ecommerce/product-service:0.2.0-local` berhasil
dibangun dan dipasang pada release `abl` revision 3 di cluster `ecommerce-abl`.
Pod aplikasi 2/2 Running dan migrasi V3 berhasil diterapkan pada database lama.
Sebanyak 31 request gateway nyata lulus assertion status, body, dan header
`server: istio-envoy`. Data aktif lama ID 1–3 tidak berubah; product uji ID 4–6
di-soft-delete setelah pengujian, dengan baris historis dipertahankan.
Rincian batas pengujian dan pemeriksaan cluster ada pada bagian hasil deployment
di [panduan Product V2](../archive/product-service-v2.md#hasil-deployment-pada-16-november-2026).
