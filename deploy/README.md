# Deployment lokal otomatis di Ubuntu WSL

Gunakan `bash scripts/deploy-local.sh` untuk menjalankan urutan deployment yang
sebelumnya dikerjakan satu per satu. Manifest Kubernetes dan Helm adalah IaC
deklaratif; script Bash mengatur urutan validasi, build, apply, dan pemeriksaannya.
Targetnya Minikube lokal, bukan production. Script tidak memasang tools host.

## 1. Prasyarat dan versi

Jalankan dari distro Ubuntu WSL tempat cluster dibuat, bukan PowerShell Windows.
Contoh checkout yang digunakan pada sesi pendampingan:

```bash
cd ~/ABL-Project
```

| Komponen | Versi/kondisi yang sudah dilaporkan berjalan |
|---|---|
| OS | Ubuntu 24.04 pada WSL2, amd64 |
| Bash | Bash 4 atau lebih baru untuk script |
| Docker | Linux containers; engine aktif dan dapat diakses dari Ubuntu; nomor versi belum dicatat |
| Minikube | 1.39.0, Docker driver |
| Kubernetes | 1.37.0; dipilih Minikube saat cluster sesi dibuat |
| Container runtime node | containerd 2.3.4 |
| Helm | 3.22.0; command script ditujukan untuk Helm 3 |
| Istio CLI/control plane | 1.31.4; periksa kompatibilitas jika memilih versi lain |
| Java | Build dan runtime Java 17 di Dockerfile; host tidak harus memasang Java |
| Tool pendukung | curl, sed, grep, head, date, mktemp; openssl untuk Secret baru otomatis; Git jika memakai file kredensial lokal |

Versi di atas adalah snapshot hasil command manual pengguna pada 16 November
2026. Script otomatis diuji dengan command doubles; seluruh alur script belum
dijalankan pada cluster WSL tersebut oleh editor. Ini bukan pin versi untuk
komputer baru: `init` memakai binary Minikube/istioctl yang tersedia di PATH dan
versi Kubernetes default Minikube jika profile baru dibuat. Periksa versi sebelum
inisialisasi. Untuk profile yang sudah aktif, script memakai cluster tersebut.

```bash
docker version
docker info --format '{{.OSType}}'
minikube version
helm version --short
istioctl version --remote=false
```

Target Docker: Client dan Server tersedia, OSType `linux`. Jika memakai Docker
Desktop, aktifkan WSL Integration untuk Ubuntu terkait. Jika binary Istio dipasang
di `~/tools/istio-1.31.4/bin`, aktifkan PATH pada terminal yang digunakan:

```bash
export PATH="$HOME/tools/istio-1.31.4/bin:$PATH"
```

Script memakai identitas tetap agar sesuai dengan manifest yang tersedia:

- Profile/context: `ecommerce-abl`.
- Namespace aplikasi/database: `ecommerce`.
- Release Helm: `abl`.
- Namespace komponen Istio/Gateway: `istio-system`.

Siapkan akses internet untuk registry Docker, repository Maven, dan image Istio.
Default awal `init` adalah 2 CPU/3000 MB jika cluster belum Running. Ini pernah
menjalankan Product beserta database dan Istio, belum membuktikan cukup untuk
empat aplikasi/database/sidecar. Untuk cluster baru, sesuaikan kapasitas, contoh
jika komputer memiliki resource yang cukup:

```bash
LOCAL_CPUS=4 LOCAL_MEMORY=6144mb bash scripts/deploy-local.sh init
```

Angka 4 CPU/6144 MB adalah contoh alokasi, bukan hasil pengukuran kebutuhan.
Environment tersebut diteruskan ke `minikube start` ketika cluster tidak Running;
tidak mengubah alokasi cluster yang sedang Running. Untuk cluster lama yang
dihentikan, perhatikan konfigurasi profile sebelum menjalankan init kembali.

## 2. Persiapan Secret database

Secret adalah resource Kubernetes `apiVersion: v1`, `kind: Secret`, type `Opaque`.
Tidak ada nomor versi aplikasi khusus untuk Secret. Chart dan PostgreSQL
membutuhkan dua key bernama persis `username` dan `password`.

