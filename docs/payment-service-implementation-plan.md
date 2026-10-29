# Tahapan, skill, dan prompt implementasi Payment Service

Dokumen ini adalah rencana pekerjaan, bukan hasil implementasi.

Tahap 0 telah dirumuskan pada [payment-service-technical-design.md](payment-service-technical-design.md) dan DEC-018; kontrak simulator tersedia di [payment-simulator.openapi.yaml](../specs/payment-simulator.openapi.yaml). Kedua kontrak lolos validasi OpenAPI. Tahap 1 (domain), tahap 2 (commands, ports, tipe aplikasi), dan tahap 3 (migrasi/JPA entities) selesai dengan 91 tests gabungan lulus: 62 unit dan 29 tests schema/mapping pada PostgreSQL 17.11 terisolasi. Rincian dan batas hasil ada pada [payment-service-testing.md](payment-service-testing.md). Tahap 4 (repository) selesai dengan total 103 tests lulus, termasuk 12 tests repository PostgreSQL. Tahap 5–10 belum dikerjakan; adapter transaksi/locking dan endpoint belum diimplementasikan.

Acuan urutan: bagian 4 dan 5 pada `.codex/alur-pekerjaan-developer-setiap-service.md`. Acuan perilaku: [decisions.md](decisions.md), khususnya DEC-012 sampai DEC-017, [business-rules.md](business-rules.md), dan [OpenAPI](../specs/openapi.yaml). Alur lama dipertahankan; idempotency dan simulator ditambahkan sesuai keputusan terbaru. Rujukan historis di `.codex` tidak menggantikan kontrak/keputusan terbaru.

## Cara memakai prompt

Jalankan tahap 0, kemudian tahap 1–10 secara berurutan. Salin pembuka bersama berikut pada sesi implementasi, lalu tambahkan prompt tahap yang sedang dikerjakan. Pada sesi baru, sertakan kembali pembuka bersama. Tests dibuat bersama komponennya; tahap 10 menjalankan verifikasi gabungan.

```text
Konteks proyek: D:\GitHub_Local_Project\e-commerce-ABL.
Baca docs/payment-service-implementation-plan.md,
docs/decisions.md, docs/business-rules.md, docs/architecture.md,
docs/skill-installation-review.md, dan specs/openapi.yaml.
Periksa kondisi kode terbaru sebelum mengubahnya.

Ikuti Java 17 dan versi dependency dari POM. Pertahankan struktur
domain/usecase/adapter, port di usecase.port.out, ID integer,
domain terpisah dari JPA, dan error {code,message}.
Contoh skill perlu disesuaikan dengan konvensi proyek.

Baseline Payment: amount dari total akhir Order, BigDecimal/NUMERIC
tanpa pembulatan diam-diam, status awal PENDING, hanya Order PENDING
yang layak, maksimal satu attempt aktif dan tidak ada attempt baru
setelah SUCCESS. Reference hanya dapat berubah selama PENDING.

Idempotency: header wajib, retensi hasil create sukses 24 jam,
replay status/body awal, payload berbeda dengan key sama ditolak,
dan duplikat yang sedang diproses ditolak sementara 409.
Ini bukan antrean FIFO. State operasi create berbeda dari Payment.

Simulator hanya development/test dan terpisah dari PATCH reference.
Payment tidak otomatis mengubah Order pada milestone ini.
Order API boleh memakai stub untuk tests terisolasi.

Kerjakan tahap yang diminta saja, pertahankan perubahan pengguna,
dan tulis tests yang relevan bersama komponen yang dibuat.
Laporkan file yang berubah, alasan desain, command verifikasi,
hasil aktual, dan batas verifikasi. Gunakan $systematic-debugging
jika ada error; rujukan skill tambahan yang belum terpasang
tidak boleh diklaim telah tersedia.
```

## Ringkasan alur

| Tahap | Pekerjaan / hasil | Alasan | Skill utama |
|---:|---|---|---|
| 0 | Desain transaksi, idempotency, dan kontrak simulator | Mencegah migrasi dan port dibangun di atas asumsi berbeda | hexagonal-architecture, spring-data-jpa |
| 1 | Domain Payment, enum, dan OrderSnapshot | Memusatkan invariant tanpa framework | hexagonal-architecture, testing-pyramid |
| 2 | Commands dan output ports | Mendefinisikan kebutuhan aplikasi sebelum adapter | hexagonal-architecture, testing-pyramid |
| 3 | Migrasi dan JPA entities | Menyediakan schema yang konsisten dengan model dan concurrency | flyway-migrations, spring-data-jpa, testing-pyramid |
| 4 | Spring Data repositories | Menyediakan query lokal untuk adapter | spring-data-jpa, testing-pyramid |
| 5 | Persistence mapper dan adapter | Menghubungkan port ke PostgreSQL dan membuktikan transaksi/locking | hexagonal-architecture, spring-data-jpa, testing-pyramid |
| 6 | Order REST adapter | Membaca Order melalui API, bukan database layanan lain | http-interface-clients, testing-pyramid |
| 7 | Use cases bisnis dan hasil simulasi | Mengoordinasikan domain dan dependency | hexagonal-architecture, testing-pyramid |
| 8 | DTO, HTTP mapper, controller, dan simulator lokal | Menghubungkan kontrak HTTP dengan use case | hexagonal-architecture, testing-pyramid |
| 9 | Global exception handler | Menyediakan error/status yang konsisten | hexagonal-architecture, testing-pyramid |
| 10 | Verifikasi aplikasi Payment secara gabungan | Membuktikan komponen bekerja bersama | testing-pyramid |

