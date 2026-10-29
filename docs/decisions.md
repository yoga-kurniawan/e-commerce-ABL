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
| DEC-003 | Baseline sumber 17 operasi pada 9 path; kontrak project diperluas menjadi 18 operasi pada 10 path | Baseline laporan, diperluas melalui OPEN-001 pilihan B pada 29 Oktober 2026 | Tambahkan POST `/carts` pada prefix `/api/v1`; perluasan berikutnya memerlukan keputusan baru |
| DEC-004 | Java 17 dan Spring Boot 4.1.2 | Dipilih untuk project; parent/module telah lolos validate | Versi build dipusatkan pada parent POM |
| DEC-005 | Maven multi-module dengan Wrapper | Dipilih untuk project dan dikonfirmasi lewat validate | Empat module tetap menghasilkan aplikasi/JAR terpisah |
| DEC-006 | Struktur APS V3: domain/usecase/adapter | Dipilih sebagai adaptasi materi APS dan gambar referensi | Domain terpisah dari JPA; use case bergantung pada port |
| DEC-007 | PostgreSQL 17, data/database per layanan | PostgreSQL dipilih pada rancangan; independensi data sejalan laporan h. 4 | Constraint/migrasi lokal; kesiapan database belum dikonfirmasi |
| DEC-008 | Target deployment Kubernetes/Minikube, Istio, Helm | Dipilih pada rancangan, setup ditunda pengguna | Konfigurasi ditulis pada tahap deployment, bukan prasyarat menulis domain |
| DEC-009 | Port HTTP lokal 8081–8084; contoh ingress publik 8080 | Port lokal pilihan teknis; ingress contoh laporan h. 16 | Bedakan akses service langsung dari base URL publik |
| DEC-010 | OpenAPI tersendiri mengikuti baseline laporan dan perluasan project yang dicatat | Kontrak awal berasal dari laporan; pengguna meminta penambahan kontrak create Cart setelah memilih OPEN-001 B | Perluasan dan pengetatan harus memiliki catatan keputusan; penambahan kontrak tidak membuktikan endpoint telah diimplementasikan |

## 3. Keputusan bisnis dan pertanyaan terbuka

| ID | Pertanyaan | Dasar/kesenjangan | Pilihan atau keputusan | Dampak / tindak lanjut |
|---|---|---|---|---|
| OPEN-001 | Bagaimana Cart awal dibuat? | Pilihan B disetujui pengguna pada 29 Oktober 2026; pengguna meminta penambahan kontrak | Diterima: client membuat Cart melalui POST `/api/v1/carts`; ID dihasilkan backend | OpenAPI diperluas; implementasi/tests belum tersedia; detail Cart aktif dan autentikasi tetap terbuka |
| OPEN-002 | Apa arti POST produk yang sudah ada di Cart? | Pilihan 2A disetujui pengguna pada 29 Oktober 2026 | Diterima: tambahkan quantity pada item produk yang sama | Satu item per produk dalam Cart; validasi stok memakai quantity akhir; implementasi/tests belum tersedia |
| OPEN-003 | Harga mana yang berlaku saat checkout? | Pilihan 3B disetujui pengguna pada 29 Oktober 2026 | Diterima: gunakan harga yang disimpan saat produk masuk Cart | Cart perlu menyimpan harga satuan; Order memakai harga Cart; detail masa berlaku dan penambahan ulang masih terbuka |
| OPEN-004 | Kapan stok direservasi/dikurangi? | Validasi stok disebut; workflow atomik lintas layanan belum ada | Reservation saat checkout/payment atau workflow lain | Risiko overselling dan compensation tidak terdefinisi |
| OPEN-005 | Bagaimana menjaga riwayat Product? | Laporan h. 31 menyebut soft delete sebagai opsi | Nonaktifkan/soft delete, atau policy delete dengan konflik | Perilaku GET/delete dan referensi transaksi belum final |
| OPEN-006 | Status mana yang boleh mengubah alamat? | Laporan h. 35 mencontohkan sebelum diproses | Hanya PENDING atau state rule yang ditetapkan tim | Handler/state tests tidak dapat dipatok |
| OPEN-007 | Bagaimana pembayaran diverifikasi? | Laporan h. 37 mengharuskan sumber backend/provider yang dipercaya | Mock terkontrol untuk praktikum atau integrasi provider | SUCCESS/FAILED belum punya alur sah |
| OPEN-008 | Apakah satu Order boleh memiliki beberapa Payment attempt? | Belum ada policy attempt | Satu request aktif atau beberapa attempt dengan aturan | Constraint UNIQUE(order_id) dan retry belum dapat dipilih |
| OPEN-009 | Bagaimana idempotency create Order/Payment? | Belum ada kontrak/penyimpanan idempotency | Key, penyimpanan hasil, dan aturan pengulangan yang disepakati | Retry dapat menggandakan transaksi |
| OPEN-010 | Bagaimana identitas/kepemilikan customer dibuktikan? | Input customerId belum mendefinisikan autentikasi | Fixture lokal atau rancangan auth/otorisasi | ID client tidak dapat dianggap identitas terverifikasi |
| OPEN-011 | Presisi uang, pembulatan, panjang nama, dan batas ID? | Schema belum mengatur semua detail | BigDecimal/NUMERIC dan aturan skala yang disepakati | DTO, SQL, dan total dapat tidak selaras |
| OPEN-012 | Apakah snapshot item disimpan pada Order? | Diusulkan APS V3; schema internal belum diberikan laporan | Simpan item/nama/harga saat create dengan policy harga | Riwayat dan audit perhitungan perlu desain eksplisit |