| Service | Nama Secret | Username default | Database |
|---|---|---|---|
| Product | `product-db-credentials` | `product_user` | `product_db` |
| Cart | `cart-db-credentials` | `cart_user` | `cart_db` |
| Order | `order-db-credentials` | `order_user` | `order_db` |
| Payment | `payment-db-credentials` | `payment_user` | `payment_db` |

Semua Secret harus berada di namespace `ecommerce`. Script menangani tiga kondisi:

1. **Secret sudah ada:** dipakai kembali setelah memastikan kedua key tidak kosong;
   tidak dirotasi atau diganti dari file lokal.
2. **Secret belum ada dan PVC belum ada:** jika `.secrets/<service>-db.env`
   tersedia, script memakainya. Jika tidak, script membuat password acak melalui
   openssl dan menyimpannya ke Secret Kubernetes melalui file sementara privat.
   File sementara dihapus saat script berakhir; nilai password tidak dicetak.
3. **Secret hilang tetapi PVC database masih ada:** script berhenti. Pulihkan
   kredensial yang sesuai dengan database lama, jangan membuat password acak baru.

Untuk Secret Product yang sudah digunakan pada sesi sebelumnya, tidak perlu
menyiapkan ulang apa pun. Periksa metadata/key tanpa mencetak nilainya:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce describe secret product-db-credentials
```

Jika ingin menentukan kredensial sendiri untuk database baru, siapkan sebelum `up`:

```bash
mkdir -p .secrets
chmod 700 .secrets
nano .secrets/cart-db.env
```

Contoh format, ganti placeholder password:

```dotenv
username=cart_user
password=GANTI_DENGAN_PASSWORD_LOKAL_PILIHANMU
```

Simpan UTF-8 tanpa BOM, tanpa kutip pelindung Bash. Nilai dibaca oleh parser
`kubectl --from-env-file`, bukan dieksekusi sebagai shell script. Kemudian:

```bash
chmod 600 .secrets/cart-db.env
git check-ignore .secrets/cart-db.env
```

Script menolak file kredensial yang belum di-ignore Git. Jangan commit file atau
membagikan nilai Secret. Perubahan Secret saja tidak mengubah password role
PostgreSQL pada PVC yang sudah diinisialisasi. Kredensial otomatis hanya tersimpan
di Secret cluster; rencanakan pemulihan Secret bila ingin mempertahankan database
di luar masa hidup cluster. Storage lokal dan Secret cluster bukan sistem backup.

## 3. Jalankan deployment

Lihat bantuan tanpa mengakses cluster:

```bash
bash scripts/deploy-local.sh --help
```

### Komputer atau cluster baru

```bash
bash scripts/deploy-local.sh init
bash scripts/deploy-local.sh up product
```

`init` memeriksa Docker, menjalankan Minikube bila belum aktif, menunggu node Ready,
menjalankan Istio precheck, dan memasang Istio jika deployment istiod belum ada.
Ia memakai `istio/install.yaml` untuk profil default dan menerapkan
`kubernetes/namespace.yaml` untuk namespace serta label injeksi sidecar.
Instalasi Istio yang sudah ada tidak otomatis di-upgrade; versi aktual ditampilkan.
Jika komponen instalasi lama tidak lengkap, init berhenti agar bisa diperiksa.

### Cluster sekarang yang sudah menjalankan Product

Tidak perlu mengulang init jika cluster dan Istio masih aktif. Untuk membaca status:

```bash
bash scripts/deploy-local.sh status
```

Untuk melanjutkan ke Cart:

```bash
bash scripts/deploy-local.sh up cart
```

Setiap `up` menjalankan tahap **kumulatif**, bukan satu service terisolasi:

| Command | Aplikasi/database yang disiapkan |
|---|---|
| `up product` | Product |
| `up cart` | Product dan Cart |
| `up order` | Product, Cart, dan Order |
| `up payment` | Product, Cart, Order, dan Payment |

Tidak perlu menjalankan empat command secara berurutan jika ingin langsung
menyiapkan semuanya: `up payment` sudah mengurus seluruh prasyarat service.
Untuk pembelajaran/debugging, tahap per service tetap memudahkan pelacakan masalah.
Script menolak stage lebih awal jika akan menonaktifkan aplikasi release abl yang
sudah terpasang. Ini perlindungan untuk deployment yang masih ada di cluster,
bukan riwayat stage setelah cluster/resource dihapus manual.

Urutan otomatis `up`:

1. Periksa cluster, komponen Istio, dan risiko penurunan stage.
2. Ambil values release sebelumnya agar override image/resource tetap dipakai.
3. Build seluruh aplikasi pada stage dengan tag unik, lalu load image ke Minikube.
4. Helm lint, render, dan server dry-run dengan values yang sama seperti install.
5. Pastikan namespace, gunakan/buat Secret, apply database, tunggu database Ready.
6. Helm upgrade/install dan tunggu aplikasi Ready.
7. Apply Gateway serta route untuk seluruh aplikasi pada stage, lalu Istio analyze.

Jika ada kegagalan, script berhenti. Langkah yang sebelumnya berhasil bisa sudah
mengubah cluster; tidak ada transaksi tunggal atau rollback otomatis seluruh alur.
Data/PVC tidak dihapus. Periksa error, perbaiki penyebabnya, lalu ulangi stage.

### Jika kode service berubah

Cukup ulangi satu command untuk stage tertinggi yang sedang dipakai:

```bash
# Sekarang hanya Product yang aktif:
bash scripts/deploy-local.sh up product