## Tahap 0 — Persiapan desain teknis

Hasil: catatan desain dan kontrak yang cukup untuk tahap berikutnya. Belum menulis implementasi bisnis.

```text
Gunakan $hexagonal-architecture dan $spring-data-jpa.
Kerjakan tahap 0 rencana Payment.

Petakan keputusan bisnis ke komponen dan rancangan penyimpanan.
Tentukan scope/validasi key, canonicalization orderId/method,
state klaim idempotency, penyimpanan hasil replay, transaksi atomik
Payment+hasil sukses, serta recovery proses yang terputus.

Rancang perlindungan maksimal satu PENDING dan penolakan attempt
setelah SUCCESS, termasuk saat key berbeda dan request bersamaan.
Jangan menahan transaksi/lock SQL selama lookup HTTP tanpa kebutuhan
yang jelas. Jelaskan penanganan status commit yang belum diketahui.

Rancang route/schema simulator terpisah, profil+flag aktivasi,
pembatasan akses lokal, dan pemrosesan hasil berulang/bertentangan.
Selaraskan pemetaan error Order API, konflik attempt, dan error input
pada kontrak agar tahap controller memiliki acuan lengkap.

Dokumentasikan pilihan teknis dalam docs/ dan pisahkan kontrak
simulator development/test dari kontrak bisnis publik.
Pertahankan aturan bisnis yang sudah diterima tanpa menambahkan
refund, provider nyata, atau sinkronisasi status Order.
```

## Tahap 1 — Domain

Hasil: `Payment`, `PaymentMethod`, `PaymentStatus`, snapshot Order milik Payment, dan exception domain yang diperlukan.

```text
Gunakan $hexagonal-architecture dan $testing-pyramid.
Kerjakan tahap 1 rencana Payment: domain dan unit tests tanpa
Spring context, HTTP, atau database.

Implementasikan aturan amount/status awal dan method yang tersedia,
perubahan reference hanya selama PENDING, serta penerimaan hasil
PENDING ke SUCCESS/FAILED. Hasil final identik tidak menimbulkan
perubahan tambahan; hasil final bertentangan ditolak.

Pisahkan invariant satu Payment dari policy lintas attempt yang
memerlukan penyimpanan. Snapshot Order dimiliki Payment Service.
Gunakan exception domain yang bermakna tanpa status HTTP di domain.
Verifikasi nilai desimal tetap utuh dan jalankan unit tests terkait.
```

## Tahap 2 — Commands dan output ports

Hasil: commands create/PATCH/hasil simulasi, `PaymentStore`, `OrderLookup`, dan port idempotency atau transaksi lokal sesuai desain tahap 0.

```text
Gunakan $hexagonal-architecture dan $testing-pyramid.
Kerjakan tahap 2 rencana Payment.

Buat command create, patch reference, dan pemrosesan hasil simulasi.
Definisikan PaymentStore, OrderLookup, serta kebutuhan idempotency
dan operasi atomik sesuai desain tahap 0.

Port harus menyatakan kebutuhan aplikasi dengan domain/tipe aplikasi,
bukan JpaRepository, DTO remote, RestClient, atau ResponseEntity.
Hindari kontrak check-then-insert yang mengklaim atomik padahal tidak.
Pisahkan hasil create baru, replay, dan konflik sementara.
Tulis tests untuk validasi command/tipe aplikasi yang memiliki logika.
```

## Tahap 3 — Migrasi dan JPA entities

Hasil: migrasi `payments`, penyimpanan idempotency sesuai desain, JPA entities terpisah dari domain, dan schema tests.

Status: selesai. `V1__create_payment_storage.sql` membuat payments, payment_order_guards, dan payment_idempotency. Mapping JPA tervalidasi dengan Flyway/PostgreSQL 17; tests juga memeriksa presisi amount, state/snapshot idempotency, unique index saat dua koneksi bersamaan, dan optimistic locking. Ini belum membuktikan protokol transaksi/claim/fencing adapter yang baru dikerjakan pada tahap 4–5.

