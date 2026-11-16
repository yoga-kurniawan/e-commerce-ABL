# Panduan deployment Minikube dan Istio untuk tim

Untuk menjalankan urutan ini secara otomatis, lihat
[panduan script deployment lokal](../deploy/README.md). Script menggunakan
manifest dan stage Helm yang sama; command manual di bawah tetap berguna untuk
pembelajaran, pengujian bisnis, dan troubleshooting.

Panduan ini menjalankan aplikasi di Kubernetes lokal dengan Istio mode sidecar.
Product dikerjakan terlebih dahulu, kemudian anggota tim meneruskan Cart,
Order, dan Payment. Perintah ditujukan untuk terminal Bash atau Zsh di Linux,
termasuk Ubuntu WSL, dijalankan dari root checkout repository, misalnya
`cd ~/ABL-Project`. Gunakan distro, Docker engine, dan checkout yang konsisten;
profile/tools Windows dapat berbeda dari profile/tools WSL.

Panduan ini menjadi acuan command operasional. Lihat
[panduan build/chart](deployment.md) untuk parameter Helm dan
[README database](../deploy/kubernetes/README.md) untuk detail PostgreSQL/PVC.
Snapshot 16 November 2026 berdasarkan output terminal WSL yang dibagikan pengguna
menunjukkan Product Ready dengan sidecar, database/PVC siap, serta dua migrasi
Flyway berhasil hingga versi v2. HTTP readiness mengembalikan 200/UP; POST dan
GET daftar/detail berhasil melalui akses langsung (18081) dan ingress Istio
(18080). Ini bukti operasi yang diuji pada satu cluster, bukan pemeriksaan
langsung editor dokumen atau jaminan kondisi terbaru semua komputer.
Runtime Cart/Order/Payment, operasi API lainnya, persistensi setelah restart,
serta kebijakan keamanan belum terverifikasi. Jangan mengulang persiapan yang
sudah berhasil hanya karena membaca panduan dari awal.

## 1. Bedakan lingkungan lokal aplikasi dan cluster

| Komponen | Pengujian Spring Boot lokal | Deployment pada panduan ini |
|---|---|---|
| Aplikasi | Satu proses terminal per service | Pod yang dikelola Deployment |
| Database | Docker Compose, port host 5433–5436 | StatefulSet dan PVC, port internal 5432 |
| Alamat antarservice | localhost:8081–8084 | DNS Service Kubernetes, port 8080 |
| Kredensial | `.env` lokal | Secret Kubernetes |
| Akses awal dari laptop | Port aplikasi langsung | Port-forward untuk uji langsung |
| Akses melalui Istio ingress | Belum digunakan | Gateway bersama dan route per service di `deploy/istio/` |

Menutup terminal `kubectl port-forward` hanya menutup akses dari laptop;
Pod aplikasi tetap berjalan. Docker engine dan Minikube harus tetap aktif.
Data Compose tidak otomatis disalin ke database cluster.

**Cluster Minikube bukan milik bersama lewat Git.** Jika teman memakai laptop
sendiri, ia harus menyiapkan cluster, Secret, database, dan semua service
pendahulu pada laptop tersebut. Pull repo hanya mengambil kode dan konfigurasi.
Jangan mengirim file `.secrets/` melalui Git.

## 2. Pembagian pekerjaan dan prasyarat service

| Tahap | Penanggung jawab | Dependency aplikasi yang harus siap | File tahap Helm |
|---|---|---|---|
| Product | Pengembang Product | Database Product | `stages/product.yaml` |
| Cart | Pengembang Cart | Product dan database Cart | `stages/cart.yaml` |
| Order | Pengembang Order | Cart, Product, dan database Order | `stages/order.yaml` |
| Payment | Pengembang Payment | Order dan database Payment | `stages/payment.yaml` |

