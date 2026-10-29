# Desain teknis Payment Service — tahap 0

Status: rancangan implementasi, 29 Oktober 2026. Belum ada class bisnis, migrasi, atau konfigurasi runtime Payment yang dibuat pada tahap ini. Acuan: DEC-012–DEC-017 pada [decisions.md](decisions.md), [alur implementasi](payment-service-implementation-plan.md), [kontrak publik](../specs/openapi.yaml), dan [kontrak simulator](../specs/payment-simulator.openapi.yaml).

## 1. Batas dan komponen

Pertahankan Java 17, dependency management parent POM, ID numerik Long, dan package `id.ac.ub.abl.payment`. Domain tidak mengimpor JPA, Spring HTTP, atau DTO remote. Tidak ada dependency module Java ke Order Service.

| Layer | Komponen yang direncanakan | Peran |
|---|---|---|
| domain | Payment, PaymentMethod, PaymentStatus, OrderSnapshot, domain errors | Amount BigDecimal dari Order; PENDING awal; reference; transisi final |
| usecase.command | CreatePaymentCommand, PatchPaymentCommand, ApplyPaymentResultCommand | Input aplikasi yang sudah dipetakan boundary |
| usecase.port.out | PaymentStore, OrderLookup, IdempotencyStore, CreationResponseEncoder | Persistence lokal, lookup HTTP, klaim/replay, serialisasi snapshot create |
| usecase.service | Create/Get/PatchPaymentUseCase, ApplyPaymentResultUseCase, CommitPaymentCreationService | Orkestrasi dan batas transaksi lokal yang dipanggil melalui bean terpisah |
| adapter.outbound.database | JPA entities, Spring Data repositories, mapper, persistence adapters | Implementasi port dan locking PostgreSQL |
| adapter.outbound.rest | OrderRestAdapter, Order API DTO/mapper, HTTP configuration | GET Order dan pemetaan ke snapshot milik Payment |
| adapter.inbound.rest | PaymentsApi/Controller, DTO, HTTP mapper, response encoder | Validasi, route publik, respons create/replay |
| adapter.inbound.rest.simulator | DevelopmentPaymentResultController | Pemicu simulator yang dibatasi; memanggil use case hasil |
| adapter.exception | GlobalExceptionHandler, ApiError | Mapping exception ke `{code,message}` |

Nama port dapat dipecah saat tahap 2 jika kontrak terlalu besar. `CreationResponseEncoder` menerima snapshot hasil aplikasi dan mengembalikan teks JSON deterministik; adapter HTTP mengimplementasikan serialisasinya. Domain tidak mengenal response HTTP. Metadata replay (kode integer, content type, body teks) adalah catatan operasi aplikasi, bukan ResponseEntity atau domain Payment.

## 2. Key, input, dan canonicalization

- Scope key: satu operasi `CREATE_PAYMENT_V1` dalam satu deployment/payment_db, bukan per orderId. Kombinasi `(operation, key)` unik. Key sama untuk Order berbeda tetap konflik payload. Auth pengguna belum tersedia; scope ini bukan mekanisme otorisasi. Jika auth ditambahkan, scope principal harus dirancang melalui keputusan baru.
- Header wajib, case-sensitive pada nilainya, panjang 1–128 karakter ASCII, pola `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`. Tidak di-trim atau diubah hurufnya. Nama header mengikuti sifat case-insensitive HTTP. Client disarankan memakai UUID v4/string acak; key bukan ID Payment dan tidak memuat data pribadi.
- Body create hanya `orderId` integer yang muat di Long dan `method` enum case-sensitive. Field wajib/null/tipe salah/overflow/field asing menghasilkan 400 sebelum klaim. Duplikasi nama properti JSON ditolak, bukan last-value-wins. Tidak menambahkan endpoint atau fitur autentikasi.
- Canonical payload versi 1 adalah UTF-8 `v1\norderId=<Long desimal>\nmethod=<enum>`. Hash SHA-256 dipakai sebagai fingerprint; simpan juga orderId/method untuk membandingkan nilai aktual. Urutan properti, spasi JSON, dan escape JSON yang menghasilkan string sama tidak membuat operasi berbeda. Angka pecahan bukan orderId valid. Untuk baseline ini tidak ada syarat positivity baru pada kontrak ID; kelayakan resource diperiksa via lookup.
- Payload mismatch pada key yang sudah terikat operasi menghasilkan 409 IDEMPOTENCY_KEY_REUSED, termasuk ketika operasi masih berjalan. Untuk request yang belum valid/berhasil mengklaim key, tidak ada hasil sukses yang direplay.