Baris berstatus diterima merupakan keputusan pengguna untuk project, bukan bukti persetujuan seluruh tim atau implementasi selesai. Opsi pada baris lainnya belum disetujui. ID OPEN dipertahankan agar referensi tetap dapat ditelusuri. Rincian keputusan yang diterima dicatat di bawah.

### 3.1 OPEN-001 — Pembuatan Cart melalui endpoint khusus

- **Status:** diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan B; kontrak ditambahkan atas permintaan pengguna; endpoint belum diimplementasikan atau diuji.
- **Pilihan yang dipertimbangkan:** fixture lokal/test (A), endpoint create Cart (B), atau pembuatan otomatis ketika item pertama ditambahkan (C).
- **Keputusan:** client memanggil POST `/api/v1/carts`; backend menyimpan Cart kosong dan menghasilkan ID. Respons 201 membawa Cart dengan items kosong dan total 0. Client membaca ID respons untuk operasi item dan checkout. POST item tidak membuat Cart secara otomatis.
- **Pertimbangan desain:** pembuatan Cart menjadi alur eksplisit yang dapat dipanggil client dan diuji tanpa Cart yang disiapkan sebelumnya. Fixture tetap boleh digunakan untuk tests terisolasi.
- **Rancangan kontrak awal:** request JSON memakai CartInput dengan customerId wajib, selaras dengan referensi pelanggan pada OrderInput; respons Cart memuat customerId. Field ini merupakan rancangan teknis awal untuk menghubungkan data, bukan keputusan autentikasi atau bukti kepemilikan terverifikasi. Bentuk ini dapat disesuaikan saat OPEN-010 diselesaikan.
- **Dampak domain/database/API:** tambahkan use case create Cart, port penyimpanan, controller, mapping referensi pelanggan, dan pengujian. OpenAPI kini memuat 18 operasi pada 10 path, termasuk enam operasi Cart; baseline sumber tetap 17 operasi pada 9 path.
- **Risiko/trade-off:** penambahan endpoint memperluas kontrak. Aturan satu/beberapa Cart aktif per customer, request berulang, lifecycle setelah checkout, dan pemeriksaan kepemilikan belum diputuskan. Kontrak belum menjanjikan get-or-create atau idempotency.
- **Tests yang diperlukan:** request valid menghasilkan 201, ID backend, items kosong, total 0, dan referensi customer yang sesuai; GET ID respons menemukan Cart tersimpan; request tanpa customerId ditolak 400; Cart hasil create dapat menerima item; POST item dengan Cart yang tidak ada tetap menghasilkan 404.
- **Keputusan yang digantikan:** tidak ada; menyelesaikan pilihan utama OPEN-001.

### 3.2 OPEN-002 — Penambahan produk yang sudah ada di Cart