Semua file tahap ada di `deploy/helm/ecommerce/`. File tahap bersifat kumulatif:
Cart mengaktifkan Product dan Cart; Order mempertahankan keduanya; Payment
mengaktifkan keempat service. Pakai release **`abl`**, namespace **`ecommerce`**,
dan context **`ecommerce-abl`** secara konsisten. Meng-upgrade release yang sama
dengan konfigurasi yang menonaktifkan service sebelumnya dapat menghapus
Deployment dan Service aplikasi tersebut.

Destination pada file `deploy/istio/*-route.yaml` menetapkan release `abl` dan
namespace `ecommerce` secara tetap, sementara Helm membentuk nama Service dari
release. Jika mengganti nama tersebut, sesuaikan semua referensi route.

Panduan dan manifest untuk Cart sampai Payment disiapkan agar dapat dijalankan
oleh pemilik service. Validasi YAML atau dry-run bukan bukti deployment runtime
berhasil; setiap pemilik melengkapi hasil dan screenshot setelah menjalankannya.

## 3. Persiapan cluster pada komputer masing-masing

Pastikan Docker engine aktif dan tersedia `minikube`, `istioctl`, serta Helm.
Jika memakai Docker Desktop, aktifkan integrasi WSL untuk distro Ubuntu terkait.
Periksa sebelum membuat profil baru:

```bash
docker info
docker context ls
minikube profile list
minikube status -p ecommerce-abl
istioctl version --remote=false
helm version --short
```

Jika profil `ecommerce-abl` belum ada:

```bash
minikube start -p ecommerce-abl --driver=docker --cpus=2 --memory=3000mb
```

Pada snapshot WSL 16 November 2026, alokasi 2 CPU/3000 MB berhasil menjalankan
cluster, Istio, aplikasi Product, dan database Product. Ini bukan jaminan cukup
untuk empat JVM, empat PostgreSQL, dan sidecar.
Sesuaikan RAM Docker Desktop serta alokasi Minikube dengan kapasitas laptop
sebelum melanjutkan seluruh service. Periksa `describe nodes` dan event Pod
jika terdapat `Insufficient memory` atau `Insufficient cpu`; jangan mengatasi
kekurangan RAM dengan menghapus readiness probe. Request aplikasi dan database
untuk empat service berjumlah 1536 MiB, belum termasuk Istio dan komponen sistem.
Periksa kapasitas sebelum menambah service:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl describe node ecommerce-abl
```

```bash
minikube status -p ecommerce-abl
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl get nodes
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl get pods -A
```

Target: host/kubelet/apiserver Running, node Ready, dan Pod sistem siap.
Gunakan kubectl bawaan Minikube agar versi client mengikuti cluster yang dituju.
Snapshot yang dilaporkan memakai Minikube 1.39.0, Kubernetes 1.37.0, containerd
2.3.4, Istio 1.31.4, dan Helm 3.22.0; ini catatan lingkungan, bukan jaminan
kompatibilitas untuk seluruh kombinasi versi berikutnya.

Jika Istio belum terpasang, periksa kompatibilitas versi CLI dengan Kubernetes
pada [matriks dukungan Istio](https://istio.io/latest/docs/releases/supported-releases/), lalu:

```bash
istioctl x precheck --context=ecommerce-abl
istioctl install --context=ecommerce-abl --set profile=default -y
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl get pods -n istio-system
```

Target: `istiod` dan `istio-ingressgateway` Running dan Ready. Pada snapshot,
Istio 1.31.4 telah terpasang; tidak perlu memasang ulang untuk melanjutkan.
Pastikan `istioctl version --remote=false` menunjukkan binary yang dimaksud,
terutama jika PATH diperbarui hanya pada terminal sebelumnya.
Buat atau pastikan namespace tersedia, lalu periksa label:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl create namespace ecommerce --dry-run=client -o yaml | minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f -
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl label namespace ecommerce istio-injection=enabled --overwrite
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl get namespace ecommerce --show-labels
```

Label harus ada sebelum Pod aplikasi dibuat. Pod database sengaja tidak diberi
sidecar melalui label pada template Pod di manifest. Pod aplikasi ditargetkan mempunyai
container aplikasi dan `istio-proxy`. Label baru tidak menambahkan sidecar ke
Pod yang sudah berjalan. Database tetap berada di namespace `ecommerce` yang
sama; injeksi dinonaktifkan per template Pod database.

