# Keputusan desain dan pekerjaan terbuka

[Kembali ke README](../README.md) · [Aturan bisnis](business-rules.md)

## 1. Konvensi pencatatan

Status **ditetapkan laporan** berarti ada dasar eksplisit pada dokumen sumber. **Dipilih untuk project** berarti pilihan teknis dalam pembahasan, bukan isi literal laporan. **Usulan/belum diputuskan** belum boleh diperlakukan sebagai aturan implementasi final.

Penerapan suatu keputusan juga berbeda dari status operasional: memilih PostgreSQL belum membuktikan database sudah berjalan. Catatan awal ini disusun dari laporan dan progres yang dikonfirmasi sampai 29 Oktober 2026.

## 2. Baseline yang sudah memiliki dasar

| ID | Keputusan | Status dan dasar | Konsekuensi |
|---|---|---|---|
| DEC-001 | Empat layanan: Product, Cart, Order, Payment | Ditetapkan laporan h. 6–7 | Batas tanggung jawab dan API mengikuti empat layanan |
| DEC-002 | REST/JSON, OpenAPI 3.0.3, API 1.0.0 | Ditetapkan laporan h. 8/16 | Request/response dan dokumentasi mengikuti kontrak |
| DEC-003 | 17 operasi pada 9 path | Gabungan daftar h. 11/27 dan YAML h. 16–25/38–44 | Tidak menambahkan endpoint publik tanpa keputusan baru |
| DEC-004 | Java 17 dan Spring Boot 4.1.2 | Dipilih untuk project; parent/module telah lolos validate | Versi build dipusatkan pada parent POM |
| DEC-005 | Maven multi-module dengan Wrapper | Dipilih untuk project dan dikonfirmasi lewat validate | Empat module tetap menghasilkan aplikasi/JAR terpisah |
| DEC-006 | Struktur APS V3: domain/usecase/adapter | Dipilih sebagai adaptasi materi APS dan gambar referensi | Domain terpisah dari JPA; use case bergantung pada port |
| DEC-007 | PostgreSQL 17, data/database per layanan | PostgreSQL dipilih pada rancangan; independensi data sejalan laporan h. 4 | Constraint/migrasi lokal; kesiapan database belum dikonfirmasi |
| DEC-008 | Target deployment Kubernetes/Minikube, Istio, Helm | Dipilih pada rancangan, setup ditunda pengguna | Konfigurasi ditulis pada tahap deployment, bukan prasyarat menulis domain |
| DEC-009 | Port HTTP lokal 8081–8084; contoh ingress publik 8080 | Port lokal pilihan teknis; ingress contoh laporan h. 16 | Bedakan akses service langsung dari base URL publik |
| DEC-010 | OpenAPI tersendiri mengikuti YAML laporan | Diminta pengguna; file telah disalin ke project | Kelengkapan/pengetatan tambahan tidak dimasukkan tanpa catatan |

## 3. Keputusan bisnis yang belum selesai

| ID | Pertanyaan | Dasar/kesenjangan | Pilihan untuk ditinjau | Dampak bila belum selesai |
|---|---|---|---|---|
| OPEN-001 | Bagaimana Cart awal dibuat? | Tidak ada POST `/carts` pada daftar awal | Fixture profil local, atau perluasan API | Alur tambah item tidak memiliki Cart awal |
| OPEN-002 | Apa arti POST produk yang sudah ada di Cart? | Perilaku duplikat belum dirinci | Tambah quantity, atau tolak duplikat | Quantity akhir dan tests tidak konsisten |
| OPEN-003 | Harga mana yang berlaku saat checkout? | Cart menampilkan subtotal; kebijakan perubahan harga belum lengkap | Harga Cart dibekukan, validasi harga terbaru, atau konfirmasi perubahan | Total Order dan ekspektasi pengguna berbeda |
| OPEN-004 | Kapan stok direservasi/dikurangi? | Validasi stok disebut; workflow atomik lintas layanan belum ada | Reservation saat checkout/payment atau workflow lain | Risiko overselling dan compensation tidak terdefinisi |
| OPEN-005 | Bagaimana menjaga riwayat Product? | Diselesaikan oleh DEC-011 | Soft delete melalui status aktif internal | Data produk tetap tersimpan; lihat keputusan di bawah |
| OPEN-006 | Status mana yang boleh mengubah alamat? | Laporan h. 35 mencontohkan sebelum diproses | Hanya PENDING atau state rule yang ditetapkan tim | Handler/state tests tidak dapat dipatok |
| OPEN-007 | Bagaimana pembayaran diverifikasi? | Laporan h. 37 mengharuskan sumber backend/provider yang dipercaya | Mock terkontrol untuk praktikum atau integrasi provider | SUCCESS/FAILED belum punya alur sah |
| OPEN-008 | Apakah satu Order boleh memiliki beberapa Payment attempt? | Belum ada policy attempt | Satu request aktif atau beberapa attempt dengan aturan | Constraint UNIQUE(order_id) dan retry belum dapat dipilih |
| OPEN-009 | Bagaimana idempotency create Order/Payment? | Belum ada kontrak/penyimpanan idempotency | Key, penyimpanan hasil, dan aturan pengulangan yang disepakati | Retry dapat menggandakan transaksi |
| OPEN-010 | Bagaimana identitas/kepemilikan customer dibuktikan? | Input customerId belum mendefinisikan autentikasi | Fixture lokal atau rancangan auth/otorisasi | ID client tidak dapat dianggap identitas terverifikasi |
| OPEN-011 | Presisi uang, pembulatan, panjang nama, dan batas ID? | Schema belum mengatur semua detail | BigDecimal/NUMERIC dan aturan skala yang disepakati | DTO, SQL, dan total dapat tidak selaras |
| OPEN-012 | Apakah snapshot item disimpan pada Order? | Diusulkan APS V3; schema internal belum diberikan laporan | Simpan item/nama/harga saat create dengan policy harga | Riwayat dan audit perhitungan perlu desain eksplisit |