- **Status:** diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan 2A; belum diimplementasikan atau diuji.
- **Pilihan yang dipertimbangkan:** menambah quantity, menolak duplikat, atau mengganti quantity.
- **Keputusan:** POST item produk yang sudah ada menambah quantity pada item yang sama. Contoh quantity awal 2 ditambah request quantity 3 menghasilkan satu item dengan quantity 5. PUT/PATCH tetap menetapkan quantity tujuan sesuai kontrak.
- **Pertimbangan desain:** perilaku ini sesuai makna menambahkan item dan membedakan POST dari operasi penggantian quantity.
- **Dampak domain/database:** cegah dua item untuk produk yang sama dalam satu Cart; periksa stok terhadap quantity akhir. Strategi constraint, locking, dan pemetaan error ditetapkan saat desain teknis diselesaikan.
- **Dampak API:** tidak menambahkan endpoint atau field input; semantik penambahan quantity sudah dicantumkan pada deskripsi operasi OpenAPI dan aturan bisnis.
- **Risiko/trade-off:** request POST yang diulang dapat menambah quantity lagi. Keputusan ini belum menyediakan idempotency. Penambahan ulang setelah harga katalog berubah memerlukan aturan harga tambahan terkait OPEN-003.
- **Tests yang diperlukan:** produk baru membuat satu item; produk yang sama menambah quantity tanpa item duplikat; quantity akhir yang tidak diizinkan ditolak tanpa menyimpan perubahan parsial; subtotal/total mengikuti aturan harga yang disepakati.
- **Keputusan yang digantikan:** tidak ada; menyelesaikan pilihan utama OPEN-002.

### 3.3 OPEN-003 — Harga saat produk masuk Cart berlaku untuk checkout

- **Status:** diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan 3B; belum diimplementasikan atau diuji.
- **Pilihan yang dipertimbangkan:** harga terbaru saat checkout, harga saat masuk Cart, atau penolakan checkout bila harga berubah.
- **Keputusan:** checkout memakai harga satuan yang disimpan ketika produk masuk Cart. Perubahan harga katalog sesudahnya tidak otomatis mengganti harga item Cart tersebut. Contoh produk masuk Cart pada harga Rp100.000, lalu katalog berubah menjadi Rp120.000: checkout item yang tersimpan tetap memakai Rp100.000, selama memenuhi kebijakan masa berlaku yang masih perlu diputuskan.
- **Pertimbangan desain:** menjaga konsistensi harga Cart dengan harga yang digunakan untuk membentuk Order.
- **Dampak domain/database:** Cart menyimpan harga satuan yang diperoleh backend dari Product saat penambahan awal. Subtotal dihitung dari quantity dan harga tersimpan; total merupakan jumlah subtotal. Order memperoleh harga checkout dari data Cart, bukan menggantinya dengan harga katalog terkini.
- **Dampak API:** input Cart tetap productId dan quantity; harga tidak ditentukan client. Respons Cart saat ini hanya memuat productId, quantity, dan subtotal per item. Cara Order memperoleh harga satuan tersimpan perlu diputuskan secara eksplisit; penambahan field/kontrak belum disetujui melalui keputusan ini. Penyimpanan snapshot Order tetap dibahas pada OPEN-012.
- **Risiko/trade-off:** harga lama dapat berbeda dari harga katalog; aturan masa berlaku diperlukan. Harga untuk quantity tambahan setelah perubahan katalog, serta perilaku hapus lalu tambah ulang, belum diputuskan. Jangan menganggap harga berlaku selamanya atau aturan penambahan ulang sudah final.
- **Tests yang diperlukan:** perubahan harga katalog setelah penambahan item tidak mengubah harga checkout item yang tersimpan; perhitungan subtotal/total konsisten; Order menggunakan harga Cart. Tests masa berlaku dan penambahan ulang dilengkapi setelah aturannya dipilih.
- **Keputusan yang digantikan:** tidak ada; menyelesaikan pilihan utama OPEN-003 dengan detail lanjutan yang masih terbuka.

README, business-rules, architecture, testing, dan OpenAPI diperbarui untuk mencerminkan keputusan utama OPEN-001/OPEN-002/OPEN-003. Perubahan schema respons Cart untuk membawa harga satuan ke Order masih menunggu keputusan lanjutan; penambahan customerId pada kontrak create Cart tidak menyelesaikan kebutuhan tersebut.

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
