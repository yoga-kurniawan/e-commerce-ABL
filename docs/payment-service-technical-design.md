# Desain teknis Payment Service — tahap 0

Desain awal: tahap 0, 29 Oktober 2026; dilengkapi catatan implementasi tahap 1–7 (pembaruan 30 Oktober 2026). Domain, commands/ports, migrasi, JPA entities, dan repository sudah tersedia. Persistence adapter, Order REST adapter dan use cases tahap 7 sudah tersedia; boundary HTTP, encoder produksi dan simulator belum diimplementasikan. Acuan: DEC-012–DEC-018 pada [decisions.md](decisions.md), [alur implementasi](payment-service-implementation-plan.md), [kontrak publik](../specs/openapi.yaml), dan [kontrak simulator](../specs/payment-simulator.openapi.yaml). Hasil verifikasi aktual dicatat pada [payment-service-testing.md](payment-service-testing.md).

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

Tahap 2 memecah kebutuhan finalisasi ke output port `PaymentCreationCommitter.commit(claim, order)` yang mengembalikan hasil create tersimpan. Implementasi transaksi bean terpisah tetap pekerjaan tahap berikutnya. `PaymentStore` menyediakan findById serta kebutuhan mutasi reference/hasil secara atomik; tidak mengekspos save biasa yang dapat melewati finalisasi Payment+idempotency.

`CreationResponseEncoder` menerima Payment hasil create dan mengembalikan `StoredCreationResponse` (201, application/json, teks body awal). Adapter HTTP nantinya mengimplementasikan serialisasi deterministik, dipanggil di dalam finalisasi lokal tanpa I/O remote. Domain tidak mengenal response HTTP. Metadata replay adalah catatan operasi aplikasi, bukan ResponseEntity atau domain Payment. Validasi sintaks/schema JSON tetap tugas encoder/tests adapter, bukan parsing JSON di core.

Tipe aplikasi berada di `usecase.model`: IdempotencyKey (scope operasi tetap), IdempotencyClaim (request, owner token, leaseUntil), IdempotencyClaimResult (Claimed/Replayed/InProgress/PayloadMismatch), IdempotencyLookupResult (Completed/Processing/Missing/PayloadMismatch), dan CreatePaymentResult (Created/Replayed). CreatePaymentCommand memiliki canonicalPayload/fingerprint serta pembandingan nilai payload; key tidak masuk fingerprint. Tipe ini tidak menentukan sendiri clock database, transaksi, atau keberhasilan locking.

`IdempotencyStore` memiliki operasi claim atomik, inspect tanpa mutasi untuk rekonsiliasi, releaseAfterConfirmedRollback dengan verifikasi token/state, serta cleanup hasil selesai expired. Missing/Processing pada inspect bukan bukti rollback; kegagalan membaca database tidak boleh diam-diam menjadi Missing. Port tidak menjamin FIFO. Implementasinya wajib diuji dengan database nyata pada tahap adapter.

## 2. Key, input, dan canonicalization

- Scope key: satu operasi `CREATE_PAYMENT_V1` dalam satu deployment/payment_db, bukan per orderId. Kombinasi `(operation, key)` unik. Key sama untuk Order berbeda tetap konflik payload. Auth pengguna belum tersedia; scope ini bukan mekanisme otorisasi. Jika auth ditambahkan, scope principal harus dirancang melalui keputusan baru.
- Header wajib, case-sensitive pada nilainya, panjang 1–128 karakter ASCII, pola `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`. Tidak di-trim atau diubah hurufnya. Nama header mengikuti sifat case-insensitive HTTP. Client disarankan memakai UUID v4/string acak; key bukan ID Payment dan tidak memuat data pribadi.
- Body create hanya `orderId` integer yang muat di Long dan `method` enum case-sensitive. Field wajib/null/tipe salah/overflow/field asing menghasilkan 400 sebelum klaim. Duplikasi nama properti JSON ditolak, bukan last-value-wins. Tidak menambahkan endpoint atau fitur autentikasi.
- Canonical payload versi 1 adalah UTF-8 `v1\norderId=<Long desimal>\nmethod=<enum>`. Hash SHA-256 dipakai sebagai fingerprint; simpan juga orderId/method untuk membandingkan nilai aktual. Urutan properti, spasi JSON, dan escape JSON yang menghasilkan string sama tidak membuat operasi berbeda. Angka pecahan bukan orderId valid. Untuk baseline ini tidak ada syarat positivity baru pada kontrak ID; kelayakan resource diperiksa via lookup.
- Payload mismatch pada key yang sudah terikat operasi menghasilkan 409 IDEMPOTENCY_KEY_REUSED, termasuk ketika operasi masih berjalan. Untuk request yang belum valid/berhasil mengklaim key, tidak ada hasil sukses yang direplay.

