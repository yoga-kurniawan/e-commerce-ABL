# Hasil verifikasi Payment — tahap 1–8

Tanggal: 29–30 Oktober 2026. Mencakup unit tests domain/aplikasi, tests persistence PostgreSQL dan client HTTP terisolasi, belum verifikasi aplikasi Payment lengkap.

## Verifikasi terbaru — tahap 8 boundary HTTP dan simulator

Working tree di atas commit `5b2d3aa`, selesai 30 Oktober 2026 pukul 00:21:16 WIB. Command aktual:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=*Test' test > services/payment-service/target/payment-stage8-test.log 2>&1
exit $LASTEXITCODE
```

**BUILD SUCCESS, exit 0, 242 tests, 0 failures, 0 errors, 0 skipped.** PaymentsHttpBoundaryTest menambah 42 MVC slice tests dan SimulatorActivationTest 17 tests startup/configuration, di atas 183 tests sebelumnya. Runtime Java 22.0.2, compile release 17, Maven 3.9.6, Boot 4.1.2/Jackson 3.1.7, PostgreSQL 17.11/Testcontainers untuk regresi database. Runtime Java 17 belum diuji. Selector `*Test` sengaja tidak menjalankan generated PaymentApplicationTests (nama berakhiran Tests); ini bukan full Maven verify.

Build bersih sebelumnya juga lulus 59 tests baru memakai selector `-Dtest=PaymentsHttpBoundaryTest,SimulatorActivationTest` dengan `clean test` (tanpa offline), selesai 00:18:41 WIB. Dependency plugin clean sempat belum lengkap di cache offline; pengulangan online melengkapinya. Referensi enum Jackson awal diperbaiki dari DeserializationFeature ke EnumFeature sesuai API Jackson 3; clean build membuang bytecode IDE yang memuat unresolved compilation problem. Tidak mengubah versi dependency/POM proyek. Log clean disimpan pada target/payment-stage8-clean-test.log; log akhir dan XML Surefire berada di target Payment.

Bukti tahap 8:

- POST baru/replay mengirim String status/body tersimpan persis, memakai encoder produksi, tanpa envelope atau reserialize state terkini. Output memuat nominal 350000.12500, tidak memuat metadata persistence, dan menghilangkan reference saat null.
- GET/PATCH sukses memetakan DTO publik; whitespace reference dipertahankan. Semua controller hanya memanggil use case/mapper, tanpa repository.
- MVC menolak body kosong/null, field wajib hilang/null, field asing (termasuk amount/status), properti duplikat, JSON tambahan, ID pecahan/string/overflow, enum numerik/salah case, reference kosong/Unicode whitespace/numerik dan ignored property. Header key hilang, invalid, atau terlalu panjang ditolak sebelum create use case.
- Simulator SUCCESS/FAILED memanggil ApplyPaymentResultUseCase. Header token hilang/salah/duplikat ditolak 401 sebelum body binding; response memakai code/message. Body simulator PENDING/null/numerik atau field asing ditolak 400. Token fixture pada tests hanya nilai publik khusus test, bukan secret environment nyata.
- Matrix profil none/prod/local/test/local+prod/test+prod dan flag aktif/nonaktif memeriksa pendaftaran controller. Saat tidak terdaftar, dispatcher MVC sungguhan mengembalikan 404. Profil prod menang atas local/test.
- Startup context menolak address kosong, wildcard IPv4/IPv6 dan non-loopback; menolak token hilang/pendek. Literal IPv4/IPv6 loopback diterima dan token dibandingkan persis, tanpa trim. Tidak memasukkan secret aktual ke source/log.
- Seluruh regresi domain, use case, transaksi/claim/fencing, schema/repository dan client Order tetap lulus.

Batas: tests baru memakai MockMvc/use cases MockitoBean dan encoder/mapper/config nyata; bukan bukti full HTTP controller → use case → database → Order. Aktivasi memverifikasi web context/dispatcher serta validasi property bind; listener/socket deployment nyata belum diuji. Kontrak error domain/dependency (409/404/502/503/504 dan body framework seragam) menunggu tahap 9. Simulator tidak memindahkan uang; replay setelah perubahan state dengan database nyata memakai encoder test pada tahap sebelumnya, sehingga gabungan encoder produksi+database menjadi target tahap 10. Order nyata, provider, integrasi cluster dan generated application test belum diverifikasi.

Konfigurasi demo lokal setelah datasource dan Order API/stub siap: aktifkan SPRING_PROFILES_ACTIVE=local, PAYMENT_SIMULATOR_ENABLED=true, SERVER_ADDRESS=127.0.0.1, dan pasok PAYMENT_SIMULATOR_TOKEN acak minimal 32 karakter melalui environment lokal. Kirim POST `/internal/dev/payments/{paymentId}/result` dengan header X-Simulator-Token dan body `{"status":"SUCCESS"}` atau `{"status":"FAILED"}`. Default off; jangan menyimpan token aktual dalam file yang dikomit. Profil prod tidak mendaftarkan endpoint. Periksa status terkini lewat GET Payment publik.

Perubahan: DTO PaymentInput/PaymentReferencePatch/PaymentOutput, PaymentHttpMapper, PaymentsApi/Controller, PaymentCreationResponseEncoder, PaymentJsonConfiguration, empat class simulator, dua class tests, default flag pada application.properties dan dokumentasi plan/design/architecture/testing. Domain, migrasi dan OpenAPI tidak diubah. Aturan bisnis terbuka tetap mengikuti decisions.md.

## Riwayat tahap 7 use cases

Working tree di atas commit `aaef6e4`, selesai 30 Oktober 2026 pukul 00:05:55 WIB. Command aktual:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=CreatePaymentUseCaseTest,PaymentMutationUseCasesTest,OrderRestAdapterTest,OrderTransportFailureTest,PaymentPersistenceAdaptersTest,PaymentRepositoriesTest,PaymentSchemaTest,PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest' test > services/payment-service/target/payment-stage7-test.log 2>&1
exit $LASTEXITCODE
```