## 4. Langkah yang diulang oleh pemilik setiap service

Pilih service yang menjadi tanggung jawab. Contoh berikut untuk Cart:

```bash
SERVICE=cart
```

Nilai yang tersedia: `product`, `cart`, `order`, `payment`.
Jalankan semua perintah yang memakai `$SERVICE` pada terminal yang sama.

### 4.1 Buat Secret database

```bash
mkdir -p .secrets
chmod 700 .secrets
nano ".secrets/${SERVICE}-db.env"
```

Isi sesuai service. Ganti placeholder password dengan password lokal pilihan
pemilik service; gunakan password berbeda untuk masing-masing database.

| Service | Isi username | Nama Secret | Database |
|---|---|---|---|
| product | `product_user` | `product-db-credentials` | `product_db` |
| cart | `cart_user` | `cart-db-credentials` | `cart_db` |
| order | `order_user` | `order-db-credentials` | `order_db` |
| payment | `payment_user` | `payment-db-credentials` | `payment_db` |

Contoh format file Cart, tanpa kutip pelindung Bash:

```dotenv
username=cart_user
password=GANTI_DENGAN_PASSWORD_CART
```

Setelah file disimpan:

```bash
chmod 600 ".secrets/${SERVICE}-db.env"
git check-ignore ".secrets/${SERVICE}-db.env"
```

Pastikan `git check-ignore` menampilkan path file; jika tidak, periksa aturan
ignore sebelum melanjutkan. Pada checkout `/mnt/c` atau `/mnt/d`, efektivitas
chmod bergantung pada konfigurasi mount WSL. Jangan membagikan isi file atau
nilai Secret sebagai bukti. Setelah pemeriksaan ignore berhasil, buat Secret:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce create secret generic "${SERVICE}-db-credentials" --from-env-file=".secrets/${SERVICE}-db.env"
```

Target: `secret/<service>-db-credentials created`. Jika sudah ada, jangan
membuat ulang atau mengganti password tanpa memeriksa konfigurasi lama.
Secret dipakai saat inisialisasi PostgreSQL; perubahan Secret tidak otomatis
mengganti password role pada PVC yang sudah berisi database.

### 4.2 Jalankan database di Kubernetes

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f "deploy/kubernetes/${SERVICE}-db.yaml"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f "deploy/kubernetes/${SERVICE}-db.yaml"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce rollout status "statefulset/${SERVICE}-db" --timeout=180s
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get pods,pvc,services
```

Target: `<service>-db-0` 1/1 Running, PVC `data-<service>-db-0` Bound, Service
`<service>-db` pada port 5432. Setiap database punya PVC 1 GiB, tanpa port host.
NetworkPolicy database disediakan, tetapi enforcement memerlukan CNI yang
mendukung NetworkPolicy; kindnet bawaan tidak menjamin enforcement tersebut.

### 4.3 Build dan muat image

```bash
IMAGE_TAG=0.1.0-local
if [ "$SERVICE" = product ]; then IMAGE_TAG=0.2.0-local; fi
docker build -f "services/${SERVICE}-service/Dockerfile" -t "ecommerce/${SERVICE}-service:${IMAGE_TAG}" .
minikube -p ecommerce-abl image load "ecommerce/${SERVICE}-service:${IMAGE_TAG}"
minikube -p ecommerce-abl image ls
```

Titik pada akhir build adalah root build context dan wajib ikut. Dockerfile
mengompilasi serta mengemas JAR; `-DskipTests` di Dockerfile tidak menjalankan
test. Muat image ke profil yang sama dengan target deployment. Image yang ada
di Docker Desktop tidak otomatis ada di runtime containerd Minikube.

### 4.4 Validasi dan deploy aplikasi

Pastikan seluruh tahap pendahulu sudah dijalankan. Di laptop baru, kerjakan
Product lebih dahulu sebelum memilih Cart, dan seterusnya.

