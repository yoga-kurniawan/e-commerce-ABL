# Build image dan deployment aplikasi

Untuk otomasi build, database, aplikasi, dan route dalam satu perintah per tahap,
lihat [panduan script deployment lokal](../deploy/README.md).

Dokumen ini menjelaskan build dan parameter Helm. Untuk alur operasional lengkap
di Bash/Ubuntu WSL, gunakan [panduan deployment tim](panduan-deployment-tim.md).
Struktur konfigurasi dijelaskan di bawah; bukti runtime dan batas pengujian
dicatat pada panduan tim.

## Lingkup dan prasyarat

Dockerfile dan Helm chart yang sama digunakan pada **Windows dan Linux**. Image yang dihasilkan adalah Linux container; Windows menjalankannya melalui Docker Desktop dengan Linux containers. Linux memerlukan Docker Engine yang aktif. Sediakan Helm CLI untuk pemeriksaan chart. Untuk deployment, siapkan Minikube dan Istio; contoh di bawah memakai kubectl bawaan Minikube.

Command berlabel `console` dapat diketik di PowerShell maupun Bash; jangan mengetik tanda prompt. Jika sintaks berbeda, tersedia blok terpisah. Jalankan dari root repository yang memiliki `pom.xml` dan folder `services/`.

Windows PowerShell, sesuaikan lokasi checkout:

```powershell
Set-Location D:\GitHub_Local_Project\e-commerce-ABL
```

Linux Bash, sesuaikan lokasi checkout:

```bash
cd ~/ABL-Project
```

Periksa Docker pada keduanya:

```console
docker version
docker info
helm version --short
```

Pastikan bagian Server Docker tersedia. Di Windows, buka Docker Desktop dan pilih Linux containers. Java 17 dan Maven untuk build image berjalan di dalam container, sehingga versi Java host tidak menentukan build image ini.

Untuk performa Minikube optimal di Windows WSL2, disarankan mengonfigurasi `%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=8GB
processors=4
```

Tersedia empat Dockerfile dan Helm chart aplikasi di `deploy/helm/ecommerce/`.
`compose.local.yaml` menyediakan database untuk aplikasi yang berjalan di host.
Database cluster tersedia sebagai manifest terpisah di `deploy/kubernetes/`,
sedangkan Gateway Istio ada di `deploy/istio/gateway.yaml`, dan rute serta kebijakan keamanan mesh dikelola langsung oleh Helm chart. Chart aplikasi tidak
otomatis menerapkan manifest tersebut, membuat Secret, atau memasang Istio.

Untuk workflow lokal ini, gunakan profile/context `ecommerce-abl`, release `abl`,
dan namespace `ecommerce`. Helm membentuk nama Service dari release secara otomatis.
Di WSL, jalankan tools dari distro dan checkout yang sama, misalnya `~/ABL-Project`;
tools/profile Windows bisa berbeda dari tools/profile Ubuntu.

## Struktur

```text
.dockerignore
services/
├── product-service/Dockerfile
├── cart-service/Dockerfile
├── order-service/Dockerfile
└── payment-service/Dockerfile
deploy/
├── helm/ecommerce/
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── stages/                    # product, cart, order, payment; kumulatif
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── serviceaccount.yaml
│       ├── istio-virtualservice.yaml
│       ├── istio-destinationrule.yaml
│       └── istio-security.yaml
├── kubernetes/                    # README dan empat manifest *-db.yaml
└── istio/                         # gateway.yaml
```

## 1. Build image

Dockerfile memakai JDK 17 pada build stage dan JRE 17 pada runtime stage. Maven Wrapper mengikuti versi repository. Build context wajib root karena module menggunakan parent POM bersama. Container aplikasi memakai user 10001 dan port 8080. Dockerfile menormalkan CRLF pada wrapper agar build dari checkout Windows tetap dapat berjalan.

Command berikut sama untuk PowerShell dan Bash. Kamu bisa mulai dari Product saja, lalu membangun service lain ketika siap:

```console
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:0.2.0-local .
docker build -f services/cart-service/Dockerfile -t ecommerce/cart-service:0.2.0-local .
docker build -f services/order-service/Dockerfile -t ecommerce/order-service:0.2.0-local .
docker build -f services/payment-service/Dockerfile -t ecommerce/payment-service:0.2.0-local .
```

Pastikan setiap build berhasil sebelum melanjutkan. Build menjalankan `package -DskipTests`: kompilasi test tetap dilakukan, tetapi tests tidak dijalankan. Jalankan tests secara terpisah menggunakan datasource/profile pengujian. Akses registry image dan repository Maven diperlukan. Tag image dasar mengikuti keluarga Java 17; pin digest setelah image diuji tim untuk build yang lebih reproducible.

`.dockerignore` mengeluarkan `.env`, secret lokal, hasil build host, dan metadata Git dari build context. Jangan menyimpan kredensial dalam Dockerfile atau image.

## 2. Periksa template tanpa cluster

Command sama untuk PowerShell dan Bash:

