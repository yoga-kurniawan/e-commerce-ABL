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
Default awal `init` adalah 4 CPU/5120 MB jika cluster belum Running.
Untuk pengguna Windows WSL2, disarankan mengonfigurasi `%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=8GB
processors=4
```

Alokasi CPU dan memori Minikube dapat disesuaikan melalui environment variable:

```bash
LOCAL_CPUS=4 LOCAL_MEMORY=5120mb bash scripts/deploy-local.sh init
```

Jika membutuhkan NetworkPolicy enforcement di Minikube, CNI kustom seperti Calico dapat ditentukan melalui `LOCAL_CNI`:

```bash
LOCAL_CNI=calico bash scripts/deploy-local.sh init
```

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
3. Build aplikasi pada stage dengan tag unik (atau hanya satu service jika memakai `--only`), lalu load image ke Minikube.
4. Helm lint, render, dan server dry-run dengan `values-minikube.yaml` serta file stage yang sesuai.
5. Pastikan namespace, gunakan/buat Secret, apply database dengan resource optimal, tunggu database Ready.
6. Helm upgrade/install dan tunggu aplikasi Ready.
7. Apply Gateway `deploy/istio/gateway.yaml`, bersihkan legacy unmanaged route jika ada, lalu Istio analyze.

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

Untuk mempercepat iterasi lokal saat hanya satu service yang diubah, gunakan flag `--only=<service>`:

```bash
# Membangun dan memuat HANYA cart-service pada stage payment:
bash scripts/deploy-local.sh up payment --only=cart
```

Flag `--only` hanya membangun dan memuat image untuk service yang ditentukan, sedangkan service lain dalam stage menggunakan image yang sudah ada di cluster.

Script membangun ulang seluruh aplikasi yang termasuk stage dengan tag baru (kecuali memakai `--only`),
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

`check` secara dinamis memeriksa endpoint GET lintas semua service yang sedang aktif pada release `abl`:
- `/api/v1/products` dan `/api/v2/products` (selalu diperiksa).
- `/api/v2/orders?customerId=1` (jika `order-service` aktif).
- `/api/v2/payments?orderId=1` (jika `payment-service` aktif).

Port-forward sementara dihentikan saat script berakhir. Ini smoke test HTTP ingress gateway; belum menguji POST/PUT/PATCH/DELETE atau alur transaksi multi-service. Gunakan
[panduan tim](../docs/guides/panduan-deployment-tim.md) untuk pengujian bisnis lengkap.

### Port Management Actuator Terisolasi (Port 9090)

Pada Kubernetes, port Actuator (`/actuator/health`, `/actuator/prometheus`) dipisahkan ke management port `9090` terisolasi, sedangkan port bisnis aplikasi adalah `8080`. Gateway Ingress `18080` dan Service Kubernetes bisnis tidak mengekspos endpoint `/actuator/*`.

Untuk memeriksa readiness probe dan metrik Prometheus secara langsung:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce port-forward deployment/abl-product-service 19090:9090
```

Lalu uji dari terminal lain:

```bash
curl -i http://127.0.0.1:19090/actuator/health/readiness
curl -i http://127.0.0.1:19090/actuator/prometheus
```

### Kebijakan Mesh & Route Istio di Helm

Seluruh kebijakan Istio (`VirtualService`, `DestinationRule`, `PeerAuthentication STRICT`, dan `AuthorizationPolicy`) kini dikelola secara deklaratif oleh template Helm (`deploy/helm/ecommerce/templates/istio-*.yaml`) dengan `-f deploy/helm/ecommerce/values-minikube.yaml`.
Ingress route otomatis meneruskan trafik `/api/v1/*` dan `/api/v2/*` ke service yang sesuai tanpa membutuhkan file rute statis terpisah. Safe retries otomatis diterapkan untuk metode idempoten (`GET`, `HEAD`) dan dinonaktifkan (`attempts: 0`) untuk metode mutasi (`POST`, `PUT`, `PATCH`, `DELETE`).

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
