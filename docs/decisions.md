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
| OPEN-005 | Bagaimana menjaga riwayat Product? | Diselesaikan oleh DEC-011 | Soft delete melalui status aktif internal | Data produk tetap tersimpan; lihat keputusan di bawah |
| OPEN-006 | Status mana yang boleh mengubah alamat? | Laporan h. 35 mencontohkan sebelum diproses | Diterapkan pada DEC-012: hanya PENDING, status lain 409 | Menunggu review tim; transisi status lain belum ditetapkan |
| OPEN-007 | Bagaimana pembayaran diverifikasi? | Pengguna memilih 6B-2; lihat DEC-016 | Diterima: simulator backend untuk automated tests dan demo lokal | Bentuk endpoint development, pembatasan akses, dan detail pemrosesan hasil perlu ditetapkan sebelum implementasi |
| OPEN-008 | Apakah satu Order boleh memiliki beberapa Payment attempt? | Diselesaikan pada tingkat policy oleh pilihan 3A; lihat DEC-014 | Diterima: beberapa attempt dalam riwayat, maksimal satu PENDING per Order; setelah SUCCESS tidak membuat attempt baru | Perlu constraint/locking dan tests concurrency; bukan UNIQUE(order_id) untuk seluruh riwayat |
| OPEN-009 | Bagaimana idempotency create Order/Payment? | Pengguna menerima penggunaan idempotency untuk create Payment; lihat DEC-013 | Payment: prinsip diterima, detail key/replay/concurrency belum diputuskan; Order tetap terbuka | Lengkapi kontrak dan persistence sebelum mengklaim request berulang aman |
| OPEN-010 | Bagaimana identitas/kepemilikan customer dibuktikan? | Input customerId belum mendefinisikan autentikasi | Fixture lokal atau rancangan auth/otorisasi | ID client tidak dapat dianggap identitas terverifikasi |
| OPEN-011 | Presisi uang, pembulatan, panjang nama, dan batas ID? | Schema belum mengatur semua detail | BigDecimal/NUMERIC dan aturan skala yang disepakati | DTO, SQL, dan total dapat tidak selaras |
| OPEN-012 | Apakah snapshot item disimpan pada Order? | Diusulkan APS V3; schema internal belum diberikan laporan | Diterapkan pada DEC-012: `order_items` menyimpan productId, nama, quantity, dan harga satuan Cart | Item bersifat internal; belum diekspos pada respons publik |

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
| API-001 | shippingAddress hanya terlihat pada contoh/PATCH, belum pada schema Order awal | Tambahkan atribut response bila menjadi kontrak yang diimplementasikan | Diterapkan pada DEC-012 (nullable), menunggu review |
| API-002 | transactionReference belum pada schema Payment awal | Tambahkan atribut respons opsional sesuai DEC-015 | Diterima dan dicatat pada kontrak; implementasi belum ada |
| API-003 | Response 200 tambahan Cart/Order/Payment hanya description pada YAML | PATCH Payment mengembalikan schema Payment | Payment diselesaikan pada kontrak; Cart/Order masih terbuka |
| API-004 | PATCH tidak menetapkan additionalProperties false | PATCH Payment menolak field asing, null, dan reference kosong | Payment diselesaikan pada kontrak melalui DEC-015; kebijakan global/layanan lain masih terbuka |
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

## DEC-012: Aturan checkout dan perubahan Order

