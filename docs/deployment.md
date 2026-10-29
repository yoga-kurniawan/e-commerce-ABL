# Build image dan template deployment

## Lingkup dan prasyarat

Dockerfile dan Helm chart yang sama digunakan pada **Windows dan Linux**. Image yang dihasilkan adalah Linux container; Windows menjalankannya melalui Docker Desktop dengan Linux containers. Linux memerlukan Docker Engine yang aktif. Sediakan Helm CLI untuk pemeriksaan chart. Untuk tahap cluster nanti, sediakan kubectl dan cluster tujuan.

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

Tersedia empat Dockerfile dan Helm chart aplikasi di `deploy/helm/ecommerce/`. Database lokal memakai `compose.local.yaml`. Chart ini tidak membuat database, Secret, ingress, atau memasang Istio. Database cluster dan konfigurasi Istio dilengkapi pada tahap berikutnya.

## Struktur

```text
.dockerignore
services/
├── product-service/Dockerfile
├── cart-service/Dockerfile
├── order-service/Dockerfile
└── payment-service/Dockerfile
deploy/helm/ecommerce/
├── Chart.yaml
├── values.yaml
└── templates/
    ├── deployment.yaml
    └── service.yaml
```

## 1. Build image

Dockerfile memakai JDK 17 pada build stage dan JRE 17 pada runtime stage. Maven Wrapper mengikuti versi repository. Build context wajib root karena module menggunakan parent POM bersama. Container aplikasi memakai user 10001 dan port 8080. Dockerfile menormalkan CRLF pada wrapper agar build dari checkout Windows tetap dapat berjalan.

Command berikut sama untuk PowerShell dan Bash. Kamu bisa mulai dari Product saja, lalu membangun service lain ketika siap:

```console
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:0.1.0-local .
docker build -f services/cart-service/Dockerfile -t ecommerce/cart-service:0.1.0-local .
docker build -f services/order-service/Dockerfile -t ecommerce/order-service:0.1.0-local .
docker build -f services/payment-service/Dockerfile -t ecommerce/payment-service:0.1.0-local .
```

Pastikan setiap build berhasil sebelum melanjutkan. Build menjalankan `package -DskipTests`: kompilasi test tetap dilakukan, tetapi tests tidak dijalankan. Jalankan tests secara terpisah menggunakan datasource/profile pengujian. Akses registry image dan repository Maven diperlukan. Tag image dasar mengikuti keluarga Java 17; pin digest setelah image diuji tim untuk build yang lebih reproducible.

`.dockerignore` mengeluarkan `.env`, secret lokal, hasil build host, dan metadata Git dari build context. Jangan menyimpan kredensial dalam Dockerfile atau image.

## 2. Periksa template tanpa cluster

Command sama untuk PowerShell dan Bash:

```console
helm lint deploy/helm/ecommerce
helm template abl deploy/helm/ecommerce --namespace ecommerce --output-dir rendered
```

Default menghasilkan empat Deployment dan empat Service ClusterIP. Helm membuat direktori keluaran `rendered/`, yang sudah diabaikan Git. Template bisa diperiksa tanpa menjalankan Kubernetes, aplikasi, atau database. Kelulusan render bukan bukti workload siap atau API GET/POST berfungsi.

Contoh memeriksa Product saja:

```console
helm template abl deploy/helm/ecommerce --namespace ecommerce --set services.cart.enabled=false --set services.order.enabled=false --set services.payment.enabled=false
```

Gunakan override `enabled` yang sama saat install jika baru Product yang siap. Dependency yang dimatikan belum mempunyai endpoint aktif; aktifkan sebelum menguji alur bisnis yang memanggilnya.

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

Default host database `product-db`, `cart-db`, `order-db`, dan `payment-db` merupakan kontrak alamat yang harus disediakan, bukan database yang otomatis dibuat chart. Nama database masing-masing `product_db`, `cart_db`, `order_db`, dan `payment_db`, pada port 5432.