Opsi pada tabel belum diperlakukan sebagai keputusan yang disetujui. Keputusan yang sudah ditetapkan harus mencantumkan alasan, dampak kontrak, dan tests yang membuktikannya.

## 4. Perbaikan kontrak yang perlu ditinjau

| ID | Temuan | Perubahan yang dibahas | Status |
|---|---|---|---|
| API-001 | shippingAddress hanya terlihat pada contoh/PATCH, belum pada schema Order awal | Tambahkan atribut response bila menjadi kontrak yang diimplementasikan | Belum diputuskan |
| API-002 | transactionReference belum pada schema Payment awal | Selaraskan schema respons dengan perilaku PATCH | Belum diputuskan |
| API-003 | Response 200 tambahan Cart/Order/Payment hanya description pada YAML | Definisikan schema berdasarkan narasi/keputusan final | Belum diputuskan |
| API-004 | PATCH tidak menetapkan additionalProperties false | Tentukan penolakan field asing dan null semantics | Belum diputuskan; narasi membatasi atribut yang boleh diubah |
| API-005 | Beberapa error workflow/dependency belum dicantumkan per operasi | Tambahkan status/body setelah memilih pemetaan error | Belum diputuskan |

Jangan menganggap response tanpa `content` di schema harus tanpa body. Narasi memberi contoh body pada beberapa operasi tambahan. Sebaliknya, DELETE 204 secara eksplisit tidak memiliki body.

## 5. Keputusan teknis yang perlu dilengkapi

| ID | Pekerjaan | Arahan awal | Status |
|---|---|---|---|
| TECH-001 | Domain dan JPA entity | Domain POJO; adapter database memetakan ke JPA entity | Rancangan dipilih; implementasi belum dibuktikan |
| TECH-002 | Transaksi dan locking | Transaksi lokal singkat; version/locking mengikuti kebutuhan | Detail belum diputuskan |
| TECH-003 | Konfigurasi tests | Unit dengan mock port, integration memakai DB yang sesuai | Rencana, belum ada hasil tests |
| TECH-004 | HTTP client | REST adapter dengan timeout dan error mapping | Rancangan; nilai timeout ditetapkan saat implementasi |
| TECH-005 | Image dan deployment versions | Pin versi/digest yang diuji saat deployment | Ditunda bersama setup cluster |
| TECH-006 | Template konfigurasi lokal | Contoh tanpa secret; environment terminal/IDE dipilih eksplisit | Belum semua konfigurasi ditulis |

## DEC-011: Soft delete Product

- Status: diterima, 29 Oktober 2026, disepakati pengguna dalam pengerjaan Product.
- Dasar: laporan h. 31 menawarkan soft delete; pengguna memilih mempertahankan data untuk history.
- DELETE menonaktifkan produk (`active=false`), tanpa menghapus baris database.
- Produk baru dan produk lama saat migrasi V2 berstatus aktif. GET/list hanya membaca produk aktif.
- GET, PUT, PATCH, dan DELETE terhadap produk nonaktif diperlakukan sebagai resource tidak ditemukan.
- Status aktif merupakan atribut internal, tidak ditambahkan ke input/output API. Tidak ada endpoint reaktivasi.
- Tidak ada lookup transaksi lintas service pada delete. Status 409 tetap tercantum dalam kontrak sumber, tetapi belum memiliki kondisi bisnis pada policy ini.
- Penyimpanan baris mempertahankan data terakhir, bukan audit seluruh perubahan harga/stok. Snapshot Order tetap diperlukan untuk riwayat transaksi.
- Bukti yang diuji: migrasi V1 ke V2 mempertahankan produk lama sebagai aktif; soft delete menyembunyikan produk dari port dan mempertahankan baris serta atributnya.

## 6. Template keputusan berikutnya

Untuk keputusan baru, catat bagian berikut pada dokumen ini:

```text
ID dan judul:
Status: usulan / diterima / digantikan
Tanggal dan pihak yang menyepakati:
Masalah serta sumbernya:
Pilihan yang dipertimbangkan:
Keputusan dan alasan:
Dampak pada domain, database, dan API:
Risiko/trade-off:
Tests atau bukti yang diperlukan:
Keputusan yang digantikan, jika ada:
```

Perbarui README, architecture, business-rules, development, testing, dan OpenAPI sesuai dampak keputusan. Jangan menandai keputusan selesai hanya karena folder atau class dengan nama terkait sudah dibuat.