```text
Gunakan $flyway-migrations, $spring-data-jpa, dan $testing-pyramid.
Kerjakan tahap 3 rencana Payment.

Periksa migrasi yang sudah ada, lalu buat versi berikutnya yang benar.
Implementasikan schema Payment dan catatan idempotency sesuai tahap 0,
termasuk uniqueness/constraint/index untuk key dan policy attempt.
Jangan menggunakan UNIQUE(order_id) untuk seluruh riwayat attempt.

Simpan amount menggunakan NUMERIC yang mempertahankan nilai dari
Order; mapping JPA tidak boleh memangkas skala tanpa disadari.
orderId adalah referensi eksternal, tanpa foreign key ke database Order.

Siapkan dependency pengujian PostgreSQL/Testcontainers yang sesuai POM
dan tests migrasi/constraint pada PostgreSQL 17 terisolasi.
Jangan mengubah migrasi yang telah diterapkan atau memodifikasi DB dev.
```

## Tahap 4 — Repository

Hasil: `PaymentsRepository` dan repository idempotency jika rancangan membutuhkannya.

Status: selesai. PaymentsRepository, PaymentOrderGuardsRepository, PaymentIdempotencyRepository, dan helper PaymentDatabaseSession tersedia. Query lock/mutation mewajibkan transaksi caller; lock budget disetel transaction-local sebelum operasi. Dua belas tests repository PostgreSQL lulus bersama 91 tests tahap sebelumnya. Protokol claim/fencing dan implementasi output port tetap tahap 5.

```text
Gunakan $spring-data-jpa dan $testing-pyramid.
Kerjakan tahap 4 rencana Payment.

Implementasikan query repository untuk kebutuhan PaymentStore dan
catatan idempotency, mengikuti schema tahap 3. Query lookup ID,
attempt per Order, hasil replay, dan operasi locking harus sesuai
desain tahap 0; jelaskan operasi yang memerlukan transaksi.

Repository hanya memakai JPA entity lokal. Core aplikasi tetap
mengakses output port, bukan repository ini secara langsung.
Uji query dan constraint relevan memakai PostgreSQL terisolasi.
```

## Tahap 5 — Persistence mapper dan adapter

Hasil: `PaymentPersistenceMapper`, `PaymentPersistenceAdapter`, serta implementasi port penyimpanan idempotency/operasi atomik.

```text
Gunakan $hexagonal-architecture, $spring-data-jpa, dan $testing-pyramid.
Kerjakan tahap 5 rencana Payment.

Implementasikan mapping domain-JPA dan adapter untuk port tahap 2.
Pertahankan ID, amount, reference, status, serta data version/locking
jika dipakai. Implementasikan klaim key dan perlindungan attempt
sesuai desain, serta penyimpanan atomik Payment+hasil create sukses.

Replay memakai hasil awal tersimpan, bukan membentuk ulang body
dari Payment yang statusnya mungkin sudah berubah. Jangan menyimpan
409 sementara atau error sebelum commit sebagai replay final.

Uji rollback, concurrency, dan recovery dengan PostgreSQL nyata yang
terisolasi. Gunakan sinkronisasi test terkontrol. Uji retensi 24 jam
dengan clock terkontrol; cleanup tidak menghapus Payment maupun
klaim yang masih aktif. Jangan memakai lock in-memory sebagai
satu-satunya perlindungan jika aplikasi memiliki beberapa instance.
```

## Tahap 6 — Order REST adapter

Hasil: `OrderRestAdapter`, DTO remote, mapper snapshot, dan wiring HTTP client.

```text
Gunakan $http-interface-clients dan $testing-pyramid.
Kerjakan tahap 6 rencana Payment.

Implementasikan OrderLookup melalui GET /api/v1/orders/{orderId}.
Petakan DTO remote menjadi OrderSnapshot milik Payment.
Gunakan total dan status dari Order; jangan menghitung ulang biaya.

Hubungkan property clients.order-url dari helper/Helm ke client
yang benar-benar digunakan. Konfigurasikan timeout dan pisahkan
Order 404 dari timeout, koneksi gagal, 5xx, serta respons rusak.

Uji dengan stub HTTP untuk setiap kondisi tersebut. Implementasi
tidak mengimpor entity/repository Order dan tidak membutuhkan
Order nyata untuk pengujian terisolasi tahap ini.
```

## Tahap 7 — Use cases

Hasil: create/get/PATCH Payment dan pemrosesan hasil simulator, dengan unit tests.