Alamat `127.0.0.1` di Pod mengarah ke Pod itu sendiri. Database Compose pada Windows/Linux tidak otomatis dapat dijangkau melalui DNS database cluster. Siapkan database cluster atau database eksternal dengan akses jaringan dari Pod dan sesuaikan values.

Client URL otomatis memakai `<release>-product-service:8080`, `<release>-cart-service:8080`, dan `<release>-order-service:8080` dalam namespace yang sama. Bean REST client perlu memakai property `clients.*-url`. Chart meneruskannya melalui `SPRING_APPLICATION_JSON`.

Gunakan file override tanpa kredensial untuk lingkungan tertentu, lalu tambahkan `-f <override.yaml>` pada lint/template/install. Gunakan nama release dan namespace secara konsisten.

## 4. Tahap cluster nanti: Secret

Bagian ini dijalankan setelah cluster dan database siap. Contoh memakai namespace `ecommerce` dan release `abl`. Pembuatan namespace berikut berlaku di kedua shell:

```console
kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
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
kubectl -n ecommerce create secret generic product-db-credentials --from-env-file=.secrets/product-db.env
```

Ulangi untuk Cart, Order, dan Payment dengan file serta nama Secret sesuai `values.yaml`. Ini command pembuatan awal; jika Secret sudah ada, lakukan pembaruan terkontrol. Jangan menaruh kredensial nyata pada values atau rendered manifest.

## 5. Tahap cluster nanti: image dan install

Untuk Minikube yang sudah aktif pada sistem operasi tempat kamu menjalankan command, muat image yang telah dibangun:

```console
minikube image load ecommerce/product-service:0.1.0-local
minikube image load ecommerce/cart-service:0.1.0-local
minikube image load ecommerce/order-service:0.1.0-local
minikube image load ecommerce/payment-service:0.1.0-local
```

Docker context dan Minikube profile Windows/Linux dapat berbeda. Periksa konteks tujuan sebelum load/deploy; image build pada satu Docker daemon belum tentu tersedia pada daemon lain. Untuk cluster lain, push ke registry yang dapat dijangkau node, lalu sesuaikan repository/tag/digest dan imagePullSecrets. Pakai tag baru saat build berubah agar rollout dan cache dapat dibedakan.

Setelah image, database, dan Secret tersedia:

```console
helm upgrade --install abl deploy/helm/ecommerce --namespace ecommerce --create-namespace --wait --timeout 5m
kubectl -n ecommerce get deployments,pods,services
```

Tambahkan file override yang sama dengan saat validasi. Jika hanya Product siap, tambahkan tiga override `enabled=false` seperti bagian 2. Jika workload gagal ready, periksa event Pod dan log aplikasi.

## 6. Health probes dan pemeriksaan awal

Chart mengaktifkan health/probes Actuator. Startup/liveness memakai `/actuator/health/liveness`. Readiness memakai `/actuator/health/readiness` dengan indikator `readinessState,db`. Kegagalan database memengaruhi readiness, tanpa memasukkan database pada liveness. Root filesystem container read-only; `/tmp` memakai volume sementara.

Setelah Product ready, jalankan command ini pada salah satu shell:

```console
kubectl -n ecommerce port-forward service/abl-product-service 8081:8080
```

Dari terminal Windows PowerShell lain:

```powershell
curl.exe --fail http://127.0.0.1:8081/actuator/health/readiness
```

Atau terminal Linux Bash:

```bash
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

Uji GET/POST mengikuti `specs/openapi.yaml` setelah endpoint diimplementasikan. Health sukses tidak membuktikan API bisnis selesai. Jika Spring Security ditambahkan, atur akses probe untuk kubelet. Routing Istio, TLS, policy mesh, deployment database, serta verifikasi workload di cluster merupakan tahap berikutnya.

## Acuan teknis

- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Helm template](https://helm.sh/docs/helm/helm_template/)
- [Spring Boot Actuator endpoints dan probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