**BUILD SUCCESS, exit 0; 183 tests, 0 failures, 0 errors, 0 skipped.** Rincian: CreatePaymentUseCaseTest 22, PaymentMutationUseCasesTest 5, PaymentPersistenceAdaptersTest 15 (satu tambahan), repository 12, schema 29, HTTP/transport 38, unit domain/commands/model sebelumnya 62. Runtime Java 22.0.2; compile release 17; Maven 3.9.6; Boot 4.1.2; PostgreSQL 17.11 via Testcontainers. Runtime Java 17 belum diuji. Log akhir dan XML Surefire berada di target Payment Service.

Unit tests memakai MockitoExtension dengan mock output ports dan fake PaymentStore yang menjalankan aturan domain nyata, tanpa Spring context/database. Tambahan 28 tests unik dibanding tahap 6 mencakup:

- Create berurutan claim → lookup → commit dengan snapshot/nominal utuh; replay langsung tanpa lookup Order meskipun dependency tidak tersedia. Mismatch/in-progress tidak memanggil finalisasi atau melepas klaim milik request lain.
- Order 404, status CONFIRMED/CANCELLED, ID salah, timeout/koneksi/upstream failure melepas klaim secara bersyarat sebelum finalisasi dimulai, tanpa memanggil committer.
- Finalisasi yang mengonfirmasi rollback melepas klaim lalu meneruskan error asli, termasuk conflict dan dependency failure. Kegagalan release tidak menimpa error utama.
- Finalisasi dengan hasil belum pasti: Completed menjadi replay; Missing/Processing/read failure tetap dependency unavailable tanpa release atau retry finalisasi. PayloadMismatch tetap konflik; claim gagal tanpa token tidak memulai lookup.
- Get missing/null, PATCH mempertahankan amount/method/status dan reference persis, hasil SUCCESS/FAILED identik hanya satu efek pada fake port, patch terminal/hasil berlawanan tidak menambah efek. Ini bukan mock yang mengklaim atomicity database.
- Tambahan PostgreSQL memakai adapter nyata: encoder gagal → rollback terkonfirmasi → use case release → retry berhasil → hasil SUCCESS → replay body awal tanpa lookup Order lagi; hanya satu Payment tersimpan. Callback lookup memeriksa tidak ada transaksi SQL aktif.