```bash
helm lint deploy/helm/ecommerce -f "deploy/helm/ecommerce/stages/${SERVICE}.yaml"
helm template abl deploy/helm/ecommerce --namespace ecommerce -f "deploy/helm/ecommerce/stages/${SERVICE}.yaml" > /tmp/ecommerce-stage.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f /tmp/ecommerce-stage.yaml
helm upgrade --install abl deploy/helm/ecommerce --kube-context ecommerce-abl --namespace ecommerce -f "deploy/helm/ecommerce/stages/${SERVICE}.yaml" --wait --timeout 5m
```

File `/tmp/ecommerce-stage.yaml` hanya memuat referensi Secret, bukan password.
Default chart mengaktifkan seluruh aplikasi; selalu sertakan file stage untuk
deployment bertahap, juga ketika lint/render/server dry-run.
Helm chart meneruskan datasource ke database cluster dan URL dependency memakai
DNS `abl-product-service`, `abl-cart-service`, serta `abl-order-service`.

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce rollout status "deployment/abl-${SERVICE}-service" --timeout=300s
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get pods,deployments,services
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce logs "deployment/abl-${SERVICE}-service" -c "${SERVICE}-service" --tail=100
istioctl proxy-status --context=ecommerce-abl
```

Target: aplikasi Ready dengan sidecar (umumnya 2/2 Running), Deployment 1/1,
startup Spring Boot berhasil, Flyway selesai tanpa error, dan proxy aplikasi
terhubung serta sinkron. `helm lint` atau status Running saja tidak membuktikan
request bisnis berhasil. Jika image source berubah, gunakan tag baru dan
pertahankan override tag tersebut pada setiap Helm upgrade selanjutnya; jangan
mengasumsikan tag lama dengan IfNotPresent otomatis memuat build terbaru.

### 4.5 Akses API dari laptop untuk uji awal

Buka satu terminal port-forward per service yang perlu diuji. Misalnya Cart:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce port-forward service/abl-cart-service 18082:8080
```

| Service | Target port-forward | Port laptop |
|---|---|---|
| Product | `service/abl-product-service` | `18081:8080` |
| Cart | `service/abl-cart-service` | `18082:8080` |
| Order | `service/abl-order-service` | `18083:8080` |
| Payment | `service/abl-payment-service` | `18084:8080` |

Gunakan pola perintah yang sama untuk membuka port-forward lainnya. Port
18081–18084 dipilih agar tidak bercampur dengan aplikasi host 8081–8084.
Terminal ini hanya meneruskan koneksi; aplikasi berjalan di Pod, bukan sebagai
proses Spring Boot host. Panggilan antarservice tetap memakai DNS cluster.

## 5. Uji alur bisnis secara berurutan

Jalankan curl di terminal pengujian setelah port-forward yang diperlukan aktif.
Ganti ID contoh dengan ID dari response aktual. Cluster baru bisa mempunyai
ID berbeda dari pengujian Compose sebelumnya.

Sebelum API bisnis, uji HTTP readiness dari terminal pengujian. Contoh Product
setelah port-forward Product pada port 18081 aktif:

```bash
curl --fail --show-error http://127.0.0.1:18081/actuator/health/readiness
```

Target: HTTP 200 dengan status UP. Readiness tidak menggantikan POST/GET bisnis.

### Product

```bash
curl -i -X POST http://127.0.0.1:18081/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard Cluster","price":350000,"stock":20}'
curl -i http://127.0.0.1:18081/api/v1/products
```

Target: POST 201, GET 200; catat productId. Product mengembalikan Location
berbentuk path relatif, misalnya `/api/v1/products/3`. Gabungkan dengan origin
request: pada gateway lokal menjadi `http://127.0.0.1:18080/api/v1/products/3`.
Ini mempertahankan port publik meskipun alamat yang dilihat backend berbeda.

### Cart

```bash
curl -i -X POST http://127.0.0.1:18082/api/v1/carts \
  -H 'Content-Type: application/json' -d '{"customerId":101}'
```

