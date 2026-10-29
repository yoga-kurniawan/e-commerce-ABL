# Hasil verifikasi Payment — tahap 1–4

Tanggal: 29 Oktober 2026. Mencakup unit tests domain/tipe aplikasi dan tests schema/mapping PostgreSQL, belum verifikasi aplikasi Payment lengkap.

## Verifikasi terbaru — tahap 4 repository

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