## 3. Rancangan data lokal

Schema di bawah diwujudkan pada `V1__create_payment_storage.sql` di Payment Service. Tidak ditemukan migrasi terdahulu pada service ini; tidak ada migrasi lama atau database development yang diubah.

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

### Detail mapping tahap 3

- `PaymentJpaEntity`, `PaymentOrderGuardJpaEntity`, dan `PaymentIdempotencyJpaEntity` berada di `adapter.outbound.database.entity`. Domain Payment tidak diubah menjadi JPA entity. `IdempotencyState` adalah state penyimpanan operasi, bukan status pembayaran.
- `payments_seq` memakai increment 50, sesuai `@SequenceGenerator(allocationSize = 50)`. ID Payment tetap Long; gap sequence normal dan bukan urutan bisnis.
- `(operation,key)` menjadi composite primary key melalui `PaymentIdempotencyId` (`@EmbeddedId`); kolom memakai collation `C`. Equality Java juga case-sensitive. Tidak menambahkan identifier resource UUID.
- Ketiga entity memakai nullable `@Version Long` di Java; database menyimpan version NOT NULL. Guard dan catatan idempotency dengan assigned ID tetap terdeteksi sebagai entity baru ketika version masih null.
- `amount` dipetakan ke `numeric` tanpa precision/scale tetap. Reference dan body replay memakai TEXT, tanpa `@Lob`/JSONB, agar teks tidak dipangkas atau diserialisasi ulang. `payment_id` dipetakan sebagai scalar Long dengan FK lokal dari Flyway, tanpa association eager.
- CHECK membedakan PROCESSING tanpa snapshot dari COMPLETED dengan payment_id, respons 201/application/json, body, completed_at, dan expires_at tepat 24 jam kemudian. Owner/lease tetap tersimpan saat COMPLETED. Index terpisah mendukung pencarian lease PROCESSING dan cleanup COMPLETED expired; tidak ada cleanup otomatis pada tahap ini.
- Waktu pada constructor/update entity dipasok caller dari clock database. Tidak ada callback yang memakai clock JVM atau otomatis mengubah timestamp saat read/no-op. Mapper/adapter berikutnya harus memperbarui entity managed setelah locking, mempertahankan id/version/createdAt, dan tidak memanggil update pada hasil domain no-op.
- `order_id`, method, fingerprint pada catatan klaim tidak berubah melalui update JPA biasa. Penggantian key expired untuk request baru nantinya memerlukan delete/insert atau SQL atomik di bawah lock; tidak cukup mengganti payload pada instance lama.
- Metode entity hanya assignment state penyimpanan. Pemeriksaan pemilik, fencing, lease, policy bisnis, urutan lock, dan finalisasi atomik belum diimplementasikan. Partial index tidak sendirian melarang semua transisi ilegal atau setiap attempt baru setelah SUCCESS; adapter/domain tetap wajib menegakkan policy lengkap.
- Konfigurasi runtime memakai `ddl-auto=validate`, Flyway aktif, baseline otomatis/out-of-order nonaktif. Tidak memakai Hibernate untuk membuat atau mengubah schema.

### Repository tahap 4 dan batas transaksi

Repository berada langsung di `adapter.outbound.database`, mengikuti konvensi Product Service. Semuanya hanya mengekspos JPA entity lokal; usecase tetap memakai output port.

| Komponen | Query/operasi |
|---|---|
| PaymentsRepository | findById; scalar findOrderIdById; existsByOrderIdAndStatus; findBlockingAttempt (PENDING/SUCCESS); findByIdForUpdate |
| PaymentOrderGuardsRepository | insertIfAbsent (ON CONFLICT DO NOTHING); findByOrderIdForUpdate |
| PaymentIdempotencyRepository | findById untuk inspect/snapshot; findByIdForUpdate; insertProcessingIfAbsent; deleteProcessingOwnedBy; deleteExpiredCompletedByKey; deleteExpiredCompletedBatch |
| PaymentDatabaseSession | clock_timestamp() sebagai Instant; configureLockTimeout(Duration) melalui set_config transaction-local |