- Status: diterapkan pada branch `feat/order-service`, 29 Oktober 2026; menunggu review tim.
- Dasar: laporan h. 14 (POST/GET Order), h. 34–35 (PATCH alamat), OPEN-003 B, OPEN-006, OPEN-012, dan API-001.
- POST `/orders` membaca Cart melalui `GET /api/v1/carts/{cartId}`. customerId request harus sama dengan customerId Cart; ini pencocokan referensi, bukan autentikasi (OPEN-010 tetap terbuka).
- Harga satuan item = subtotal Cart / quantity, yaitu harga tersimpan saat produk masuk Cart (OPEN-003 B). Kontrak Cart tidak diubah. Total Order = jumlah subtotal item.
- Setiap produk dibaca dari Product untuk nama snapshot dan pemeriksaan stok. Produk tidak ada/nonaktif atau stok kurang dari quantity menghasilkan 409. Pemeriksaan ini bukan reservasi stok (OPEN-004).
- Pemetaan error POST: Cart tidak ada 404; Cart kosong, customer berbeda, produk tidak tersedia, stok kurang 409; Cart/Product tidak dapat dihubungi atau respons tidak valid 503. Status 404/409/503 ditambahkan pada OpenAPI.
- Order baru berstatus PENDING dengan shippingAddress null. Snapshot item disimpan pada `order_items` dan tidak dihitung ulang dari katalog.
- PATCH hanya mengubah shippingAddress, hanya saat PENDING; status lain 409. Null eksplisit, string kosong, dan body tanpa shippingAddress ditolak 400. Atribut lain seperti total/status diabaikan, tidak mengubah data.
- Respons Order menambahkan shippingAddress (nullable). Respons PATCH 200 memakai schema Order.
- Lookup HTTP dilakukan di luar transaksi database; Order dan item disimpan dalam satu transaksi lokal.
- Belum diputuskan: pengosongan/penguncian Cart setelah checkout, idempotency create Order (OPEN-009), transisi PENDING ke CONFIRMED/CANCELLED, dan optimistic locking.

## DEC-013: Amount dan status awal Payment

- Status: diterima oleh pengguna pada 29 Oktober 2026; belum diimplementasikan atau diuji.
- Amount Payment berasal dari total akhir yang disediakan backend Order, bukan nominal bebas dari client atau harga katalog terkini.
- Istilah subtotal berarti jumlah harga item; total akhir berarti subtotal ditambah biaya yang berlaku dan dikurangi potongan jika fitur tersebut tersedia. Order bertanggung jawab menghitung komponen total. Payment mengambil nilai `Order.total` dan tidak menambahkan ongkir/biaya untuk kedua kalinya.
- Versi kontrak saat ini hanya memiliki `Order.total`; belum ada perincian ongkir atau biaya tambahan. Keputusan ini tidak menambahkan fitur biaya baru atau nominal biaya asumsi. Jika belum ada biaya/potongan, total sama dengan subtotal item.
- Payment baru selalu berstatus `PENDING`, termasuk method COD. Respons 201 serta pengisian transactionReference bukan bukti pembayaran selesai.
- Dampak API/domain: input create tetap orderId dan method; backend memperoleh amount melalui OrderLookup. Enum status akhir tetap mengikuti kontrak; sumber hasil simulasi ditetapkan melalui DEC-017.
- Tests yang diperlukan: create memakai total dari stub/Order, menginisialisasi PENDING, dan tidak menghitung biaya tambahan sendiri; nominal/status dalam input tidak dijadikan sumber nilai bisnis. Policy penolakan field asing mengikuti keputusan kontrak terpisah.
- Pengguna menerima BigDecimal pada Java dan NUMERIC pada PostgreSQL tanpa pembulatan diam-diam. Simpan amount dengan nilai desimal yang sama seperti total Order; jangan memakai float/double sebagai representasi perhitungan atau menetapkan skala yang memangkas nilai tanpa keputusan baru. Uji pemetaan dan serialisasi dengan nilai pecahan seperti 350000.125.
- Kelayakan status Order dan payment attempts ditetapkan melalui DEC-015; perubahan reference melalui DEC-016; simulator hasil pembayaran melalui DEC-017. Mata uang, batas nominal, masa berlaku nominal, dan integrasi COD nyata setelah penyerahan barang tetap terbuka di luar baseline awal.

## DEC-014: Idempotency pada create Payment