Kontrak kegagalan committer diperjelas menggunakan PaymentCreationRolledBackException dan observer transaction afterCompletion. Hanya STATUS_ROLLED_BACK menghasilkan wrapper; error setelah commit berhasil tetap tidak dibungkus sebagai rollback. Test fault acknowledgment commit dari tahap 5 secara eksplisit memeriksa pembedaan ini. Tests concurrency lama diperbarui untuk memeriksa wrapper beserta code penyebabnya; run awal sempat gagal karena test hanya menangkap PaymentOperationException lama, lalu run gabungan akhir lulus.

Batas: adapter/DB tests membuktikan transaksi lokal dan outcome; unit mock tidak membuktikan jaringan/database nyata. Integrasi baru memakai snapshot Order dan encoder khusus test, belum full HTTP Order → use case → controller. Tidak ada write Order, provider nyata, refund, route simulator, encoder produksi, generated PaymentApplicationTests, runtime Java 17 atau full Maven verify pada tahap ini. Use case tidak membuka transaksi SQL menyeluruh; caller berikutnya harus tetap menghindari membungkus lookup HTTP dalam transaksi sendiri.

File baru: CreatePaymentUseCase, GetPaymentUseCase, PatchPaymentUseCase, ApplyPaymentResultUseCase, PaymentCreationRolledBackException, CreatePaymentUseCaseTest dan PaymentMutationUseCasesTest. PaymentCreationCommitter, PaymentTransactions, PaymentCreationPersistenceAdapter serta integration tests diselaraskan dengan kontrak rollback. Dokumentasi plan/design/architecture/testing diperbarui; domain, migrasi dan OpenAPI tidak diubah.

## Riwayat tahap 6 Order REST adapter