# Jika keempat service sudah aktif:
bash scripts/deploy-local.sh up payment
```

Script membangun ulang seluruh aplikasi yang termasuk stage dengan tag baru,
sehingga Helm memicu rollout image baru. Secret dan PVC dipakai kembali. Tidak
perlu mengulangi command database atau image load satu per satu. Ini bukan hot
reload: perubahan kode harus sudah ada di checkout WSL yang menjadi build context.

Perubahan field payload/response umumnya cukup dengan rebuild/redeploy, tetapi
script tidak memperbarui kontrak atau konfigurasi secara otomatis. Jika berubah
path API, port, environment, dependency, atau schema database, perbarui manifest,
konfigurasi, atau migrasi Flyway terkait sebelum menjalankan ulang. Jangan mengubah
migrasi lama yang sudah diterapkan; sediakan migrasi baru.

Untuk menerapkan ulang konfigurasi tanpa membangun image:

```bash
bash scripts/deploy-local.sh up product --skip-build
```

`--skip-build` hanya tepat jika image yang diperlukan sudah ada di Minikube.
Pada release lama, override tag sebelumnya dipertahankan. Pada aplikasi yang baru
diaktifkan, tag mengikuti values chart jika belum ada override. Flag ini tidak
memuat perubahan source code ke image. Dockerfile build juga memakai `-DskipTests`;
jalankan tests aplikasi secara terpisah.

## 4. Akses gateway dan pemeriksaan HTTP

Untuk akses dari browser/curl, jalankan di terminal tersendiri:

```bash
bash scripts/deploy-local.sh gateway
```

Biarkan terminal berjalan. Akses dari terminal lain:

```bash
curl -i http://127.0.0.1:18080/api/v1/products
```

Jika terminal port-forward manual lama sudah memakai 18080, gunakan terminal
tersebut atau hentikan dengan Ctrl+C sebelum menjalankan `gateway`.

Pemeriksaan otomatis menggunakan port-forward sementara dengan port lokal yang
dipilih otomatis, sehingga tidak perlu membuka gateway manual:

```bash
bash scripts/deploy-local.sh check
```

`check` memeriksa GET daftar Product melalui ingress, tidak membuat data uji.
Port-forward sementara dihentikan saat script berakhir. Ini smoke test HTTP
Product; belum menguji POST/PUT/PATCH/DELETE, alur Cart/Order/Payment, idempotency,
atau persistensi setelah restart. Gunakan
[panduan tim](../docs/panduan-deployment-tim.md) untuk pengujian bisnis lengkap.

### Payment v1 dan v2 melalui gateway yang sama

`istio/payment-route.yaml` meneruskan exact `/api/v1/payments` dan
`/api/v2/payments`, serta prefix `/api/v1/payments/` dan `/api/v2/payments/`,
ke `abl-payment-service.ecommerce.svc.cluster.local:8080` tanpa rewrite path.
Kedua versi memakai aplikasi, Service, database, dan Secret yang sama.

Pastikan checkout WSL sudah berisi adapter HTTP v2 dan manifest terbaru. Jika
image aplikasi belum memuat v2, bangun dan deploy ulang seluruh stage Payment:

```bash
bash scripts/deploy-local.sh up payment
```

Script sudah melakukan server dry-run dan apply manifest route Payment setelah
rollout aplikasi. Jika image v2 sudah terpasang dan hanya route yang berubah,
cukup terapkan manifest tersebut dari WSL tanpa rebuild:

```bash
# Simpan route cluster sebelum apply untuk rollback (jika sudah ada).
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get virtualservice payment-route -o yaml > /tmp/payment-route-before.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f deploy/istio/payment-route.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f deploy/istio/payment-route.yaml
istioctl analyze --context=ecommerce-abl -n ecommerce
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get virtualservice payment-route -o yaml
```

Jalankan `bash scripts/deploy-local.sh gateway` di terminal terpisah, lalu:

```bash
# Ganti dengan ID Order dan Payment dari database cluster lokal.
ORDER_ID=1
PAYMENT_ID=1
curl -i "http://127.0.0.1:18080/api/v1/payments/${PAYMENT_ID}"
curl -i "http://127.0.0.1:18080/api/v2/payments/${PAYMENT_ID}"
curl -i "http://127.0.0.1:18080/api/v2/payments?orderId=${ORDER_ID}"
```

Target detail: 200 untuk Payment yang ada. Target list: 200, ID menaik,
atau `[]` bila Order belum memiliki Payment. GET list hanya tersedia pada v2.
Status Pod Ready atau validasi YAML saja belum membuktikan akses gateway.

Untuk rollback route saja, gunakan snapshot route cluster yang disimpan sebelum
apply di atas. Pastikan file backup berisi VirtualService sebelum memulihkannya:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f /tmp/payment-route-before.yaml
istioctl analyze --context=ecommerce-abl -n ecommerce
```

