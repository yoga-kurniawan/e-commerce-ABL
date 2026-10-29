# Rencana pengujian

[Kembali ke README](../README.md) · [Aturan bisnis](business-rules.md) · [Development](development.md)

## 1. Status dan tujuan

Dokumen ini berisi rencana pengujian bersama. Hasil aktual Product tersedia pada [hasil pengujian Product](product-service-testing.md): 80 tests lulus melalui `verify` per module, termasuk enam operasi HTTP dan PostgreSQL terisolasi. Eksekusi gabungan 17 operasi lintas service dan load test belum selesai.

Laporan h. 25 dan 45 menjelaskan pemeriksaan kontrak menggunakan Swagger Editor. Contoh request/response dinyatakan sebagai ilustrasi pada h. 16. Validasi kontrak tidak membuktikan aplikasi mematuhi kontrak atau pembayaran selesai.

Materi APS menekankan tests terhadap invariant entity dan skenario utama/alternatif, serta keterlacakan desain ke kode. Terapkan hal itu pada domain, use case, dan adapter project ini.

## 2. Jenis pengujian dan lokasinya

| Jenis | Lokasi | Yang diperiksa |
|---|---|---|
| Domain unit test | `services/<service>/src/test/java/.../domain/` | Invariant dan efek method bisnis tanpa HTTP/database |
| Use case unit test | `.../usecase/` | Alur utama/alternatif menggunakan mock output port |
| Database integration test | `.../adapter/` | Mapping JPA, constraint, query, transaksi lokal dan version |
| REST adapter test | `.../adapter/` | Mapping respons remote, timeout, 404 dan 5xx menggunakan stub |
| Controller/API test | `.../adapter/` | Route, validation, status/header/body HTTP |
| Lintas layanan | `tests/integration/` | Komunikasi nyata dan workflow sesuai cakupan |
| Pengujian manual HTTP | `tests/postman/` | Collection dan environment tanpa secret nyata |
| Load test | `tests/load/` | Beban, latency, failure rate dan resource sesuai skenario |

Tests lokal berada pada module masing-masing. Folder `tests/` root tidak otomatis dijalankan oleh Maven; hubungkan melalui script/plugin/CI setelah mekanismenya dipilih.

Untuk repository integration test, PostgreSQL/Testcontainers merupakan rancangan yang sesuai target database. H2 test bila dipilih hanya membuktikan perilaku dalam lingkungan H2; jangan menyamakannya dengan kompatibilitas SQL PostgreSQL.

## 3. Pemeriksaan kontrak

Sebelum menguji server:

1. Buka [OpenAPI](../specs/openapi.yaml) di validator/Swagger Editor yang digunakan tim.
2. Pastikan 10 path memuat total 18 operasi dengan operationId unik dan semua local reference terdefinisi. Baseline laporan 17 operasi diperluas dengan POST `/carts` melalui OPEN-001 B.
3. Pastikan path parameter sesuai nama pada URL dan required.
4. Bandingkan request/response dengan contoh dan schema laporan.
5. Catat ketidaklengkapan sumber sebagai issue; jangan mengubah kontrak tanpa keputusan.

Schema asli tidak menetapkan seluruh whitelist, panjang string, null semantics, dan respons tambahan. Rencana tests untuk aturan baru harus menyebut keputusan yang menjadi dasarnya.

## 4. Matriks minimum 18 operasi

Semua path memiliki prefix `/api/v1`. Setiap baris adalah target pengujian setelah implementasi tersedia, bukan hasil yang sudah lulus.