- Lock dan semua native mutation mewajibkan transaksi caller (`MANDATORY`), sehingga lock tidak hilang akibat repository membuat transaksi pendek sendiri. Adapter tahap 5 harus memanggil `configureLockTimeout` sebelum insert/lock, dengan default rancangan 250 ms. PostgreSQL lock_timeout dipakai secara nyata; bukan mengandalkan hint JPA timeout positif yang belum tentu diterapkan dialect. Nilai nol ditolak karena PostgreSQL menafsirkannya sebagai timeout dinonaktifkan. Budget berlaku per perolehan lock, bukan deadline total transaksi.
- Urutan finalisasi tetap idempotency → guard → Payment; PATCH/result guard → Payment. `findById` dan scalar lookup tidak mengambil write lock. `findBlockingAttempt` bukan lock/claim atomik; policy wajib diperiksa setelah lock guard. Lock query yang menghasilkan empty tidak mengunci baris yang belum ada; gunakan insert ON CONFLICT lebih dahulu untuk claim/guard.
- Insert klaim menghasilkan count 1/0, tanpa mengubah pemilik/payload lama. Caller membandingkan payload, state, expiry, lease, dan owner token; takeover memakai entity managed di bawah lock serta @Version. Insert tidak memberikan hasil replay atau melakukan lookup Order. Lease dihitung database; caller wajib memvalidasi durasi positif.
- findById catatan idempotency menyertakan snapshot awal tanpa join ke Payment terkini. Caller harus memeriksa expiry/payload sebelum replay. Reconciliation memakai transaksi/koneksi baru; Optional.empty bukan pengganti kegagalan membaca database.
- Cleanup menggunakan CTE PostgreSQL, LIMIT dan FOR UPDATE SKIP LOCKED, hanya COMPLETED expired; Payment/PROCESSING tetap ada. Caller wajib memvalidasi batchSize positif dan menyediakan transaksi cleanup terpisah. Penggantian key expired mengunci baris, conditional delete, lalu insert baru dalam satu transaksi.
- Native SQL sengaja dipakai untuk ON CONFLICT, clock/timeout PostgreSQL, dan cleanup terbatas. Operasi bulk melewati persistence context JPA: flush perubahan relevan sebelum bulk dan detach/clear entity terdampak sebelum membacanya lagi. Jangan melakukan clear sembarangan saat ada perubahan Payment yang belum di-flush. Repository tidak otomatis membuka transaksi REQUIRES_NEW atau membersihkan seluruh context.
- Release klaim membatasi state PROCESSING dan owner_token. Pemanggilan hanya sah setelah rollback finalisasi dipastikan; repository tidak dapat membuktikan hasil commit. Encoder respons, penyelesaian snapshot, fencing, mapping error HTTP, serta implementasi port tetap tahap 5 dan berikutnya.

### Implementasi adapter tahap 5