Jika route sebelumnya belum ada, command backup gagal; jangan gunakan file kosong
untuk rollback. Rollback Helm tidak memulihkan VirtualService yang dikelola
terpisah ini.

## 5. Jika gagal dan rollback

```bash
bash scripts/deploy-local.sh status
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get events --sort-by=.lastTimestamp
helm history abl --kube-context ecommerce-abl -n ecommerce
```

Setelah memilih revisi yang sudah diketahui baik:

```bash
helm rollback abl NOMOR_REVISI --kube-context ecommerce-abl -n ecommerce --wait --timeout 5m
```

Rollback Helm hanya mengelola resource chart aplikasi. Database, Secret, namespace,
Istio, dan route dikelola terpisah; migrasi Flyway serta data tidak dibatalkan.
Script tidak menyediakan delete/reset cluster atau PVC.

## 6. Validasi script dan batas lingkungan lokal

Pemeriksaan syntax dan regresi orkestrasi dapat dijalankan tanpa cluster:

```bash
bash -n scripts/deploy-local.sh
bash tests/integration/deploy-local-test.sh
```

Tes memakai command doubles untuk menguji perlindungan stage, penanganan Secret,
values lama, dan penghentian saat gagal. Ini bukan integration test Docker atau
Kubernetes nyata. Nama folder tests/integration mengikuti lokasi test helper;
hasilnya tidak membuktikan runtime cluster.

Single replica, PVC lokal, HTTP wildcard, serta port-forward merupakan konfigurasi
development. Keamanan mesh, ServiceAccount aplikasi khusus, enforcement
NetworkPolicy, backup, serta kebutuhan production belum diotomatisasi oleh script.

Referensi: [Helm upgrade](https://helm.sh/docs/v3/helm/helm_upgrade/),
[Kubernetes Secret](https://kubernetes.io/docs/concepts/configuration/secret/), dan
[Minikube image](https://minikube.sigs.k8s.io/docs/commands/image/).