Working tree di atas commit `bea0624`, run gabungan selesai 29 Oktober 2026 pukul 23:54:15 WIB. Command aktual:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=OrderRestAdapterTest,OrderTransportFailureTest,PaymentPersistenceAdaptersTest,PaymentRepositoriesTest,PaymentSchemaTest,PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest' test > services/payment-service/target/payment-stage6-test.log 2>&1
exit $LASTEXITCODE
```

Hasil: **BUILD SUCCESS, exit 0, 155 tests, 0 failures, 0 errors, 0 skipped**. OrderRestAdapterTest 37 + OrderTransportFailureTest 1; tests tahap sebelumnya 117. Maven 3.9.6, runtime Java 22.0.2, compile release 17, Boot 4.1.2, Spring Web 7.0.10, PostgreSQL 17.11/Testcontainers untuk regresi database. Runtime Java 17 belum diuji. Starter-restclient ditambahkan mengikuti dependency management parent; build awal mengunduh dependency yang belum tersedia, tests memakai cache offline.

Tests HTTP memakai Boot auto-configuration dan proxy @ImportHttpServices nyata, RestClient nyata, serta HttpServer JDK pada port loopback acak. Konfigurasi test menggunakan @TestConfiguration dan mengecualikan datasource; tidak memerlukan DB atau Order nyata. Property clients.order-url diarahkan ke port stub untuk membuktikan bridge URL dan prefix `/api/v1/orders/501`/GET. Tests tidak menambahkan dependency mock server baru.

Setelah penandaan konfigurasi stub sebagai @TestConfiguration untuk mencegah component scanning pada aplikasi, kedua class HTTP diverifikasi ulang dengan selector `-Dtest=OrderRestAdapterTest,OrderTransportFailureTest`. Selesai 23:55:44 WIB: BUILD SUCCESS, exit 0, 38 tests lulus tanpa failure/error/skipped. Log: `services/payment-service/target/payment-stage6-http-test.log`. Ini pengulangan terfokus, bukan tambahan 38 tests unik di luar total 155.

Bukti:

- Amount 350000.12500 serta angka besar 123456789012345678901234567890.123456789 dipertahankan tanpa double. Total nol dan status CONFIRMED/CANCELLED tetap dapat dibaca sebagai snapshot; aturan payable berada di domain/use case.
- 404 menjadi Optional.empty. Respons 400/401/403/409/429/500/502/503/504 tetap kegagalan upstream, bukan Order hilang atau timeout transport.
- Empty/invalid JSON, null/array, field hilang/null/salah tipe, nominal negatif/nonfinite, ID tidak cocok/overflow/pecahan/string, enum asing, properti duplikat, trailing JSON, content type salah/invalid, serta status 201/204/302 ditolak sebagai ORDER_UPSTREAM_ERROR.
- Read timeout nyata: stub menahan pengiriman headers menggunakan latch, client khusus test memiliki read timeout 200 ms. Skenario normal memakai default 5s dan connect 2s. Koneksi ke port loopback yang sudah ditutup menghasilkan DEPENDENCY_UNAVAILABLE. Transport fault injection HttpConnectTimeoutException menghasilkan ORDER_TIMEOUT; timeout connect OS nyata tidak diuji karena tidak deterministik lintas jaringan.
- Tidak melakukan I/O database, write Order, lookup repository Order, atau perhitungan ulang total pada adapter HTTP. Regresi transaksi/idempotency tahap sebelumnya tetap lulus.

Perbaikan selama pengujian: default redirect handling membuat respons 302 tanpa Location menjadi kegagalan transport; group orders memakai redirects=dont-follow sehingga 3xx dinilai langsung. Timeout 200 ms pada seluruh class sempat memicu timeout request normal pertama ketika client baru berjalan; override tersebut dipisah khusus test timeout, sedangkan tests respons normal memakai default 5s. Ini perubahan konfigurasi pengujian; default produksi tetap sesuai rancangan 2s/5s.

Batas: tests read timeout menahan headers, bukan seluruh kemungkinan kegagalan streaming body. Integrasi Order sungguhan, DNS/TLS/cluster, batas timeout connect jaringan nyata, full Payment HTTP flow, simulator, generated PaymentApplicationTests dan full Maven verify belum diuji. Pemetaan exception ke HTTP 502/503/504 dilakukan exception handler pada tahap berikutnya; tests ini memeriksa code aplikasi. Lookup tidak membuka transaksi DB; orkestrator tahap 7 tetap bertanggung jawab memisahkan lookup HTTP dari transaksi finalisasi.

File baru: OrderApiClient, OrderClientConfiguration, OrderRestAdapter, dto/OrderResponse, mapper/OrderSnapshotMapper, OrderRestAdapterTest, OrderTransportFailureTest. POM, application.properties serta dokumen plan/design/architecture/testing diperbarui. Kontrak OpenAPI, domain dan migrasi tidak diubah.

## Riwayat tahap 5 persistence adapter

Working tree di atas commit `703c232`, selesai 29 Oktober 2026 pukul 23:38:28 WIB. Command aktual:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=PaymentPersistenceAdaptersTest,PaymentRepositoriesTest,PaymentSchemaTest,PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest' test > services/payment-service/target/payment-stage5-test.log 2>&1
exit $LASTEXITCODE
```

**BUILD SUCCESS; exit code 0; 117 tests, 0 failures, 0 errors, 0 skipped.** PaymentPersistenceAdaptersTest 14, repository 12, schema 29, unit tests 62. Run awal dengan 11 tests adapter lulus; run akhir menambahkan fault acknowledgment commit, kontensi mutasi dan balapan hasil terminal. Maven 3.9.6, Java runtime 22.0.2, target release 17, Spring Boot 4.1.2, Hibernate 7.4.13.Final, Testcontainers 2.0.5, PostgreSQL 17.11. Runtime Java 17 belum diuji. Log dan XML Surefire berada di target Payment Service.

Pengujian menggunakan DataJpaTest + Flyway asli pada container disposable. Adapter dipanggil melalui port/implementasinya dengan transaksi PostgreSQL nyata; encoder produksi diganti bean khusus test, bukan mock database. Tidak memodifikasi database development.

Bukti tahap 5:

- Mapping mempertahankan 350000.12500, reference dengan whitespace, dan ID; hasil create serta snapshot commit bersama. Setelah Payment menjadi SUCCESS, replay/inspect tetap mengembalikan teks respons awal PENDING. TTL tepat 24 jam dan retry tidak memperpanjangnya.
- Klaim aktif membedakan in-progress dan payload mismatch. Lease expired diambil alih dengan token baru; pemilik lama tidak dapat finalisasi/release. Lease expired tanpa takeover juga tidak dapat finalisasi.
- Encoder gagal sesudah insert Payment memicu rollback: Payment tidak ada, klaim tetap PROCESSING. Release eksplisit yang aman memungkinkan retry; release tidak menghapus hasil COMPLETED.
- Fixtures timestamp relatif terhadap clock database menguji lease/retensi tanpa sleep. COMPLETED expired dapat diganti payload baru; cleanup mempertahankan Payment dan PROCESSING, serta membatasi batch.
- FAILED memungkinkan attempt baru, PENDING/SUCCESS menghalangi attempt baru, hasil final identik mempertahankan version/created_at/updated_at, PATCH terminal dan hasil berlawanan ditolak. Snapshot Order salah ID atau CANCELLED tidak meninggalkan Payment.
- Dua thread/koneksi dengan latch: key sama mendapat satu owner; key berbeda untuk Order sama menghasilkan satu Payment; lease habis saat finalisasi memegang lock tidak dapat diambil alih. Mutasi pada guard sibuk mendapat PAYMENT_OPERATION_BUSY tanpa perubahan. SUCCESS vs FAILED bersamaan menghasilkan satu transisi dan satu konflik, version hanya naik sekali.
- Fault injection setelah commit PostgreSQL berhasil menghasilkan DEPENDENCY_UNAVAILABLE; inspect pada transaksi baru menemukan COMPLETED, retry memperoleh replay, dan hanya satu Payment tersimpan. Klaim tidak dihapus otomatis.

Batas: encoder test hanya membuktikan penyimpanan teks/rollback, belum schema JSON OpenAPI produksi. Tidak melakukan pemutusan socket database sungguhan, integrasi Order nyata, HTTP 409/503, controller/simulator, atau full Maven verify/generated PaymentApplicationTests. Orkestrator tahap 7 masih harus memanggil inspect/release dengan benar dan membedakan rollback pasti dari hasil commit ambigu. Clock tests mengatur timestamp fixtures database; tidak mengganti clock server atau menguji drift antar-host. Kegagalan DB tidak diubah menjadi Missing; jaminan recovery juga bergantung pada database dapat dibaca kembali.

File baru: PaymentPersistenceMapper, PaymentPersistenceAdapter, PaymentIdempotencyAdapter, PaymentCreationPersistenceAdapter, PaymentTransactions, PaymentOperationException, dan PaymentPersistenceAdaptersTest. Status/rincian diselaraskan pada implementation-plan, technical-design, dan architecture. Domain, migrasi dan OpenAPI tetap mengikuti tahap sebelumnya.

## Riwayat tahap 4 repository