```text
Gunakan $hexagonal-architecture dan $testing-pyramid.
Kerjakan tahap 7 rencana Payment.

Implementasikan CreatePaymentUseCase, GetPaymentUseCase,
PatchPaymentUseCase, dan pemrosesan hasil pembayaran simulasi.
Gunakan domain dan output ports yang telah tersedia.

Replay operasi lama harus tetap dapat diperoleh walaupun Order
sudah tidak layak untuk create baru. Untuk operasi baru, lakukan
lookup/validasi Order dan policy attempt sebelum penyimpanan atomik.
Amount berasal dari total Order dan status awal selalu PENDING.

Penolakan key/payload berbeda dan operasi in-progress mengikuti
kontrak. Pergantian metode setelah FAILED memakai attempt+key baru.
Hasil simulasi diterapkan melalui domain, bukan update SQL bebas.
Tidak ada perubahan status Order otomatis.

Uji alur utama/alternatif menggunakan fake/mock ports. Buktikan
kegagalan dependency tidak menyimpan Payment parsial dan hasil
final identik tidak memicu efek tambahan.
```

## Tahap 8 — DTO, mapper, API/controller, dan demo simulator

Hasil: boundary HTTP bisnis dan endpoint simulator khusus development/test.

```text
Gunakan $hexagonal-architecture dan $testing-pyramid.
Kerjakan tahap 8 rencana Payment.

Buat PaymentInput, PaymentReferencePatch, PaymentOutput,
PaymentHttpMapper, PaymentsApi, dan PaymentsApiController sesuai
kontrak. Baca Idempotency-Key wajib di boundary dan petakan ke
input aplikasi. Pertahankan replay status/body awal sesuai I2-A.

PATCH hanya menerima reference non-null/nonblank; tolak body kosong
dan field asing. Input client tidak menentukan amount/status.

Implementasikan boundary simulator sesuai kontrak tahap 0, terpisah
dari PATCH bisnis. Endpoint harus tidak tersedia ketika dinonaktifkan
atau di luar profil yang diizinkan; uji pembatasan akses lokalnya.
Controller memanggil use case, bukan repository atau database langsung.

Tulis tests mapping/validation dan route sukses. Tests error lengkap
dilengkapi setelah handler pada tahap 9; laporkan batas sementara ini.
```

## Tahap 9 — Exception handler

Hasil: respons error bisnis/framework yang konsisten dengan kontrak.

```text
Gunakan $hexagonal-architecture dan $testing-pyramid.
Kerjakan tahap 9 rencana Payment.

Implementasikan ApiError dan GlobalExceptionHandler sesuai
{code,message}. Petakan input invalid, Payment/Order tidak ditemukan,
Order tidak layak, attempt aktif/sudah SUCCESS, key dipakai untuk
payload berbeda, create in-progress, dan hasil simulator bertentangan.
Petakan kegagalan dependency sesuai kontrak tahap 0.

Pertahankan status/header error framework yang sesuai dan hindari
kebocoran stack trace/token pada body publik. Exception domain/usecase
tidak boleh bergantung pada class exception milik adapter.
Jalankan controller/handler tests dan periksa status serta body aktual.
```

## Tahap 10 — Verifikasi gabungan

Hasil: bukti terukur bahwa Payment bekerja dengan PostgreSQL dan stub Order.

```text
Gunakan $testing-pyramid.
Kerjakan tahap 10 rencana Payment.

Jalankan aplikasi Payment dalam pengujian dengan PostgreSQL 17
terisolasi dan stub HTTP Order. Verifikasi POST, GET, PATCH,
amount desimal, simulator SUCCESS/FAILED, hasil final berulang,
state terminal, dan ketidaktersediaan simulator saat dinonaktifkan.

Uji key wajib, replay awal setelah status berubah, mismatch payload,
409 in-progress lalu retry sukses, key berbeda untuk Order sama,
attempt baru setelah FAILED, dan penolakan setelah SUCCESS.
Uji kegagalan sebelum commit versus kegagalan respons setelah commit.

Jalankan Maven verify untuk payment-service dan module yang diperlukan.
Pisahkan hasil tests terisolasi dari integrasi Order nyata yang belum
tersedia. Catat commit, environment, jumlah tests/failure/skipped,
command aktual, serta batas verifikasi dalam docs/payment-service-testing.md.
Selaraskan status implementasi pada dokumentasi berdasarkan bukti.
```

## Tahap lanjutan setelah baseline

- `$devops-engineer`: verifikasi image dan container Payment dengan konfigurasi database/dependency; Dockerfile sudah tersedia, sehingga periksa dulu sebelum membuat ulang.
- `$kubernetes-specialist`: verifikasi Helm, database/Secret, probes, dan deployment cluster sesuai scope yang kemudian diminta.
- Integrasi Order nyata: jalankan ulang alur dengan layanan tersebut setelah tersedia. Keberhasilan stub tidak membuktikan integrasi nyata.

Tahap lanjutan tidak menjadi prasyarat domain/unit tests. Refund, provider nyata, dan perubahan status Order otomatis tetap memerlukan scope serta rancangan tersendiri.