## 3. Rancangan data lokal

Ini rancangan untuk tahap migrasi, bukan SQL yang telah diterapkan.

| Tabel | Kolom/constraint penting |
|---|---|
| payments | id BIGINT sequence; order_id BIGINT NOT NULL tanpa FK eksternal; method/status VARCHAR enum; amount NUMERIC tanpa fixed scale; transaction_reference TEXT nullable; version BIGINT; created_at/updated_at TIMESTAMPTZ |
| payment_order_guards | order_id BIGINT PRIMARY KEY; baris koordinasi lokal, bukan salinan atau FK Order |
| payment_idempotency | operation, key, order_id, method, fingerprint; state PROCESSING/COMPLETED; owner_token; lease_until; payment_id FK lokal nullable; response_status/content_type/body; completed_at/expires_at; version |

- `payments` memiliki CHECK untuk method/status sesuai enum dan amount non-negatif/finite. Tolak Order response yang totalnya hilang, negatif, atau tidak dapat diparse sebagai BigDecimal dengan 502; jangan memakai default 0. Total nol tetap boleh, selaras harga produk non-negatif; tidak ada aturan baru free-order checkout.
- Partial unique index pada order_id untuk baris `status IN ('PENDING','SUCCESS')` mempertahankan maksimum satu attempt aktif/berhasil; baris FAILED tetap dapat banyak. Guard digunakan juga untuk serialisasi policy; index adalah perlindungan tambahan, bukan satu-satunya alur bisnis.
- `@Version Long` diteruskan oleh mapper untuk entity yang mutable. ID sequence numerik; tidak memakai UUID untuk identifier resource. Tidak memakai record sebagai ordinary JPA entity atau Lombok @Data.
- NUMERIC tidak dipaksa menjadi scale 2. JPA mapping, JSON parser/encoder, dan schema validation harus mempertahankan 350000.125, bukan hanya migrasinya. Ini perlu dibuktikan pada tests PostgreSQL.
- COMPLETED harus memiliki snapshot respons 201, payment_id, completed_at dan expires_at. PROCESSING memiliki pemilik dan lease, bukan snapshot sukses. Gunakan CHECK konsistensi sesuai state; response body disimpan sebagai TEXT agar representasi JSON awal tidak diubah oleh JSONB.
- Idempotency key memakai equality case-sensitive/deterministik pada PostgreSQL; jangan memakai citext atau collation case-insensitive. Timestamps memakai Instant/TIMESTAMPTZ; lease dan expiry memakai clock database agar antar-instance konsisten.

## 4. Klaim, transaksi, dan recovery

Default awal rancangan: lease 30 detik, lock wait maksimum 250 ms, connect timeout Order 2 detik dan read timeout 5 detik; seluruhnya configurable dan harus diuji. Angka ini bukan SLA yang telah diukur. Tidak ada network call di dalam transaksi finalisasi.

1. Boundary memvalidasi input dan membentuk canonical payload. Transaksi klaim singkat mencoba insert PROCESSING dengan owner_token acak dan lease_until. Unique `(operation,key)` menjadi arbiter antar-instance; bukan synchronized/in-memory lock.
2. Jika key sudah COMPLETED dan belum expired: mismatch ditolak; payload sama langsung mengembalikan status/body teks tersimpan, tanpa lookup Order atau memvalidasi ulang kelayakan create. Set Content-Type seperti hasil awal; Date dan header transport dinamis bukan bagian kontrak replay.
3. Jika PROCESSING dengan lease aktif: payload sama mendapat 409 REQUEST_IN_PROGRESS. Kontensi unique insert/row lock yang tidak selesai dalam lock budget juga dipetakan ke konflik sementara, bukan menunggu tanpa batas. Insert konflik tidak boleh membuat transaksi berikutnya memakai transaksi SQL yang sudah aborted; gunakan ON CONFLICT atau transaction boundary yang benar.
4. Setelah klaim commit, baca Order di luar transaksi SQL. Ambil id/total/status, verifikasi ID respons sama dan status enum dikenali; hanya PENDING memenuhi syarat. Data invalid/kontradiktif bukan Order 404.
5. Finalisasi melalui bean `CommitPaymentCreationService` yang memiliki transaksi lokal: lock row idempotency lebih dahulu, cek owner_token/state/lease, lalu buat/get guard Order dan lock guard. Urutan lock konsisten: idempotency (jika create), guard Order, lalu Payment. Inisialisasi guard juga tunduk lock budget.
6. Di bawah guard, periksa SUCCESS/PENDING yang sudah ada; jika ada, tolak menurut tabel error. Jika layak, simpan Payment, encode respons create secara deterministik, simpan body/status 201 dan ubah klaim ke COMPLETED dengan expires_at = completed_at + 24 jam. Seluruhnya commit bersama. Encoder harus murni, tidak melakukan I/O remote. Kegagalan serialisasi juga membatalkan Payment.
7. Respons baru maupun replay mengirim teks hasil encoder yang sama. Jangan serialize ulang Payment setelah transaksi lalu berharap hasilnya identik dengan snapshot tersimpan.

