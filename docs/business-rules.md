# Aturan bisnis dan batas kontrak

[Kembali ke README](../README.md) · [Keputusan terbuka](decisions.md) · [OpenAPI](../specs/openapi.yaml)

## 1. Cara membaca dokumen

- **Ketentuan laporan:** perilaku atau constraint yang dinyatakan pada narasi maupun YAML laporan.
- **Contoh laporan:** ilustrasi input/output yang tidak selalu menentukan semua kebijakan bisnis.
- **Belum diputuskan:** pilihan implementasi yang tidak boleh dianggap aturan final sebelum disepakati.

Schema sumber dan narasi tidak selalu merinci hal yang sama. Dokumen ini menunjukkan perbedaannya; file OpenAPI tidak diperketat atau diperluas diam-diam.

Semua endpoint pada tabel memakai prefix `/api/v1`. Versi API 1.0.0 dan OpenAPI 3.0.3 berasal dari laporan h. 8 dan 16. Kontrak project diperluas menjadi 18 operasi pada 10 path melalui keputusan OPEN-001; baseline laporan memuat 17 operasi pada 9 path.

## 2. Product Service

| Method | Path | Input utama | Sukses | Error yang dicantumkan |
|---|---|---|---|---|
| GET | `/products` | Tidak ada parameter wajib | 200, daftar Product | 500 |
| GET | `/products/{productId}` | productId integer | 200, Product | 404 |
| POST | `/products` | name, price, stock | 201, Product | 400 |
| PUT | `/products/{productId}` | name, price, stock lengkap | 200, Product | 400, 404 |
| PATCH | `/products/{productId}` | Sebagian name/price/stock | 200, Product | 400, 404 |
| DELETE | `/products/{productId}` | productId integer | 204 tanpa body | 404, 409 |

Ketentuan sumber, h. 12, 21–22, 28–31, 38–40:

- ProductInput mewajibkan name, price, dan stock.
- Harga tidak negatif; stok merupakan integer tidak negatif.
- PUT membawa seluruh atribut wajib pada representasi pengganti.
- PATCH mengubah atribut yang dikirim; atribut lain tetap. Schema PATCH memuat `minProperties: 1`.
- DELETE perlu mempertimbangkan keterkaitan dengan transaksi. Soft delete/penonaktifan adalah pilihan yang disebut laporan, belum satu-satunya kebijakan yang diwajibkan.

Keputusan project DEC-011: DELETE memakai soft delete (`active=false`). GET/list hanya menampilkan produk aktif; GET/PUT/PATCH/DELETE terhadap produk nonaktif menghasilkan 404. Data terakhir tetap ada di database, tanpa endpoint history atau reaktivasi. Atribut `active` hanya internal. Tidak ada pengecekan keterkaitan transaksi pada policy ini; kondisi 409 belum diterapkan.

PATCH kosong ditolak. Null eksplisit pada name/price/stock ditolak karena atribut wajib domain dan schema tidak menyatakan nullable; field yang tidak dikirim tetap dipertahankan.

Belum dirinci: nama kosong/panjang nama, presisi/skala mata uang, pembulatan, dan optimistic locking. Jangan menganggap schema asal sudah memiliki `minLength`, `maxLength`, atau `additionalProperties: false`.

## 3. Cart Service

| Method | Path | Input utama | Sukses | Error yang dicantumkan |
|---|---|---|---|---|
| POST | `/carts` | customerId wajib; rancangan kontrak awal OPEN-001 | 201, Cart kosong dengan ID backend | 400 |
| GET | `/carts/{cartId}` | cartId integer | 200, Cart | 404 |
| POST | `/carts/{cartId}/items` | productId, quantity | 201, Cart pada YAML | 400, 404 |
| PUT | `/carts/{cartId}/items/{productId}` | quantity wajib | 200, contoh item diperbarui | 400, 404, 409 |
| PATCH | `/carts/{cartId}/items/{productId}` | quantity | 200, contoh item diperbarui | 400, 404, 409 |
| DELETE | `/carts/{cartId}/items/{productId}` | Identifier Cart dan Product | 204 tanpa body | 404 |

Ketentuan sumber, h. 13–14, 22–23, 31–34, 40–42:

- Quantity input minimal 1.
- Cart menampilkan items berisi productId, quantity, subtotal, serta total keranjang.
- PUT item mengganti quantity tujuan. Quantity merupakan satu-satunya atribut item yang dapat diubah pada representasi tersebut.
- PATCH membawa atribut yang ingin diperbarui; schema quantity memakai minimum 1 dan `minProperties: 1`.
- Jumlah yang melebihi stok/kondisi keranjang yang tidak mengizinkan perubahan ditunjukkan sebagai konflik pada operasi update.
- DELETE menghapus item tertentu, bukan seluruh Cart.

Contoh naratif respons update item memuat cartId, productId, quantity, dan subtotal. YAML tambahannya baru menuliskan deskripsi response 200, belum schema/content lengkap.

Keputusan project pada 29 Oktober 2026:

- **OPEN-001 B:** Cart dibuat melalui POST `/carts`; backend menghasilkan ID, items awal kosong, dan total 0. Client menggunakan ID respons untuk operasi berikutnya. Endpoint ini merupakan perluasan atas baseline laporan. Request awal memakai customerId sebagai referensi pelanggan; identitas dan otorisasi belum ditetapkan.
- **OPEN-002 A:** POST produk yang sudah ada menambah quantity pada item yang sama; validasi stok memakai quantity akhir. POST item tetap membutuhkan Cart yang sudah ada.
- **OPEN-003 B:** harga satuan disimpan dari backend Product saat produk masuk Cart dan digunakan saat checkout. Perubahan harga katalog tidak otomatis mengganti harga item yang tersimpan.

Belum diputuskan: satu/beberapa Cart aktif per customer, bukti kepemilikan Cart, masa berlaku Cart/harga, aturan harga saat produk ditambahkan ulang setelah perubahan harga, serta kapan stok benar-benar dipesan. Penyimpanan harga satuan diperlukan; cara mengekspos harga tersebut kepada Order masih perlu diputuskan.

## 4. Order Service

| Method | Path | Input utama | Sukses | Error yang dicantumkan |
|---|---|---|---|---|
| POST | `/orders` | cartId, customerId | 201, Order | 400 |
| GET | `/orders/{orderId}` | orderId integer | 200, Order | 404 |
| PATCH | `/orders/{orderId}` | Contoh atribut: shippingAddress | 200, contoh Order diperbarui | 400, 404, 409 |

Ketentuan sumber, h. 14–15, 23, 27–28, 34–35, 43:

- Pesanan dibuat berdasarkan keranjang pelanggan.
- Representasi awal Order memuat id, customerId, total, dan status.
- Enum schema status: PENDING, CONFIRMED, CANCELLED. Enum tidak mendefinisikan seluruh transisi antarstatus.
- PATCH dibatasi pada atribut yang diizinkan. Contoh laporan adalah perubahan alamat sebelum pesanan diproses.
- Total dan status pembayaran tidak boleh diubah bebas melalui PATCH ini.
- PUT dan DELETE Order tidak disediakan pada rancangan awal.

Harga checkout mengikuti harga tersimpan pada Cart (OPEN-003 B), bukan harga katalog terbaru. Belum diputuskan: status persis yang boleh mengubah alamat, Cart kosong, validasi kepemilikan customer, masa berlaku harga, pembekuan Cart, snapshot internal, stock reservation, dan penanganan request create berulang. Policy hanya-PENDING adalah usulan yang perlu disepakati, bukan ketentuan eksplisit laporan.

Rancangan APS V3 mengusulkan snapshot item/harga di Order dan lookup Product untuk field yang tidak tersedia lengkap pada Cart. Jangan menganggap usulan model internal ini sudah menjadi schema respons publik.

## 5. Payment Service

| Method | Path | Input utama | Sukses | Error yang dicantumkan |
|---|---|---|---|---|
| POST | `/payments` | orderId, method; header Idempotency-Key wajib | 201, Payment / replay hasil awal | 400, 404, 409, 500, 502, 503, 504 |
| GET | `/payments/{paymentId}` | paymentId integer | 200, Payment | 400, 404, 500, 503 |
| PATCH | `/payments/{paymentId}` | transactionReference | 200, Payment | 400, 404, 409, 500, 503 |