| ID | Method dan path | Skenario sukses | Respons sukses |
|---|---|---|---|
| P-01 | GET `/products` | Daftar produk dapat diambil; bentuk array sesuai schema | 200 |
| P-02 | GET `/products/{productId}` | Produk fixture yang ada ditemukan | 200 |
| P-03 | POST `/products` | name/price/stock valid membuat resource | 201 |
| P-04 | PUT `/products/{productId}` | Semua atribut wajib diganti | 200 |
| P-05 | PATCH `/products/{productId}` | Satu atribut berubah, atribut lain tetap | 200 |
| P-06 | DELETE `/products/{productId}` | Penghapusan diizinkan policy | 204, body kosong |
| C-01 | GET `/carts/{cartId}` | Cart fixture beserta isi/total dapat dibaca | 200 |
| C-02 | POST `/carts/{cartId}/items` | Produk valid dan quantity valid ditambahkan | 201 |
| C-03 | PUT `/carts/{cartId}/items/{productId}` | Quantity tujuan ditetapkan | 200 |
| C-04 | PATCH `/carts/{cartId}/items/{productId}` | Quantity yang diizinkan diperbarui | 200 |
| C-05 | DELETE `/carts/{cartId}/items/{productId}` | Item dihapus; Cart tetap ada | 204, body kosong |
| C-06 | POST `/carts` | customerId valid membuat Cart kosong dengan ID backend, items kosong, total 0 | 201 |
| O-01 | POST `/orders` | Cart fixture layak dibuat menjadi pesanan | 201 |
| O-02 | GET `/orders/{orderId}` | Order fixture ditemukan | 200 |
| O-03 | PATCH `/orders/{orderId}` | Alamat diubah pada keadaan yang diizinkan | 200 |
| B-01 | POST `/payments` | Order dan method valid membuat permintaan pembayaran | 201 |
| B-02 | GET `/payments/{paymentId}` | Payment fixture ditemukan | 200 |
| B-03 | PATCH `/payments/{paymentId}` | Reference diperbarui pada keadaan yang diizinkan | 200 |

## 5. Skenario gagal dan invariant

### Product

- DEC-011: DELETE mempertahankan baris dan atribut produk dengan `active=false`; lookup/list tidak menampilkannya. Uji juga GET/update/delete ulang terhadap produk nonaktif serta migrasi data V1 ke V2.

- Create/PUT dengan harga atau stok negatif ditolak; field wajib yang hilang ditangani sebagai 400.
- Lookup/update ID yang tidak ada menghasilkan 404 pada operasi yang mendokumentasikannya.
- PATCH mempertahankan field yang tidak dikirim. Body kosong tidak memenuhi `minProperties: 1`.
- Penghapusan yang melanggar policy transaksi menghasilkan 409 jika policy tersebut dipilih dan kondisi test tersedia.
- Bila version/optimistic locking diterapkan, uji update bersaing dan petakan konflik sesuai kontrak yang disepakati.

### Cart

- Create Cart mengembalikan ID yang dapat dibaca lewat GET dan dipakai untuk tambah item; customerId yang hilang menghasilkan 400. Aturan validasi identitas customer masih mengikuti OPEN-010.
- POST item pada Cart yang tidak ada menghasilkan 404; tidak membuat Cart otomatis.
- Quantity 0/negatif ditolak; Cart atau item yang tidak ditemukan mengikuti respons 404.
- Update quantity melebihi stok menghasilkan konflik sesuai operasi update yang mendokumentasikan 409.
- Uji quantity akhir ketika produk yang sama ditambahkan kembali: quantity awal 2 ditambah 3 menghasilkan satu item dengan quantity 5 (OPEN-002 A).
- Perubahan harga katalog setelah penambahan awal tidak mengubah harga checkout item tersimpan (OPEN-003 B). Aturan harga quantity tambahan dan masa berlaku belum diputuskan.
- Subtotal dan total sesuai policy harga; jangan mempercayai total bebas dari client.
- Dependency Product 404 dibedakan dari timeout/5xx, dan tidak ditutupi dengan harga/stok default.

### Order

- Cart kosong, Cart tidak ada, atau produk tidak layak checkout memiliki perilaku yang diputuskan dan didokumentasikan sebelum status error dipatok.
- Uji snapshot/total jika rancangan snapshot diterapkan; perubahan harga katalog sesudah create tidak mengubah riwayat.
- Perubahan alamat saat status melarang menghasilkan konflik; fixture status mengikuti policy yang disepakati.
- Coba mengubah total/status melalui PATCH dan periksa bahwa pembatasan atribut diterapkan serta kontraknya selaras.
- Kegagalan penyimpanan OrderItem membatalkan perubahan lokal Order yang berada pada transaksi yang sama.