Lease habis bukan bukti pemilik lama telah berhenti. Retry boleh mengambil alih klaim PROCESSING yang expired dengan CAS/row lock singkat, mengganti owner_token dan lease. Pemilik lama wajib melakukan fencing check saat finalisasi; token lama tidak dapat commit. Jika transaksi finalisasi lama masih memegang lock, retry mendapat konflik sementara; ia tidak boleh memaksa takeover. Lease dicek saat masuk bagian finalisasi di bawah lock; setelah itu takeover tidak dapat berjalan sampai transaksi commit/rollback.

Recovery menyimpan PROCESSING tanpa dihapus oleh cleanup umur biasa. Stale claim hanya dapat dipulihkan melalui protokol takeover di atas. Setelah error yang dipastikan rollback, lepaskan klaim hanya dengan pencocokan token dan state; jangan menghapus klaim milik pemilik baru atau hasil COMPLETED. Jika respons commit/connection tidak jelas, cari key pada koneksi baru. COMPLETED berarti replay; row yang masih dikunci/hasil belum dapat diketahui berarti 503 untuk retry key sama, bukan key baru atau klaim bahwa Payment pasti gagal. Jangan menghapus data saat hasil commit belum pasti.

Cleanup hanya menghapus COMPLETED expired dengan row lock/conditional delete yang aman; pembayaran tidak dihapus. Sebelum cleanup fisik, request dapat memperlakukan COMPLETED expired sebagai key baru melalui penggantian atomik di bawah lock; sesudah itu policy attempt tetap berlaku. Client tidak boleh mendaur ulang key untuk operasi baru. Cleanup tidak pernah menghapus guard yang masih digunakan atau PROCESSING hanya karena lease habis.

Kelayakan Order adalah snapshot hasil lookup: desain ini tidak menghilangkan race jika Order dibatalkan tepat setelah lookup. Tidak ada transaksi global atau jaminan reservation Order. Rekonsiliasi lintas layanan tetap tahap integrasi berikutnya.

## 5. Mutasi reference dan hasil simulator

PATCH dan apply-result memakai transaksi lokal singkat. Cari orderId dari Payment tanpa memodifikasinya, lock guard lalu Payment, baca ulang state/version, dan terapkan domain rule. orderId Payment immutable. Dengan demikian hasil simulator dan PATCH yang bersamaan tidak melakukan lost update. GET tidak mengambil write lock.

- Reference harus memuat minimal satu karakter non-whitespace; simpan teks persis yang diterima, tanpa trim diam-diam. TEXT tanpa batas panjang bisnis baru; body-size limit infrastruktur ditentukan saat implementasi. Atribut response dihilangkan sampai reference diisi. Unknown fields termasuk amount/status ditolak pada PATCH maupun create.
- Hasil PENDING ke SUCCESS/FAILED mengubah state sekali. Hasil final identik mengembalikan 200 Payment terkini tanpa perubahan version/timestamp/efek tambahan. Hasil final berlawanan mendapat 409 PAYMENT_RESULT_CONFLICT. Tidak ada event finansial eksternal atau write Order pada baseline, sehingga state terminal cukup untuk no-op hasil identik; ini bukan deduplikasi provider event universal.
- Tidak ada perubahan SUCCESS ke FAILED untuk refund atau FAILED ke SUCCESS untuk menghidupkan attempt lama. COD SUCCESS simulator berarti konfirmasi uang diterima, bukan sekadar pengiriman.

## 6. Simulator development/test

Kontrak terpisah: POST `/internal/dev/payments/{paymentId}/result`, body hanya `{"status":"SUCCESS"}` atau `{"status":"FAILED"}`, respons 200 Payment. GET publik dipakai untuk memeriksa hasil. Simulator memakai ApplyPaymentResultUseCase yang sama dengan tests, bukan SQL controller.