- `PaymentPersistenceMapper` melakukan restore domain dan membuat entity hanya untuk Payment baru. Update memakai entity managed: id/version/createdAt, amount, orderId dan method dipertahankan; status/reference identik tidak mengubah timestamp/version.
- `PaymentPersistenceAdapter` mengimplementasikan PaymentStore. Mutasi mengambil scalar Order ID lalu lock guard → Payment, menjalankan aturan domain, dan flush. Hasil terminal identik benar-benar tidak menghasilkan perubahan metadata.
- `PaymentIdempotencyAdapter` mengimplementasikan IdempotencyStore. Insert ON CONFLICT dan lock row berjalan atomik, membandingkan payload aktual/fingerprint, menghasilkan InProgress/mismatch/replay atau klaim baru. Lease usang diambil alih dengan token baru di bawah lock; claim expired COMPLETED diganti melalui conditional delete/insert dan detach entity lama. Retensi replay tidak diperpanjang. Inspect memakai transaksi baru dan tidak menghapus/mengambil alih catatan.
- `PaymentCreationPersistenceAdapter` mengimplementasikan PaymentCreationCommitter. Ia memverifikasi state/token/lease dan ID snapshot, lock guard, memeriksa attempt, membuat Payment lewat domain, encode snapshot, dan menyimpan COMPLETED dalam satu transaksi lokal. Ini implementasi boundary finalisasi yang semula dinamai CommitPaymentCreationService pada rancangan; tidak menambah orkestrator create/HTTP Order pada tahap ini.
- `PaymentTransactions` memakai TransactionTemplate REQUIRES_NEW untuk setiap operasi port, sehingga caller tidak membungkus klaim/lookup/finalisasi dalam satu transaksi tanpa sengaja. Lock timeout disetel pada koneksi yang sama sebelum operasi. Konfigurasi positif: payment.persistence.lock-timeout-ms (default 250) dan payment.persistence.claim-lease-ms (default 30000). TTL hasil tetap 24 jam.
- SQLSTATE 55P03 diterjemahkan setelah rollback; klaim mendapat outcome InProgress, lock klaim finalisasi REQUEST_IN_PROGRESS, lock guard/Payment PAYMENT_OPERATION_BUSY. Kegagalan database/commit lain dilaporkan DEPENDENCY_UNAVAILABLE melalui exception aplikasi murni Java. Tidak menghapus klaim otomatis setelah error; caller tahap 7 wajib merekonsiliasi hasil commit ambigu melalui inspect pada transaksi baru, dan hanya memanggil release setelah rollback dipastikan.
- `CreationResponseEncoder` produksi belum tersedia hingga tahap HTTP. Finalizer memakai ObjectProvider dan baru membutuhkan bean encoder saat commit; tidak menyertakan encoder palsu produksi. Tests memasok encoder deterministik/fault injection. Kegagalan atau ketiadaan encoder membatalkan finalisasi lokal.
- Tests fault acknowledgment memakai transaction manager delegasi yang benar-benar commit PostgreSQL, kemudian melempar TransactionSystemException. Hasil tetap COMPLETED/replay dengan satu Payment. Ini pengujian fault boundary commit, bukan pemutusan jaringan PostgreSQL sungguhan.
- Tidak ada perubahan migrasi, domain entity, kontrak publik atau implementasi OrderLookup. Consumer aplikasi tetap memakai output ports; exception transport/database tidak masuk domain.

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

### Order REST adapter tahap 6

- `OrderRestAdapter` mengimplementasikan OrderLookup; `OrderApiClient` memakai @HttpExchange dan @GetExchange untuk GET `/api/v1/orders/{orderId}`. Proxy didaftarkan melalui @ImportHttpServices(group="orders") di OrderClientConfiguration, dengan starter-restclient versi parent. Tidak memakai proxy factory manual atau entity/repository Order.
- `spring.http.serviceclient.orders.base-url=${clients.order-url:http://127.0.0.1:8083}` menghubungkan property yang benar-benar dipasok scripts/run-local-service.sh dan Helm deployment ke group client. Default connect 2s/read 5s, configurable melalui properti group yang sama. Redirect dinonaktifkan agar 3xx diperiksa langsung sebagai respons upstream yang tidak sesuai, tanpa mengikuti tujuan baru atau mengubahnya menjadi kegagalan koneksi.
- Client menerima ResponseEntity<String>. Mapper lokal memparse JSON dengan BigDecimal, pemeriksaan tipe ketat, duplicate detection, dan trailing-token detection, lalu membentuk DTO remote OrderResponse dan domain OrderSnapshot. Required snapshot id/total/status, rentang Long, kesamaan ID, enum dikenali dan total nonnegatif diperiksa. Field Order lain seperti customerId boleh ada. Tidak menghitung ulang total; format double pada schema sumber Order tidak dijadikan alasan mengonversi nominal melalui Java double.
- Hanya HTTP 404 menjadi Optional.empty. Upstream 4xx lain/5xx, status sukses selain 200, body/content-type invalid, atau data tidak konsisten menghasilkan ORDER_UPSTREAM_ERROR. Kegagalan koneksi menghasilkan DEPENDENCY_UNAVAILABLE; timeout connect/read menghasilkan ORDER_TIMEOUT berdasarkan rantai exception transport. Status 504 upstream adalah kegagalan respons upstream, bukan timeout transport lokal.
- Adapter tidak membuka transaksi database, tidak melakukan write Order, dan tidak menambahkan retry otomatis. Orkestrator tahap 7 tetap harus menempatkan lookup di luar transaksi finalisasi. Status CONFIRMED/CANCELLED tetap dapat dibaca sebagai snapshot; kelayakan create diperiksa domain/finalizer.
- Wiring proxy, property URL, presisi nilai, respons rusak, read timeout dan koneksi ditolak diuji dengan server HTTP loopback dan Boot nyata tanpa database. Cabang connect timeout memakai transport fault injection; tidak mengklaim timeout OS nyata teruji. Integrasi Order Service sungguhan tetap tahap berikutnya.