Catat cartId. Contoh berikut mengasumsikan cartId 1 dan productId 1; sesuaikan:

```bash
curl -i -X POST http://127.0.0.1:18082/api/v1/carts/1/items \
  -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
curl -i http://127.0.0.1:18082/api/v1/carts/1
```

Target: POST cart 201, POST item 201, GET 200, quantity 2 dan total 700000.
Keberhasilan POST item juga menguji koneksi Cart ke Product di cluster.

### Order

```bash
curl -i -X POST http://127.0.0.1:18083/api/v1/orders \
  -H 'Content-Type: application/json' -d '{"cartId":1,"customerId":101}'
curl -i http://127.0.0.1:18083/api/v1/orders/1
```

Sesuaikan cartId dan ID pada GET dari response. Target: POST 201, GET 200,
total 700000, status PENDING. Cart harus memiliki item dan customer yang sesuai.

### Payment

```bash
curl -i -X POST http://127.0.0.1:18084/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: cluster-payment-001' \
  -d '{"orderId":1,"method":"COD"}'
curl -i http://127.0.0.1:18084/api/v1/payments/1
```

Sesuaikan orderId dan paymentId. Target: POST 201, GET 200, amount 700000,
status PENDING. Ulangi POST dengan key dan body sama: respons kembali memakai
payment yang sama. Key sama dengan method TRANSFER harus menghasilkan 409
IDEMPOTENCY_KEY_REUSED. Header hilang menghasilkan 400; GET ID yang tidak ada
menghasilkan 404. Simulator pembayaran tidak dibutuhkan dan tetap nonaktif.

## 6. Bedakan bukti aplikasi cluster dan bukti ingress Istio

Istio control plane aktif, sidecar terpasang, dan API melalui port-forward
berhasil adalah bukti tahap aplikasi Kubernetes. Itu belum membuktikan routing
ingress. Chart aplikasi tidak membuat Gateway atau VirtualService; manifest
terpisah tersedia di `deploy/istio/`. Terapkan route hanya setelah aplikasi
service tersebut Ready. Product bisa diuji lebih dahulu tanpa Cart/Order/Payment.

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f deploy/istio/gateway.yaml -f "deploy/istio/${SERVICE}-route.yaml"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f deploy/istio/gateway.yaml -f "deploy/istio/${SERVICE}-route.yaml"
istioctl analyze --context=ecommerce-abl -n ecommerce
```

Gateway bersama berada di namespace istio-system, tempat Pod ingress berada.
Route aplikasi berada di ecommerce dan merujuk `istio-system/ecommerce-gateway`.
Route mempertahankan path `/api/v1/<resource>` tanpa rewrite dan hanya menerima
path collection tepat atau detail dengan pemisah `/`. Host wildcard dan HTTP
dipakai untuk lab lokal ini; konfigurasi domain/TLS lingkungan lain perlu
disesuaikan. Pola mengikuti [panduan ingress Istio](https://istio.io/latest/docs/tasks/traffic-management/ingress/ingress-control/).

Buka terminal khusus untuk akses gateway dari laptop:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n istio-system port-forward service/istio-ingressgateway 18080:80
```

Biarkan terminal tersebut berjalan, lalu di terminal pengujian:

```bash
curl -i http://127.0.0.1:18080/api/v1/products
```

Target: 200 dengan daftar Product dari database cluster. Ulangi POST dan GET
pada bagian 5 dengan port **18080** untuk seluruh service yang route-nya sudah
diterapkan. Jangan mengasumsikan ID dari database Compose. Satu port-forward ke
gateway cukup untuk akses ini. Berbeda dari port-forward langsung ke Service
aplikasi, jalur ini melewati Envoy ingress dan VirtualService:

```text
curl localhost:18080 -> Istio ingressgateway -> route service
                    -> Pod aplikasi dengan sidecar -> PostgreSQL cluster
```