Aktivasi memerlukan keduanya: profil `local` atau `test` DAN `payment.simulator.enabled=true`; default false. Jika profil `prod` aktif bersamaan, simulator tetap tidak didaftarkan. Saat simulator aktif, listener lokal harus terikat ke loopback untuk baseline host/WSL ini; startup ditolak jika bind address bukan loopback atau konfigurasi token tidak valid. Mode demo container/cluster tidak otomatis diaktifkan.

Header `X-Simulator-Token` wajib, dibandingkan secara constant-time terhadap secret `PAYMENT_SIMULATOR_TOKEN`/property konfigurasi setara, panjang minimal 32 karakter. Jangan memasukkan nilainya ke YAML yang dikomit, log, atau image. Token dipasok melalui environment lokal; helper yang ada belum mengaktifkan simulator. Tidak membangun JWT/customer authentication baru. Header hilang/salah mendapat 401; saat dinonaktifkan atau tidak diizinkan, endpoint tidak tersedia (404). Ini pembatasan alat demo, bukan otorisasi pelanggan produksi.

## 7. Pemetaan error

| Kondisi | HTTP | code |
|---|---:|---|
| Input/key/reference invalid atau unknown fields | 400 | BAD_REQUEST |
| Payment tidak ditemukan / Order 404 | 404 | NOT_FOUND / ORDER_NOT_FOUND |
| Order bukan PENDING | 409 | ORDER_NOT_PAYABLE |
| Attempt PENDING sudah ada | 409 | PAYMENT_ALREADY_PENDING |
| Payment SUCCESS sudah ada | 409 | ORDER_ALREADY_PAID |
| Key sama, payload berbeda | 409 | IDEMPOTENCY_KEY_REUSED |
| Klaim key masih aktif / lease fencing kalah | 409 | REQUEST_IN_PROGRESS |
| Guard Order/Payment sibuk melewati lock budget | 409 | PAYMENT_OPERATION_BUSY |
| PATCH pada state terminal | 409 | PAYMENT_NOT_MODIFIABLE |
| Hasil final simulator bertentangan | 409 | PAYMENT_RESULT_CONFLICT |
| Respons Order rusak/tidak sesuai kontrak atau upstream 5xx | 502 | ORDER_UPSTREAM_ERROR |
| Koneksi Order gagal / database tidak tersedia / commit belum diketahui | 503 | DEPENDENCY_UNAVAILABLE |
| Timeout koneksi/baca Order | 504 | ORDER_TIMEOUT |
| Bug internal tak terduga | 500 | INTERNAL_SERVER_ERROR |

Simbol exception transport/SQL dipetakan adapter; core memakai exception aplikasi/domain. Timeout connect dibedakan dari kegagalan koneksi langsung. Respons sementara tidak menjadi snapshot replay. Stub mencakup semua cabang. Status 502/503/504 tidak membuktikan Payment terminal FAILED.

## 8. Bukti yang wajib pada tahap implementasi

- Dua key sama: satu pemilik; duplikat fail-fast lalu replay; JSON property order berbeda tetap ekuivalen.
- Dua key berbeda untuk Order sama: guard dan partial index mencegah dua PENDING/SUCCESS, termasuk balapan simulator dan create.
- Finalisasi rollback tidak meninggalkan Payment atau COMPLETED parsial; commit sukses/response hilang tetap replay byte body awal.
- Owner lama dengan token usang tidak commit; stale PROCESSING takeover dan lock contention diuji dengan barrier/clock terkontrol, bukan tidur arbitrer.
- Retensi 24 jam, cleanup, key expired, dan pemulihan hasil commit tidak jelas memakai fault injection.
- Mapping JPA/JSON mempertahankan pecahan; tests menegaskan reference dan state terminal serta no-op final identik.
- Simulator benar-benar tidak ada pada profil/default yang tidak diizinkan, prod override, flag false, dan token invalid; tes startup memeriksa loopback/token.

Verifikasi tahap 0: kontrak publik dan simulator telah lolos validator OpenAPI; ini validasi spesifikasi, bukan bukti runtime. Domain tahap 1 kemudian diimplementasikan dengan 30 unit tests lulus; lihat [hasil tests](payment-service-testing.md). Klaim, transaksi, locking, mapping persistence, client, dan endpoint belum memiliki bukti runtime. Urutan berikutnya adalah tahap 2 (command/port), lalu migrasi, repository, adapter, use case, HTTP, handler, dan verifikasi gabungan.