### Payment

- Tahap 0: [desain teknis](payment-service-technical-design.md) menetapkan key ASCII 1–128 case-sensitive, canonicalization berbasis nilai, klaim lease/fencing, lock budget, dan recovery. Tests implementasi wajib menguji takeover stale claim, pemilik lama ditolak, partial index/guard dengan key berbeda, commit ambigu, dan cleanup tanpa menghapus PROCESSING.
- Simulator mengikuti specs/payment-simulator.openapi.yaml: profil local/test + flag wajib, profil prod menonaktifkan, startup memeriksa loopback/token, token hilang/salah 401, disabled 404, dan hasil final bertentangan 409. Seluruhnya masih rencana tests.
- Order tidak ditemukan menghasilkan 404; method di luar enum input ditolak.
- Pada rancangan amount-from-Order, uji bahwa total yang dipakai berasal dari backend Order.
- Penulisan transactionReference tidak otomatis mengubah status menjadi SUCCESS.
- Perubahan metadata saat kondisi transaksi melarang menghasilkan 409.
- Uji repeated request/attempt berdasarkan policy idempotency yang dipilih; jangan mengklaim aman dari duplikasi jika mekanismenya belum ada.
- I1-A: POST Payment tanpa header Idempotency-Key menghasilkan 400.
- I2-A: setelah create berhasil dan status Payment berubah, retry dengan key/payload yang sama mengembalikan status HTTP/body create pertama tanpa resource tambahan; GET Payment mengembalikan state terkini. Uji retensi 24 jam dengan clock terkontrol.
- Key sama dengan payload berbeda menghasilkan 409 IDEMPOTENCY_KEY_REUSED. Urutan properti/whitespace JSON yang berbeda tidak mengubah makna orderId/method.
- Error sebelum commit tidak disimpan sebagai replay final dan memungkinkan retry setelah penyebab teratasi. Jika Payment dan hasil idempotency sudah commit tetapi pengiriman respons gagal, retry menghasilkan replay tanpa duplikasi. Gunakan fault injection terkontrol untuk membedakan skenario tersebut.
- Amount pecahan seperti 350000.125 tetap sama dari Order hingga domain, PostgreSQL, dan respons HTTP, tanpa pembulatan diam-diam.
- PATCH Payment menolak null, kosong/whitespace, body kosong, field asing, serta upaya mengubah amount/status. PATCH sukses mengembalikan schema Payment beserta reference yang disimpan.
- Concurrency tolak sementara: saat operasi pertama ditahan pada titik sinkronisasi terkontrol, duplikat key/payload sama menghasilkan 409 REQUEST_IN_PROGRESS tanpa resource tambahan. Setelah create selesai, retry menghasilkan replay respons awal. Payment PENDING yang operasinya sudah selesai tidak menghasilkan REQUEST_IN_PROGRESS hanya karena belum dibayar. Model ini tidak menjamin urutan retry FIFO.
- DEC-016 memilih simulator untuk automated tests dan demo lokal. Uji hasil SUCCESS/FAILED secara deterministik, amount tetap, reference tidak mengesahkan pembayaran, dan penolakan perubahan reference pada state terminal.
- Setelah endpoint simulator development dirancang, uji ketersediaannya hanya ketika diaktifkan pada development/test dan ketidaktersediaannya pada environment lain. Catat hasil sebagai simulasi, bukan pembayaran provider nyata.
- Pada COD, fixture hasil SUCCESS simulator menyatakan uang diterima saat penyerahan barang; create Payment dan pencatatan pengiriman saja tidak cukup. Hasil final identik tidak menghasilkan perubahan/efek tambahan; SUCCESS ke FAILED atau FAILED ke SUCCESS ditolak 409. Detail deduplikasi event dan request bersamaan diuji setelah desain teknis ditetapkan.
- DEC-017: milestone Payment terisolasi tidak mengubah status atau database Order; gunakan stub Order untuk tests lokal dan verifikasi integrasi nyata ketika Order tersedia.