Port-forward gateway masih diperlukan untuk membawa trafik laptop ke cluster
Docker Desktop; ini belum membuktikan akses LoadBalancer eksternal atau TLS.
Simpan bukti GET dan POST melalui gateway, bukan hanya status Pod. Setelah route
baru dipasang, ulangi `istioctl analyze` dan request API. Jangan menyatakan semua
service selesai sebelum seluruh route dan alur antarservice diuji.

Jika perlu membatalkan route service tanpa menghentikan aplikasinya:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl delete -f "deploy/istio/${SERVICE}-route.yaml"
```

Gateway bersama tetap digunakan service lain; jangan menghapusnya untuk
membatalkan satu route. Perubahan route terpisah dari riwayat rollback Helm.

## 7. Jika deployment atau pengujian gagal

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get events --sort-by=.lastTimestamp
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce describe deployment "abl-${SERVICE}-service"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce logs "deployment/abl-${SERVICE}-service" -c "${SERVICE}-service" --tail=100
```

| Gejala | Pemeriksaan berikutnya |
|---|---|
| Pending | Event Pod: CPU/RAM kurang atau PVC belum Bound |
| ImagePullBackOff | Build dan image load pada profil yang benar; repository/tag sama dengan Helm |
| CreateContainerConfigError | Secret dan key username/password tersedia di namespace ecommerce |
| CrashLoopBackOff | Log container aplikasi; datasource, Flyway, dan error startup |
| Aplikasi hanya 1/1 tanpa sidecar | Label injeksi namespace dan nama container Pod; label sebelum pembuatan Pod |
| HTTP 503 | Dependency cluster Ready dan URL DNS sesuai; bukan localhost host |
| curl connection refused | Port-forward belum aktif atau port berbeda |
| PostgreSQL password authentication failed | Secret cocok dengan role yang sudah dibuat pada PVC |

Untuk Pod crash, ambil nama Pod dari `get pods`, lalu gunakan
`logs <nama-pod> -c <service>-service --previous`. Jika update release gagal,
periksa riwayat dan rollback ke revisi sebelumnya yang sudah terbukti baik:

```bash
helm history abl --kube-context ecommerce-abl -n ecommerce
```

Setelah menentukan nomor revisi yang benar:

```bash
helm rollback abl NOMOR_REVISI --kube-context ecommerce-abl -n ecommerce --wait --timeout 5m
```

Rollback Helm tidak memulihkan data PostgreSQL atau membatalkan migrasi Flyway.
Jangan menghapus PVC/cluster untuk memperbaiki masalah sebelum mempertimbangkan
data yang sudah tersimpan.

## 8. Bukti dan serah terima ke anggota berikutnya

Perbarui catatan bukti runtime pada panduan ini berdasarkan output aktual. Bedakan manifest
tersedia, lint/dry-run lolos, rollout Ready, API langsung berhasil, dan ingress
berhasil. Deployment Ready tidak membuktikan seluruh jenis pengujian tersebut.
Pengujian kebijakan mTLS/AuthorizationPolicy dan enforcement NetworkPolicy
merupakan pekerjaan terpisah setelah alur dasar stabil. Chart belum membuat
ServiceAccount khusus per aplikasi; pisahkan identitas aplikasi sebelum
otorisasi berdasarkan identitas workload. NetworkPolicy yang tersedia memerlukan
CNI dengan dukungan enforcement dan pengujian akses diizinkan/ditolak.

Setiap pemilik menyimpan screenshot node Ready, Pod aplikasi dan database,
PVC Bound, log startup/migrasi tanpa kredensial, status proxy Istio, serta
request/response GET dan POST. Catat context, namespace, release, tag image,
tanggal, ID data uji, status aktual, dan kendala yang ditemukan. Laporkan apakah
aksesnya port-forward atau ingress.

Setelah tahap siap, review perubahan lalu buat satu commit Conventional Commits
untuk tahap tersebut dan push branch kerja. Jangan commit `.env`, `.secrets/`,
laporan, atau screenshot pribadi tanpa kesepakatan tim. Sampaikan ke anggota
berikutnya service pendahulu yang sudah siap dan cara mengulangi datanya.