Working tree di atas commit `541fc89`, selesai 29 Oktober 2026 pukul 23:27:46 WIB. Command aktual (output diarahkan ke log sementara saat eksekusi):

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest,PaymentSchemaTest,PaymentRepositoriesTest' test
```

Hasil akhir: **BUILD SUCCESS, 103 tests, 0 failures, 0 errors, 0 skipped**. PaymentRepositoriesTest 12; PaymentSchemaTest 29; domain/commands/tipe aplikasi 62. Environment tetap Maven 3.9.6, runtime Java 22.0.2, target Java 17, Boot 4.1.2, Hibernate 7.4.13.Final, PostgreSQL 17.11/Testcontainers 2.0.5. Tidak menguji runtime Java 17. Reports ada di `services/payment-service/target/surefire-reports`; log akhir disimpan di `services/payment-service/target/payment-stage4-test.log`.

Tests baru menggunakan slice DataJpaTest dan database disposable terpisah; tidak mengakses database development. Cakupan:

- Lookup ID, scalar Order ID, filter attempt PENDING/SUCCESS di antara riwayat FAILED, pengecekan status, hasil kosong, presisi amount dan reference persis.
- Insert guard/klaim duplikat menghasilkan count 0 tanpa membuat transaksi aborted; payload/pemilik pertama tetap utuh, key beda huruf tetap berbeda, lease dihitung dari database.
- Snapshot respons COMPLETED tetap PENDING sesudah Payment menjadi SUCCESS; lookup tidak mengambil state respons dari Payment terkini.
- Conditional release tidak menghapus pemilik baru atau COMPLETED; key COMPLETED aktif tidak dianggap expired.
- Cleanup berbatas batch hanya menghapus COMPLETED expired, mempertahankan PROCESSING meskipun lease usang, mempertahankan Payment, dan SKIP LOCKED melewati row yang sedang digunakan.
- Conditional delete/insert key expired dibatalkan bersama saat rollback; snapshot semula kembali. Tests melakukan clear context setelah native replacement agar tidak membaca entity lama.
- Kontensi nyata pada query lock Payment, guard, dan idempotency: dua transaksi pada thread/koneksi berbeda; pemegang lock dibuat lebih dahulu, pesaing memakai lock_timeout 250 ms dan menghasilkan SQLSTATE 55P03; setelah commit lock kembali dapat diambil. Tidak memakai sleep untuk menyusun race; Future dibatasi 10 detik.
- Operasi lock/mutation ditolak tanpa transaksi caller; budget nol ditolak karena dapat menonaktifkan timeout PostgreSQL.

Perbaikan selama verifikasi: hasil native query JPA perlu cast eksplisit ke Instant. Satu test awal menangkap exception validasi di dalam transaksi sehingga commit menghasilkan UnexpectedRollbackException; assertion dipindahkan ke luar batas transaksi. Semua tests lulus sesudah perbaikan. Log timeout SQLSTATE 55P03 adalah keluaran skenario kontensi yang memang diharapkan; warning Mockito/Byte Buddy bukan failure.

Batas tahap 4: repository/helper belum mengimplementasikan PaymentStore, IdempotencyStore, atau PaymentCreationCommitter. Adapter berikutnya wajib menyetel lock budget sebelum operasi, menegakkan urutan lock, payload/lease/token/fencing, durasi/batch positif, no-op, dan rekonsiliasi commit. Bulk query melewati context JPA; caller mengelola flush serta entity terdampak. Tests tidak membuktikan fail-fast HTTP 409, transaksi finalisasi aplikasi, takeover antar-instance, encoder JSON, endpoint/simulator, ataupun integrasi Order nyata. Generated PaymentApplicationTests dan full Maven verify belum dijalankan.

File baru: PaymentsRepository.java, PaymentOrderGuardsRepository.java, PaymentIdempotencyRepository.java, PaymentDatabaseSession.java, dan PaymentRepositoriesTest.java. Dokumentasi implementation-plan, technical-design, architecture, dan laporan ini diselaraskan. Migrasi/entities/domain/kontrak OpenAPI tidak diubah pada tahap 4.

## Riwayat tahap 3 migrasi dan JPA entities

Perubahan diuji pada working tree di atas commit `ceb6265`. Command aktual, selesai 23:18:32 WIB:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest,PaymentSchemaTest' test
```

Hasil: **BUILD SUCCESS**, **91 tests**, 0 failures, 0 errors, 0 skipped. PaymentSchemaTest menjalankan 29 tests; empat class unit sebelumnya tetap 62 tests. Run awal 90 tests lulus; run akhir menyertakan tambahan test kontensi dua koneksi dan selesai sekitar 40 detik setelah image tersedia.

Environment aktual: Windows, Maven 3.9.6, runtime Maven/Surefire Java 22.0.2, Spring Boot 4.1.2, Hibernate 7.4.13.Final, Testcontainers 2.0.5, Docker Engine 29.4.0, image postgres:17 yang menjalankan PostgreSQL 17.11. Kompilasi awal memakai `release 17`; inspeksi bytecode entity dan schema test menunjukkan major version 61 (Java 17). Ini belum membuktikan eksekusi pada runtime Java 17. Docker Desktop awalnya tidak aktif, lalu dijalankan untuk tests; pengunduhan image pertama memerlukan sekitar tujuh menit.

PaymentSchemaTest memakai `@DataJpaTest`, PostgreSQL Testcontainers, Flyway asli, dan Hibernate `ddl-auto=validate`. URL datasource selalu berasal dari container disposable, bukan `.env`/database development. Pembersihan tabel hanya dilakukan pada database test tersebut. Tidak menggunakan H2 atau memodifikasi migrasi yang sudah diterapkan.

Cakupan yang dibuktikan:

- Migrasi V1 berhasil pada database kosong, validasi checksum/schema berhasil, migrate berikutnya menerapkan nol perubahan. Hanya ada FK lokal payment_id ke payments; tidak ada FK Order lintas service.
- Round-trip JPA mempertahankan nilai dan skala `350000.12500`, nol, serta nilai besar berpresisi tinggi; reference lebih dari 1.000 karakter beserta whitespace dan timestamp dipertahankan.
- Database menolak amount negatif/NaN/Infinity, required value yang hilang, dan enum yang salah.
- Beberapa FAILED tetap tersimpan, satu PENDING baru diperbolehkan setelah FAILED, dan partial unique index menolak kombinasi aktif/berhasil ganda untuk Order yang sama.
- Composite key scope operasi unik untuk Order berbeda, case-sensitive, serta menolak pola/panjang key yang invalid; 128 karakter diterima dan 129 ditolak.
- PROCESSING wajib memiliki owner/lease/fingerprint dan tidak boleh memiliki snapshot; COMPLETED wajib memiliki Payment lokal, respons 201/application/json/body, serta retensi tepat 24 jam. NULL eksplisit pada atribut wajib ditolak oleh CHECK.
- Mapping ketiga entity berhasil, version baru terisi, owner/lease bisa disimpan, dan teks body respons dibaca kembali persis. Dua writer dengan version Payment sama menyebabkan stale writer ditolak dengan OptimisticLockException. Assignment identik tanpa perubahan waktu tidak menyebabkan update version/timestamp.
- Dua koneksi nyata: insert duplikat pada partial unique index dan primary key idempotency menunggu transaksi pertama; lock_timeout 250 ms menghasilkan SQLSTATE 55P03, lalu setelah commit duplikat ditolak 23505. Tests tidak memakai sleep untuk membuat race.
- Rollback transaksi SQL manual yang menulis Payment+COMPLETED mengembalikan database ke klaim PROCESSING semula tanpa Payment parsial.

Batas hasil tahap 3:

- Tests kontensi membuktikan perilaku index PostgreSQL. Fail-fast HTTP 409, guard locking, CAS/takeover/fencing, cleanup, rekonsiliasi commit tidak pasti, dan transaksi adapter belum diimplementasikan atau diuji.
- Rollback manual bukan bukti implementasi PaymentCreationCommitter; snapshot TEXT bukan bukti encoder JSON sesuai OpenAPI. No-op dirty checking JPA bukan bukti use case hasil final berulang.
- Index tidak menegakkan seluruh transisi status atau kelayakan Order. Domain dan adapter tahap berikutnya tetap wajib memeriksa policy di bawah guard/lock.
- Repository, mapper/adapter, client Order, endpoint/simulator, integrasi Order nyata, serta generated PaymentApplicationTests belum dijalankan. Command ini bukan `mvn verify` untuk seluruh aplikasi.
- Tidak ada perubahan kontrak HTTP pada tahap 3. Unit domain/usecase tetap tanpa import Spring/JPA/HTTP.

File implementasi tahap ini: pom.xml dan application.properties Payment; V1__create_payment_storage.sql; PaymentJpaEntity, PaymentOrderGuardJpaEntity, PaymentIdempotencyJpaEntity, PaymentIdempotencyId, IdempotencyState; PaymentSchemaTest. Status/rincian diselaraskan pada implementation-plan, technical-design, dan architecture.

## Riwayat verifikasi tahap 2 commands dan ports