Perluasan teknis tabel di atas mengikuti DEC-018 dan [desain tahap 0](payment-service-technical-design.md), bukan klaim bahwa semua respons telah diimplementasikan. Simulator mempunyai [kontrak development/test terpisah](../specs/payment-simulator.openapi.yaml); 18 operasi publik tetap pada specs/openapi.yaml.

Ketentuan sumber, h. 15–16, 23–24, 27–28, 36–37, 43–44:

- Input create hanya mewajibkan orderId dan method; amount tidak termasuk input create yang didokumentasikan.
- Method pada schema input: TRANSFER, E_WALLET, COD.
- Representasi awal Payment memuat id, orderId, method, status, dan amount.
- Enum schema status: PENDING, SUCCESS, FAILED; transisi dan integrasi provider belum dirinci.
- PATCH dibatasi pada atribut yang diizinkan; contoh laporan memakai transactionReference.
- Client tidak boleh bebas menetapkan SUCCESS/FAILED. Status final semestinya berasal dari verifikasi provider atau mekanisme backend yang dipercaya.
- PUT dan DELETE Payment tidak disediakan pada rancangan awal.

Keputusan pengguna (DEC-012): amount diperoleh backend dari `Order.total`, yaitu total akhir yang telah memperhitungkan biaya/potongan jika fiturnya tersedia. Payment tidak menghitung ulang ongkir atau menambahkan biaya sendiri. Versi saat ini belum menyediakan perincian biaya tambahan; tanpa biaya/potongan, total sama dengan subtotal item. Status awal Payment adalah `PENDING`, termasuk COD. Representasi uang memakai BigDecimal/NUMERIC tanpa pembulatan diam-diam agar nilai sama dengan total Order.

DEC-013 menetapkan header `Idempotency-Key` wajib pada create Payment (I1-A); header yang tidak dikirim menghasilkan 400. Untuk create yang berhasil, retry dengan key dan payload yang sama mengembalikan status HTTP/body respons create pertama (I2-A), tanpa membuat Payment baru. Respons disimpan 24 jam sejak hasil create tersimpan; retry tidak memperpanjang retensi. Ini bukan batas waktu membayar atau expiry Payment. Setelah retensi dan cleanup, replay tidak dijamin; aturan attempt tetap berlaku dan riwayat Payment tidak dihapus. Status Payment terkini dibaca melalui GET Payment, termasuk untuk refresh dashboard; refresh Order tidak menyinkronkan Payment secara otomatis. Policy konflik payload, error sebelum commit, dan concurrency dicatat di bawah; scope/format key, canonicalization, persistence, serta recovery diselesaikan saat implementasi. Idempotency create Order belum ditetapkan.

DEC-014 menetapkan bahwa create Payment baru hanya untuk Order PENDING. Riwayat boleh memiliki beberapa attempt, tetapi maksimal satu PENDING per Order; setelah FAILED boleh membuat attempt baru jika Order masih layak, sedangkan setelah SUCCESS tidak boleh membuat attempt baru. Idempotent replay operasi yang sama bukan attempt baru. DEC-015 mengizinkan pengisian/koreksi transactionReference hanya selama Payment PENDING; SUCCESS/FAILED menolak perubahan.

Concurrency create dengan key/payload sama mengikuti model tolak sementara: operasi yang berhasil diklaim atomik diproses; duplikat saat operasi berjalan mendapat 409 REQUEST_IN_PROGRESS dan retry sendiri dengan key sama setelah jeda. Setelah create selesai, hasil awal direplay. Ini bukan antrean FIFO dan tidak menjamin urutan retry. Operasi create selesai berbeda dari Payment selesai dibayar; Payment PENDING tetap dapat memiliki respons create yang siap direplay. Key sama dengan payload berbeda ditolak 409 IDEMPOTENCY_KEY_REUSED. Hanya hasil create sukses disimpan untuk replay; error sebelum commit tidak menjadi hasil final, sedangkan commit yang berhasil tetap direplay meskipun pengiriman respons gagal. Detail teknis lock dan pemulihan proses diselesaikan saat implementasi.