### PATCH dan schema yang belum lengkap

Penolakan field asing, null eksplisit, serta batas panjang/skala yang lebih ketat memerlukan keputusan dan sinkronisasi kontrak. Tipe nullable saja tidak membedakan field tidak dikirim dari null. Tambahkan tests untuk implementasi presence yang dipakai, bukan mengasumsikannya bekerja otomatis.

## 6. Skenario lintas layanan

Prasyarat: empat API tersedia, database siap, dependency URL benar, serta fixture customer dan aturan checkout yang diuji sudah ditetapkan.

1. Buat Product dengan harga/stok fixture yang diketahui, lalu simpan ID respons.
2. Buat Cart melalui POST `/carts` dengan customerId fixture; simpan ID respons, periksa items kosong dan total 0, lalu tambahkan item memakai ID tersebut. Endpoint create Cart merupakan perluasan kontrak OPEN-001 B.
3. Baca Cart dan periksa items/total menurut policy harga.
4. Buat Order menggunakan cartId dan customerId fixture; simpan ID respons.
5. Baca Order, lalu uji perubahan alamat dalam keadaan yang diizinkan.
6. Buat permintaan Payment menggunakan orderId dan method dari enum.
7. Baca Payment dan periksa amount/status sesuai rancangan yang diterapkan.
8. Uji kegagalan dependency dan perubahan state yang dilarang pada fixture terpisah.

Gunakan ID yang dikembalikan server, bukan mematok contoh laporan 1/501/9001. Keberhasilan skenario ini belum membuktikan transfer uang nyata atau reservation stok atomik.

## 7. Menjalankan pemeriksaan

Dari root repository:

```bash
./mvnw validate
./mvnw -pl services/product-service -am compile
```

Setelah dependency test, datasource/profile test, dan fixture siap:

```bash
./mvnw -pl services/product-service -am test
./mvnw verify
```

`verify` hanya menjalankan integration test bila plugin/lifecycle-nya memang dikonfigurasi. Command sukses dengan nol tests bukan bukti pengujian fitur. Jangan menghapus generated context test hanya untuk menyembunyikan konfigurasi datasource yang belum benar.

## 8. Load test pada tahap berikutnya

Tentukan endpoint, dataset, concurrency, durasi/jumlah pengulangan, hardware/resource, serta tujuan sebelum menjalankan Siege. Pisahkan baca katalog dari operasi create/update yang mengubah state.

Catat success/failure, response time, dan penggunaan resource. Jika trafik melewati port-forward, hasilnya mencakup overhead jalur tersebut dan tidak boleh disajikan sebagai kapasitas maksimum deployment produksi. Motivasi ribuan/jutaan pengguna pada latar belakang laporan bukan hasil ukur project.

## 9. Format pencatatan hasil

Untuk setiap eksekusi, simpan:

| Field | Isi |
|---|---|
| Tanggal dan commit | Waktu pengujian dan revision source |
| Environment | Versi Java/framework/database, mode lokal/cluster, resource yang dipakai |
| Command/skenario | Perintah nyata dan ID test yang dijalankan |
| Fixture/prasyarat | Data, state, dan dependency yang diperlukan |
| Expected | Hasil yang ditetapkan kontrak/keputusan |
| Actual | Hasil observasi, bukan rencana |
| Status | Belum dijalankan / Lulus / Gagal / Terhambat |
| Bukti | Path report/log yang relevan tanpa secret/token |

Maven reports biasanya berada pada `target/` module. Pilih bukti ringkas yang memang ingin dikomit ke dokumentasi; output mentah sementara dapat disimpan pada direktori results yang diabaikan Git. Jangan mengubah status menjadi lulus hanya karena folder tests sudah dibuat.