```console
helm lint deploy/helm/ecommerce -f deploy/helm/ecommerce/values-minikube.yaml
helm template abl deploy/helm/ecommerce --namespace ecommerce -f deploy/helm/ecommerce/values-minikube.yaml --output-dir rendered
```

Default menghasilkan empat Deployment dan empat Service ClusterIP beserta ServiceAccount khusus per service dan kebijakan mesh Istio. Helm membuat direktori keluaran `rendered/`, yang sudah diabaikan Git. Template bisa diperiksa tanpa menjalankan Kubernetes, aplikasi, atau database. Kelulusan render bukan bukti workload siap atau API GET/POST berfungsi.

Contoh memeriksa Product saja:

```console
helm lint deploy/helm/ecommerce -f deploy/helm/ecommerce/values-minikube.yaml -f deploy/helm/ecommerce/stages/product.yaml
helm template abl deploy/helm/ecommerce --namespace ecommerce -f deploy/helm/ecommerce/values-minikube.yaml -f deploy/helm/ecommerce/stages/product.yaml
```

Gunakan file stage yang sama saat validasi dan install. Default chart mengaktifkan
semua aplikasi; untuk deployment bertahap selalu pilih stage yang sesuai.
Cart mempertahankan Product; Order mempertahankan Product dan Cart; Payment
mengaktifkan seluruh aplikasi. Kembali ke stage lebih awal dapat menghapus
Deployment/Service yang dinonaktifkan. Dependency yang dimatikan belum memiliki
endpoint aktif; aktifkan sebelum menguji alur bisnis yang memanggilnya.

## 3. Parameter deployment

| Parameter values | Kegunaan |
|---|---|
| `services.<nama>.enabled` | Mengaktifkan workload per service |
| `services.<nama>.image.repository` / `tag` | Lokasi dan versi image |
| `services.<nama>.image.digest` | Digest opsional; jika diisi mengalahkan tag |
| `services.<nama>.database.host` / `port` / `name` | Alamat database yang dapat dijangkau Pod |
| `services.<nama>.database.existingSecret` | Nama Secret dengan key `username` dan `password` |
| `replicaCount` | Default satu replica per service |
| `resources` | Anggaran CPU/memori awal; evaluasi saat pengujian |
| `imagePullSecrets` | Secret registry privat jika diperlukan |
| `imagePullPolicy` | Default `IfNotPresent` untuk workflow image lokal |

Default host database `product-db`, `cart-db`, `order-db`, dan `payment-db` merupakan kontrak alamat yang harus disediakan, bukan database yang otomatis dibuat chart. Nama database masing-masing `product_db`, `cart_db`, `order_db`, dan `payment_db`, pada port 5432.

Alamat `127.0.0.1` di Pod mengarah ke Pod itu sendiri. Database Compose pada Windows/Linux tidak otomatis dapat dijangkau melalui DNS database cluster. Siapkan database cluster atau database eksternal dengan akses jaringan dari Pod dan sesuaikan values.

Client URL otomatis memakai `<release>-product-service:8080`, `<release>-cart-service:8080`, dan `<release>-order-service:8080` dalam namespace yang sama. Bean REST client perlu memakai property `clients.*-url`. Chart meneruskannya melalui `SPRING_APPLICATION_JSON`.

Gunakan file override tanpa kredensial untuk lingkungan tertentu, lalu tambahkan `-f <override.yaml>` pada lint/template/install. Gunakan nama release dan namespace secara konsisten.

Panduan bertahap untuk anggota tim tersedia pada
[Panduan deployment Minikube dan Istio untuk tim](panduan-deployment-tim.md).
Panduan tersebut memakai profil `ecommerce-abl`, manifest database terpisah,
dan file tahap Helm kumulatif untuk Product, Cart, Order, serta Payment.

## 4. Siapkan Secret dan database cluster

Bagian ini dijalankan setelah cluster siap. Untuk database dari manifest repo,
buat Secret sebelum menerapkan StatefulSet. Contoh memakai namespace `ecommerce`
dan release `abl`. Pembuatan namespace berikut berlaku di kedua shell:

```console
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl create namespace ecommerce --dry-run=client -o yaml | minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f -
```