Command aktual pada 22:53 WIB:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=PaymentTest,OrderSnapshotTest,PaymentCommandsTest,PaymentApplicationTypesTest' test
```

Hasil: **BUILD SUCCESS**, 62 tests, 0 failures, 0 errors, 0 skipped, kompilasi release 17. Rinciannya: PaymentTest 25, OrderSnapshotTest 5, PaymentCommandsTest 27, PaymentApplicationTypesTest 5. Pengujian memakai cache Maven offline dan tidak menjalankan generated context test.

Tests baru mencakup field command wajib, enum hasil final, reference nonblank tanpa trim, batas/pola/case key, canonical payload dan fingerprint SHA-256 yang diketahui, perbedaan payload vs key, snapshot replay tetap PENDING meskipun domain terkini SUCCESS, penolakan respons error sebagai replay sukses, hasil create harus tersimpan/PENDING, dan validasi token/lease serta outcome yang tidak ambigu.

Tidak ada tests yang sekadar memanggil mock interface untuk mengklaim atomicity. Lima output ports baru masih berupa kontrak: PaymentStore, OrderLookup, IdempotencyStore, PaymentCreationCommitter, dan CreationResponseEncoder. Tidak ada implementasi repository/client/transaksi/locking pada tahap 2. Penyimpanan snapshot, validitas JSON hasil encoder, unknown-field detection pada input HTTP, TTL, dan recovery nyata masih harus diuji saat adapter/use case tersedia. Inspeksi dependensi source menemukan tidak ada import Spring/JPA/HTTP client pada usecase tahap ini.

## Riwayat verifikasi tahap 1

## Command dan hasil

Command aktual PowerShell menggunakan Maven yang tersedia di cache lokal:

```powershell
& 'C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\USER\.m2\repository' -pl services/payment-service -am '-Dtest=PaymentTest,OrderSnapshotTest' test
```

Hasil: **BUILD SUCCESS**, 30 tests, 0 failures, 0 errors, 0 skipped. Kompilasi memakai `release 17`. Maven Wrapper repository (3.10.0) tidak dipakai pada eksekusi ini. Command dipersempit pada dua class unit tests; generated PaymentApplicationTests tidak dijalankan.

| Class | Tests | Cakupan |
|---|---:|---|
| PaymentTest | 25 | Tiga metode termasuk COD mulai PENDING; total desimal utuh; create hanya untuk Order PENDING; null/negative invalid; reference immutable dan valid; state terminal; hasil identik no-op; hasil berlawanan ditolak; rehidrasi state |
| OrderSnapshotTest | 5 | Snapshot valid/invalid, total nol, presisi total, dan penolakan CONFIRMED/CANCELLED |

Laporan Maven tersedia di `services/payment-service/target/surefire-reports/`. Unit tests menggunakan JUnit dan AssertJ tanpa Spring context, database, atau Docker. Dependency/plugin Maven diperlukan; eksekusi awal offline berhasil compile tetapi gagal memuat Surefire karena cache plugin belum lengkap. Pengulangan dengan akses repository Maven berhasil tanpa mengubah versi dependency/POM.

## Hasil tahap 0 yang dituntaskan

```text
npx.cmd --offline --yes @apidevtools/swagger-cli@4.0.4 validate specs/openapi.yaml
npx.cmd --offline --yes @apidevtools/swagger-cli@4.0.4 validate specs/payment-simulator.openapi.yaml
```

Keduanya valid. Validator menemukan duplikasi YAML pada edit tahap 0 awal; perbaikan dilakukan sebelum validasi akhir. Tool ini dipakai hanya sebagai validator sementara dari cache npm; tidak ditambahkan sebagai dependency aplikasi.

## Batas verifikasi tahap 1–2 (historis)

Catatan berikut menggambarkan batas sebelum tahap 3. Bukti PostgreSQL/JPA terbaru ada pada bagian tahap 3 di atas.

- Domain immutable, murni Java: perubahan reference/status menghasilkan instance baru; hasil terminal identik mengembalikan instance yang sama. Factory restore hanya untuk rekonstruksi data persistence, bukan endpoint perubahan status.
- OrderSnapshot dan enum Status bersarang dimiliki Payment; tidak mengimpor module/DTO/entity Order. Invalid snapshot remote nantinya harus dipetakan Order REST adapter sebagai error dependency, bukan dianggap input client.
- Policy satu attempt aktif, key/replay, TTL, klaim/lease/fencing, guard/locking, version JPA, serta transaksi database belum diimplementasikan atau diuji.
- Transisi domain no-op tidak otomatis membuktikan deduplikasi side effect/database. Use case/persistence berikutnya wajib mempertahankan no-op tanpa update timestamp/version.
- Endpoint publik/simulator, error HTTP, bean client, datasource, migrasi, tests integration/context, container, dan runtime Java 17 belum diuji. Tidak ada bukti Payment API sudah berjalan.
- Pengujian uang di tahap ini hanya membuktikan BigDecimal domain; pemetaan PostgreSQL dan JSON masih harus dibuktikan pada tahap adapter.
