# Hasil verifikasi Payment — tahap 1 domain

Tanggal: 29 Oktober 2026. Ini hasil unit tests domain, bukan verifikasi aplikasi Payment lengkap.

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

## Batas verifikasi

- Domain immutable, murni Java: perubahan reference/status menghasilkan instance baru; hasil terminal identik mengembalikan instance yang sama. Factory restore hanya untuk rekonstruksi data persistence, bukan endpoint perubahan status.
- OrderSnapshot dan enum Status bersarang dimiliki Payment; tidak mengimpor module/DTO/entity Order. Invalid snapshot remote nantinya harus dipetakan Order REST adapter sebagai error dependency, bukan dianggap input client.
- Policy satu attempt aktif, key/replay, TTL, klaim/lease/fencing, guard/locking, version JPA, serta transaksi database belum diimplementasikan atau diuji.
- Transisi domain no-op tidak otomatis membuktikan deduplikasi side effect/database. Use case/persistence berikutnya wajib mempertahankan no-op tanpa update timestamp/version.
- Endpoint publik/simulator, error HTTP, bean client, datasource, migrasi, tests integration/context, container, dan runtime Java 17 belum diuji. Tidak ada bukti Payment API sudah berjalan.
- Pengujian uang di tahap ini hanya membuktikan BigDecimal domain; pemetaan PostgreSQL dan JSON masih harus dibuktikan pada tahap adapter.