### Use cases tahap 7 dan kepastian rollback

- CreatePaymentUseCase, GetPaymentUseCase, PatchPaymentUseCase, dan ApplyPaymentResultUseCase berada di usecase.service dan hanya menggunakan domain/output ports. Spring @Service dipakai untuk wiring; tidak ada dependency repository, adapter, HTTP DTO, JPA, atau transaksi SQL pada orkestrator.
- Create melakukan claim lebih dahulu. Replay langsung mengembalikan snapshot tanpa lookup/validasi Order terkini; mismatch/in-progress menghasilkan konflik tanpa finalisasi atau release klaim milik request lain. Claimed dilanjutkan lookup snapshot, validasi ID/payable, lalu satu panggilan committer. Policy attempt tetap diperiksa atomik di committer, bukan check-then-insert pada use case.
- Kegagalan sebelum committer dipanggil (termasuk Order 404/timeout/invalid/nonpayable) dapat melepas klaim milik request karena finalisasi belum dimulai. Pelepasan bersyarat token/state; kegagalannya dicatat sebagai suppressed pada error utama tanpa menghapus paksa atau menimpa error asal.
- Kontrak finalisasi diperjelas lewat PaymentCreationRolledBackException (pure Java, membawa kegagalan asli). PaymentTransactions.creation mengamati afterCompletion transaksi: hanya STATUS_ROLLED_BACK yang menghasilkan wrapper. STATUS_COMMITTED/UNKNOWN atau kegagalan sebelum outcome diketahui tidak boleh ditebak rollback berdasarkan code/status exception.
- Use case melepas klaim setelah menerima wrapper rollback terkonfirmasi, lalu meneruskan kegagalan asli. Untuk exception finalisasi lain, inspect pada transaksi baru: Completed menjadi Replayed, PayloadMismatch menjadi IDEMPOTENCY_KEY_REUSED, sedangkan Missing/Processing/read failure menjadi DEPENDENCY_UNAVAILABLE dengan instruksi retry key sama. Tidak ada auto-retry finalisasi, penghapusan klaim ambigu, atau perubahan Payment menjadi FAILED akibat timeout.
- Get menghasilkan NOT_FOUND untuk Payment hilang. PATCH dan hasil simulator meneruskan command ke operasi atomic PaymentStore; tidak melakukan GET/check/update terpisah yang membuka race. Domain/adapter tetap memegang invariant dan no-op hasil terminal identik. Belum ada route simulator atau write Order.
- Unit tests menggunakan mock/fake ports; tests PostgreSQL tambahan membuktikan alur use case encoder gagal → rollback → release → retry → satu Payment → hasil SUCCESS → replay body awal tanpa lookup Order. Lookup pada skenario tersebut berjalan tanpa transaksi SQL aktif. Tests fault commit memastikan completion COMMITTED tidak keliru menjadi rollback terkonfirmasi.

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

Verifikasi tahap 0: kontrak publik dan simulator telah lolos validator OpenAPI; ini validasi spesifikasi, bukan bukti runtime. Tahap 1–4 memiliki 103 tests lulus: 62 unit, 29 schema/mapping, dan 12 repository dengan PostgreSQL 17 terisolasi. Tests repository membuktikan query/kontensi lock; tests adapter tahap 5 membuktikan protokol klaim, transaksi lokal dan fencing pada skenario yang dijelaskan dalam laporan. Lihat command, hasil, dan batas aktual pada [hasil tests](payment-service-testing.md). Tahap 5 menambahkan 14 tests adapter dengan total 117 lulus. Tahap 6 menambahkan 38 tests HTTP/transport dengan total 155 lulus. Tahap 7 menambahkan 28 tests unik, total 183 lulus. Berikutnya tahap 8 (HTTP/encoder/simulator), handler dan verifikasi gabungan.
