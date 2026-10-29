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