- Status: prinsip diterima oleh pengguna pada 29 Oktober 2026; dilengkapi pilihan I1-A (header wajib) dan I2-A (replay respons create pertama). Belum diimplementasikan atau diuji.
- Pengulangan request untuk operasi create Payment yang sama harus dapat dikenali agar retry tidak membuat Payment tambahan.
- POST `/api/v1/payments` mewajibkan header `Idempotency-Key` yang dibuat client untuk satu operasi; retry operasi yang sama menggunakan key yang sama. Request tanpa header ditolak 400. Format/panjang key, scope, dan detail canonicalization payload ditentukan saat desain teknis; keputusan payload berbeda dan hasil gagal ditetapkan di bawah.
- Masa retensi respons create yang berhasil dipilih 24 jam sejak hasil create pertama tersimpan, tanpa memperpanjang durasi setiap retry. Pengguna menyerahkan pemilihan durasi berdasarkan praktik nyata; 10 menit–1 jam dinilai terlalu pendek untuk pemulihan request yang tertunda. Ini durasi penyimpanan catatan idempotency, bukan expiry Payment/Order atau batas waktu membayar.
- Catatan hasil yang sudah selesai dan melewati retensi boleh dibersihkan; catatan operasi yang masih diproses tidak dihapus hanya karena waktu berlalu. Setelah jendela retensi, replay tidak lagi dijamin; jika catatan sudah dibersihkan, key yang digunakan ulang dapat dianggap operasi baru. Client harus membuat key baru untuk operasi bisnis baru, bukan mendaur ulang key lama. Penghapusan catatan tidak menghapus Payment/riwayat attempt, dan policy DEC-015 tetap berlaku.
- Dasar pemilihan: [Stripe API v1](https://docs.stripe.com/api/idempotent_requests) mengizinkan pembersihan key setelah minimal 24 jam dan menolak perubahan parameter pada key yang sama; [Adyen](https://docs.adyen.com/development-resources/api-idempotency/) mendokumentasikan validitas 7–14 hari. Durasi dan titik awal di atas adalah keputusan proyek, bukan standar universal atau integrasi provider.
- Pergantian metode setelah Payment FAILED merupakan attempt baru dengan key baru jika Order masih layak. Timeout request saja tidak membuktikan Payment FAILED. Pengguna menerima penolakan 409 Conflict untuk key sama dengan payload berbeda; gunakan code IDEMPOTENCY_KEY_REUSED. Bandingkan makna request orderId/method, bukan urutan properti atau whitespace JSON; detail canonicalization diselesaikan saat implementasi.
- Untuk operasi create yang berhasil, retry dengan key dan payload yang sama mengembalikan status HTTP dan body respons create pertama, tanpa membuat Payment baru. Jika respons awal berisi Payment 501 berstatus PENDING lalu resource telah berubah menjadi SUCCESS, replay tetap mengembalikan body awal PENDING.
- Pengguna menerima penyimpanan hanya respons create yang berhasil untuk replay pada versi pertama. Error sebelum perubahan tersimpan tidak menjadi replay final dan dapat dicoba ulang dengan key yang sama setelah penyebabnya diatasi. Payment dan hasil sukses idempotency perlu disimpan secara atomik dalam transaksi lokal. Jika commit sudah berhasil tetapi respons tidak sampai, retry harus menemukan hasil tersimpan. Hasil commit yang belum diketahui harus direkonsiliasi melalui key; jangan menganggap rollback hanya karena koneksi terputus. Detail klaim/recovery dirancang saat implementasi.
- Status Payment terkini dibaca lewat GET `/api/v1/payments/{paymentId}`. Dashboard dapat menyegarkan data Payment melalui endpoint tersebut; refresh Order saja tidak menjamin status Payment ikut diperbarui. Batas milestone tanpa perubahan status Order otomatis diterima melalui DEC-018.
- Pemeriksaan idempotency harus mempertimbangkan request bersamaan; pemeriksaan keberadaan data lalu insert tanpa perlindungan atomik belum membuktikan aman dari duplikasi.
- Pengguna memilih tolak sementara: request pertama yang berhasil mengklaim operasi secara atomik diproses. Duplikat dengan key/payload sama saat operasi masih berjalan mendapat 409 dengan code REQUEST_IN_PROGRESS; client menunggu jeda lalu retry menggunakan key yang sama. Setelah hasil create tersedia, duplikat memperoleh replay I2-A, bukan konflik in-progress.
- Model ini bukan antrean FIFO ketat: request yang ditolak tidak diantrekan, urutan retry tidak dijamin, dan pemenang ditentukan oleh klaim atomik, bukan timestamp kedatangan jaringan. Pengguna telah mengonfirmasi batas ini. Detail implementasi klaim, lock, dan pemulihan proses yang terputus masih perlu ditetapkan.
- REQUEST_IN_PROGRESS mengacu pada operasi HTTP create yang belum selesai, bukan status Payment PENDING. Payment dapat tetap PENDING setelah create selesai dan tetap memiliki respons yang dapat direplay. Respons konflik sementara tidak boleh menggantikan hasil create atau disimpan sebagai replay final operasi tersebut.
- Idempotency berbeda dari policy payment attempts: request memakai key baru dapat mewakili operasi baru dan tetap harus mematuhi aturan jumlah attempt/Payment aktif pada OPEN-008.
- Dampak: kontrak header wajib, replay, dan konflik sementara dicatat pada OpenAPI; diperlukan penyimpanan identitas request serta status/body hasil create agar replay tidak dibentuk dari state Payment terkini. Detail persistence, batas transaksi lokal, dan perlindungan concurrency belum diputuskan. Jangan menetapkan constraint satu Payment per Order hanya berdasarkan keputusan idempotency.
- Tests yang diperlukan setelah mekanisme dipilih: pengulangan operasi yang sama tidak menggandakan Payment; request bersamaan terkontrol; perbedaan payload ditangani menurut kontrak; kegagalan tidak meninggalkan Payment dan catatan idempotency yang tidak konsisten.
- Tests khusus I1-A/I2-A: header hilang menghasilkan 400; create sukses lalu status resource berubah tetap menghasilkan replay status/body awal; GET setelahnya mengembalikan state terkini dan jumlah Payment tetap satu.
- Tests concurrency tolak sementara: tahan operasi create pertama secara terkontrol, kirim duplikat, periksa 409 REQUEST_IN_PROGRESS tanpa Payment tambahan; setelah operasi pertama selesai, retry mendapat replay 201/body awal. Jangan memakai sleep arbitrer sebagai satu-satunya sinkronisasi test.
- Tests retensi: replay dalam 24 jam mengikuti I2-A; retry tidak memperpanjang TTL; cleanup hanya catatan hasil selesai yang melewati retensi; Payment tetap tersimpan; request setelah cleanup tetap mengikuti aturan attempt aktif/sudah SUCCESS. Gunakan clock terkontrol untuk menguji waktu tanpa menunggu 24 jam.
- Keputusan ini tidak otomatis menyelesaikan idempotency create Order atau berarti retry otomatis sudah aman diaktifkan.

## DEC-015: Kelayakan Order dan payment attempts

- Status: diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan 1A dan 3A; belum diimplementasikan atau diuji.
- Hanya Order berstatus `PENDING` yang boleh dibuatkan Payment baru. Order `CONFIRMED` dan `CANCELLED` tidak memenuhi syarat untuk create Payment baru. Status Order berbeda dari status Payment; aturan perubahan status Order setelah pembayaran belum diputuskan.
- Satu Order dapat memiliki beberapa Payment attempt dalam riwayat, tetapi maksimal satu attempt berstatus `PENDING` pada satu waktu. Jika masih ada PENDING, operasi create baru ditolak. Setelah attempt FAILED, pengguna dapat membuat attempt baru selama Order tetap memenuhi syarat. Setelah ada SUCCESS, attempt baru ditolak, sekalipun Order masih terlihat PENDING.
- Idempotent replay untuk operasi create yang sama bukan attempt baru dan harus diperlakukan sesuai DEC-014; mekanisme replay tetap perlu dirinci.
- Pertimbangan desain: memberi kesempatan mencoba lagi setelah kegagalan terkonfirmasi serta mencegah beberapa permintaan pembayaran aktif untuk satu Order. Policy ini tidak menjamin pembayaran tidak akan gagal.
- Dampak persistence: jangan memakai UNIQUE(order_id) tanpa kondisi karena akan melarang seluruh riwayat attempt. Pilih perlindungan atomik terhadap request bersamaan dan konflik dengan perubahan status; strategi teknis belum diputuskan.
- Dampak kontrak: respons konflik untuk attempt aktif/sudah berhasil dan Order tidak layak perlu didokumentasikan pada POST Payment. Status HTTP spesifik masih perlu diselaraskan dengan OpenAPI sebelum implementasi final.
- Tests yang diperlukan: create untuk Order PENDING; penolakan Order CONFIRMED/CANCELLED; maksimal satu PENDING meskipun key berbeda dan request bersamaan; create baru setelah FAILED; penolakan setelah SUCCESS; replay request lama tidak menambah attempt.
- Kebutuhan lanjutan yang disebut pengguna: pembatalan jika pesanan tidak dikerjakan dalam batas waktu dan pengembalian dana. Durasi, trigger, state, otorisasi, serta workflow refund belum diputuskan dan belum ditambahkan ke kontrak. Pembayaran yang sudah SUCCESS tidak diubah menjadi FAILED hanya untuk mewakili refund.

## DEC-016: Perubahan transactionReference selama PENDING

- Status: diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan 5A; belum diimplementasikan atau diuji.
- transactionReference boleh diisi dan diperbaiki selama Payment berstatus PENDING. Payment SUCCESS/FAILED menolak perubahan reference.
- Perubahan reference tidak mengubah amount atau status Payment dan bukan verifikasi pembayaran.
- Dampak kontrak: selaraskan schema respons Payment/PATCH dengan transactionReference dan petakan state yang melarang perubahan ke respons konflik sesuai kontrak PATCH.
- Validasi yang diterima pengguna: PATCH hanya menerima transactionReference; tolak null, string kosong/seluruhnya whitespace, body kosong, dan field asing dengan 400. Field amount/status tidak boleh diubah melalui endpoint ini. Batas panjang, normalisasi reference, dan koordinasi dengan verifikasi bersamaan dirancang saat implementasi.
- Respons Payment dapat memuat transactionReference setelah diisi; sebelum diisi, atribut opsional ini dapat tidak dikirim. Respons PATCH sukses menggunakan schema Payment yang sama. Kontrak respons diselaraskan tanpa mengizinkan null pada request PATCH.
- Tests yang diperlukan: pengisian awal dan koreksi saat PENDING; penolakan saat SUCCESS/FAILED; amount dan status tetap; kegagalan update tidak menyimpan perubahan parsial.

## DEC-017: Simulator pembayaran untuk automated tests dan demo lokal

- Status: diterima oleh pengguna pada 29 Oktober 2026 melalui pilihan 6B-2; belum diimplementasikan atau diuji.
- Keputusan: sediakan simulator backend dengan hasil yang dapat dipilih secara deterministik untuk menjalankan skenario pembayaran SUCCESS dan FAILED dalam automated tests serta demo lokal. Simulator tidak memindahkan uang atau membuktikan transaksi provider nyata.
- Demo lokal memiliki endpoint khusus development yang terpisah dari PATCH transactionReference. Simulator/endpoint hanya diaktifkan secara eksplisit pada lingkungan development/test dan tidak tersedia pada deployment produksi. Nama profil, flag aktivasi, route, schema input/output, dan pembatasan akses belum diputuskan.
- Alur yang dituju: POST Payment menghasilkan PENDING; pengisian reference tidak mengubah status; simulator memasok hasil; use case/domain memvalidasi perubahan state dan menyimpannya; GET Payment menampilkan hasil.
- Rancangan pembagian tanggung jawab: pemicu simulator meneruskan hasil ke pemrosesan hasil pembayaran pada application/domain, bukan mengubah tabel langsung dari controller. Detail port/adapter ditetapkan saat implementasi.
- Untuk COD, skenario SUCCESS simulator merepresentasikan konfirmasi uang diterima pada penyerahan barang. Membuat Payment COD atau sekadar mencatat pengiriman tidak otomatis membuat SUCCESS. Integrasi penerimaan uang nyata belum termasuk keputusan ini.
- Policy hasil yang diterima pengguna: PENDING dapat menjadi SUCCESS atau FAILED. Pengiriman ulang hasil final yang sama tidak mengubah data atau memicu efek tambahan. Permintaan mengubah SUCCESS menjadi FAILED atau FAILED menjadi SUCCESS ditolak 409. Setelah FAILED, percobaan bisnis baru membuat Payment baru sesuai DEC-015, bukan menghidupkan kembali Payment lama.
- Detail event/request hasil simulasi, identitas event/deduplikasi teknis, dan concurrency dengan perubahan reference diselesaikan saat implementasi. Keputusan idempotency create Payment (DEC-014) tidak otomatis menyediakan mekanisme idempotency pemrosesan hasil.
- Dampak kontrak: dokumentasikan endpoint simulator sebagai kontrak development/test terpisah dari API bisnis publik. Kontrak publik yang ada belum diperluas pada tahap pencatatan ini; jumlah 18 operasi pada 10 path tetap merujuk specs/openapi.yaml saat ini.
- Tests yang diperlukan: skenario PENDING ke SUCCESS dan PENDING ke FAILED; reference tetap tidak menjadi pemicu status final; amount tidak berubah; state terminal menolak perubahan reference; endpoint simulator tidak tersedia saat dinonaktifkan/di luar environment yang diizinkan; perilaku hasil berulang dan concurrency diuji setelah policy dirinci.
- Tidak menetapkan integrasi provider, perubahan status Order otomatis, deadline pengerjaan, refund, atau penambahan status pembayaran baru.

## DEC-018: Batas milestone Payment terisolasi

- Status: diterima oleh pengguna pada 29 Oktober 2026 bersama seluruh rekomendasi penyelesaian baseline; belum diimplementasikan atau diuji.
- Payment tidak otomatis mengubah status Order pada milestone pertama. Simulator mengubah status Payment melalui use case/domain Payment saja; tidak melakukan write langsung ke database Order.
- Dashboard membaca status Order dan Payment dari sumber masing-masing. Agregasi dashboard serta sinkronisasi status antarlayanan menjadi pekerjaan integrasi berikutnya.
- Implementasi awal memakai stub Order untuk pengujian terisolasi; integrasi Order nyata diverifikasi ketika layanannya tersedia.
- Detail teknis nama class/tabel, constraint/index, klaim atomik, recovery proses terputus, route/profil/aktivasi/pembatasan akses simulator, serta fixture dan stub dirancang saat implementasi dan harus dijelaskan serta diuji. Ini bukan persetujuan untuk menambah fitur bisnis di luar baseline.
- Refund, provider nyata, biaya ongkir baru, deadline pengerjaan, dan deployment Kubernetes tetap tahap berikutnya. Keputusan baseline cukup untuk mulai domain, port, use case, dan tests terkait; detail teknis yang tersisa bukan alasan menunda seluruh implementasi.

## DEC-019: Desain teknis tahap 0 Payment

- Status: rancangan teknis ditetapkan dalam scope tahap 0 yang diminta pengguna; belum diimplementasikan atau diuji runtime. Rincian normatif untuk tahap berikutnya ada pada [payment-service-technical-design.md](payment-service-technical-design.md).
- Scope key adalah `(CREATE_PAYMENT_V1, key)` per payment_db, dengan nilai case-sensitive 1–128 karakter ASCII sesuai pola pada OpenAPI. Payload dibandingkan berdasarkan orderId Long/method yang tervalidasi, bukan teks JSON mentah; create menolak field asing/properti duplikat. Tidak memperkenalkan autentikasi customer.
- Klaim PROCESSING memakai lease default 30 detik dan owner token/fencing; COMPLETED memuat snapshot status/body create. Transaksi klaim dan transaksi finalisasi terpisah; lookup Order berlangsung tanpa transaksi SQL. Payment dan hasil replay commit bersama. Lock budget awal 250 ms; nilai konfigurasi belum menjadi SLA terukur.
- Guard lokal per Order dan partial unique index untuk status PENDING/SUCCESS melindungi policy attempt. Recovery wajib memeriksa token dan state; expired lease tidak boleh mengalahkan transaksi finalisasi yang masih memegang lock. Commit yang belum diketahui direkonsiliasi, bukan dianggap rollback.
- Money memakai NUMERIC tanpa fixed scale dan BigDecimal; reference dipertahankan persis tanpa trim atau batas panjang bisnis baru. Payment memakai version Long dan ID sequence numerik. Detail skema dan port ada pada desain, bukan migrasi/class yang telah dibuat.
- Kontrak publik Payment dilengkapi 400/404/409/500/502/503/504 sesuai operasi. Simulator memakai kontrak terpisah `specs/payment-simulator.openapi.yaml`, POST `/internal/dev/payments/{paymentId}/result`, hasil SUCCESS/FAILED, dan 200 Payment/no-op terminal identik.
- Simulator membutuhkan profil local/test, flag enable eksplisit, listener loopback, serta secret environment dengan header X-Simulator-Token. Profil prod menonaktifkan simulator sekalipun flag/profil lain aktif. Startup memvalidasi token minimal 32 karakter dan binding. Helper/config deployment belum diubah.
- Rincian yang sebelumnya disebut akan ditetapkan saat implementasi pada DEC-014–DEC-017 mengikuti dokumen desain tahap 0 ini. Implementasi selanjutnya tetap harus membuktikan atomicity, recovery, batas akses, dan kompatibilitas mapping melalui tests.

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