Siapkan file kredensial Product di Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force .secrets | Out-Null
notepad .secrets/product-db.env
```

Atau Linux Bash:

```bash
mkdir -p .secrets
chmod 700 .secrets
(umask 077; touch .secrets/product-db.env)
nano .secrets/product-db.env
```

Isi dengan user/password database yang sebenarnya. Contoh format, bukan kredensial siap pakai:

```dotenv
username=product_user
password=GANTI_DENGAN_PASSWORD_DATABASE
```

Simpan UTF-8 tanpa BOM. Untuk `--from-env-file`, nilai bersifat literal, jadi jangan memakai quote pelindung Bash. Parser ini berbeda dari `.env` Compose. Batasi akses file melalui permission sistem operasi; `.secrets/` sudah diabaikan Git.

Buat Secret, command sama pada kedua shell:

```console
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce create secret generic product-db-credentials --from-env-file=.secrets/product-db.env
```

Ulangi untuk Cart, Order, dan Payment dengan file serta nama Secret sesuai `values.yaml`. Ini command pembuatan awal; jika Secret sudah ada, lakukan pembaruan terkontrol. Jangan menaruh kredensial nyata pada values atau rendered manifest.

Terapkan database Product setelah Secret tersedia:

```console
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f deploy/kubernetes/product-db.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f deploy/kubernetes/product-db.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce rollout status statefulset/product-db --timeout=180s
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get pods,pvc,services
```

Target: database `1/1 Running`, PVC Bound, dan Service port 5432. Ulangi sesuai
service pada tahapnya. Database dan aplikasi berada di namespace yang sama;
database menonaktifkan injeksi sidecar pada template Pod. Siapkan Istio dan label
namespace sebelum memasang aplikasi, mengikuti panduan tim. Lihat
[README database](../deploy/kubernetes/README.md) untuk detail persistensi dan
batasan NetworkPolicy. Perubahan Secret tidak otomatis mengganti password role
PostgreSQL pada PVC yang telah diinisialisasi.

## 5. Muat image dan install aplikasi

Untuk Minikube yang sudah aktif pada sistem operasi tempat kamu menjalankan command, muat image yang telah dibangun:

```console
minikube -p ecommerce-abl image load ecommerce/product-service:0.2.0-local
minikube -p ecommerce-abl image load ecommerce/cart-service:0.2.0-local
minikube -p ecommerce-abl image load ecommerce/order-service:0.2.0-local
minikube -p ecommerce-abl image load ecommerce/payment-service:0.2.0-local
```

Docker context dan Minikube profile Windows/Linux dapat berbeda. Periksa konteks tujuan sebelum load/deploy; image build pada satu Docker daemon belum tentu tersedia pada daemon lain. Untuk cluster lain, push ke registry yang dapat dijangkau node, lalu sesuaikan repository/tag/digest dan imagePullSecrets. Pakai tag baru saat build berubah agar rollout dan cache dapat dibedakan.

Muat hanya image service yang sudah dibangun. Setelah image, database, Secret,
dan injeksi sidecar siap, contoh install Product:

```console
helm upgrade --install abl deploy/helm/ecommerce --kube-context ecommerce-abl --namespace ecommerce -f deploy/helm/ecommerce/stages/product.yaml --wait --timeout 5m
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get deployments,pods,services
```

Gunakan stage berikutnya hanya setelah seluruh prasyaratnya siap. Pertahankan
override image/resource tambahan pada setiap upgrade. Jika workload gagal ready,
periksa event Pod dan log aplikasi; status Helm deployed bukan bukti API bisnis.

## 6. Health probes dan pemeriksaan awal

Chart mengaktifkan health/probes Actuator pada management port terisolasi `9090` (`MANAGEMENT_SERVER_PORT=9090`). Startup/liveness memakai `/actuator/health/liveness`. Readiness memakai `/actuator/health/readiness` dengan indikator `readinessState,db`. Kegagalan database memengaruhi readiness, tanpa memasukkan database pada liveness. Root filesystem container read-only; `/tmp` memakai volume sementara. Port bisnis `8080` dan Gateway ingress tidak mengekspos endpoint `/actuator/*`.

Setelah Product ready, jalankan command ini pada salah satu shell untuk probe manual pada management port `9090`:

```console
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce port-forward deployment/abl-product-service 19090:9090
```

Dari terminal Windows PowerShell lain:

```powershell
curl.exe --fail http://127.0.0.1:19090/actuator/health/readiness
curl.exe --fail http://127.0.0.1:19090/actuator/prometheus
```

Atau terminal Linux Bash:

```bash
curl --fail http://127.0.0.1:19090/actuator/health/readiness
curl --fail http://127.0.0.1:19090/actuator/prometheus
```

Uji GET/POST mengikuti `specs/openapi.yaml` dan contoh di panduan tim. Health
sukses tidak membuktikan API bisnis selesai. Kebijakan rute Istio dan keamanan mesh
(`VirtualService`, `DestinationRule`, `PeerAuthentication`, `AuthorizationPolicy`)
dikelola langsung oleh Helm chart, dengan Gateway bersama diterapkan dari `deploy/istio/gateway.yaml`.
Akses bisnis eksternal diuji melalui gateway port 18080 (via `bash scripts/deploy-local.sh gateway`).

Menurut output WSL pengguna pada 16 November 2026, Product sudah lolos startup,
migrasi Flyway, readiness, POST, dan GET daftar/detail melalui akses langsung
serta ingress Istio. Hasil tersebut belum membuktikan runtime Cart/Order/Payment,
seluruh operasi API, persistensi setelah restart, kebijakan mesh, atau enforcement
NetworkPolicy. Domain/TLS publik bukan bagian dari konfigurasi HTTP lab saat ini.

## Acuan teknis

- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Helm template](https://helm.sh/docs/helm/helm_template/)
- [Spring Boot Actuator endpoints dan probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