PATCH hanya menerima transactionReference dan menolak null, kosong/whitespace, body kosong, serta field asing dengan 400. Reference hanya dapat diubah selama PENDING dan ditampilkan pada respons Payment setelah diisi; respons PATCH sukses memakai schema Payment. Batas panjang dan normalisasi diselesaikan saat implementasi.

DEC-016 menetapkan pilihan 6B-2: simulator backend untuk automated tests dan demo lokal, dengan hasil SUCCESS/FAILED yang deterministik. Endpoint simulator khusus development/test dipisahkan dari PATCH reference dan tidak tersedia pada produksi; route, schema, aktivasi, serta pembatasan aksesnya masih perlu ditetapkan. Pada COD, hasil SUCCESS simulator meniru konfirmasi uang diterima saat penyerahan barang. Simulator tidak membuktikan pembayaran nyata.

Simulator mengizinkan PENDING ke SUCCESS/FAILED. Hasil final yang sama tidak memproses perubahan/efek tambahan; hasil final yang bertentangan ditolak 409. Sesuai DEC-017, milestone Payment terisolasi tidak otomatis mengubah status Order. Integrasi provider/callback nyata, sinkronisasi Order, batas waktu pengerjaan, refund, serta penyelesaian COD nyata menjadi tahap berikutnya. Menulis transactionReference tidak membuktikan uang diterima; 201 berarti permintaan pembayaran dibuat, bukan transaksi keuangan selesai. Pembatalan setelah pembayaran sukses dan pengembalian dana memerlukan workflow tersendiri; SUCCESS tidak diubah menjadi FAILED untuk merepresentasikan refund.

## 6. Keterbatasan kontrak sumber

| Temuan | Sumber | Tindak lanjut |
|---|---|---|
| Endpoint membuat Cart tidak ada pada baseline laporan | Daftar h. 11 dan 27 | OPEN-001 B diterima: kontrak project menambahkan POST `/carts`; implementasi dan tests belum tersedia |
| shippingAddress tampil pada contoh PATCH, belum ada di schema Order awal | h. 23, 35, 43 | Selaraskan schema bila field dikembalikan sebagai kontrak terdokumentasi |
| transactionReference tampil pada contoh PATCH, belum ada di schema Payment awal | h. 24, 36, 44 | Selaraskan schema bila field dikembalikan |
| Response 200 tambahan Cart/Order/Payment belum memiliki content/schema | YAML h. 41–44 | Gunakan narasi sebagai bahan pembahasan; lengkapi kontrak melalui keputusan eksplisit |
| PATCH memakai minProperties tetapi tidak membatasi additionalProperties | YAML h. 39–44 | Tentukan whitelist/penolakan field asing dan dokumentasikan pengetatan |
| Null/presence, panjang string, dan beberapa status gagal belum lengkap | Schema sumber | Tentukan aturan sebelum menulis DTO, handler, dan tests final |
| Cara menyelesaikan pembayaran/stok lintas layanan belum dirinci | Lingkup keseluruhan laporan | Susun workflow lanjutan sebelum mengklaim checkout final |

Schema yang tidak menyebut content bukan perintah bahwa body response harus kosong. Khusus DELETE dengan 204, narasi secara eksplisit menyatakan tidak ada body.

## 7. Konvensi lintas layanan

- Gunakan JSON; request dengan body JSON memakai `Content-Type: application/json`. `Accept: application/json` menyatakan format respons yang diminta.
- Respons error mengikuti bentuk `code` dan `message`, misalnya NOT_FOUND/Data tidak ditemukan pada h. 37.
- Path parameter identifier bertipe integer dalam kontrak. Batas positif/format int64 belum dinyatakan pada seluruh schema sumber.
- Jangan menganggap semua error umum pada tabel h. 10 sudah tercantum pada setiap operasi; periksa response operasi masing-masing.
- Nilai ID dan nominal pada laporan adalah data ilustrasi. Tests harus membaca ID dari respons create, bukan mengharuskan selalu 1/501/9001.

## 8. Pekerjaan sebelum aturan dianggap final

Gunakan [decisions](decisions.md) untuk mencatat keputusan terbuka, perubahan kontrak, alasan, dan konsekuensi pengujiannya. Update dokumen, OpenAPI, source, dan tests bersama-sama setelah keputusan disetujui tim.
