# Database PostgreSQL untuk Minikube

Pembuatan/pemakaian Secret dan penerapan database dapat diorkestrasi melalui
[script deployment lokal](../README.md). `namespace.yaml` menyiapkan namespace
ecommerce beserta label injeksi; empat file `*-db.yaml` menyiapkan database.

Folder ini menyediakan database terpisah untuk Product, Cart, Order, dan Payment.
Setiap manifest berisi ServiceAccount, headless Service, StatefulSet, PVC melalui
volumeClaimTemplates, probes, dan NetworkPolicy ingress. Database diterapkan
terpisah dari Helm chart aplikasi.

| Service | Manifest | Secret | Database |
|---|---|---|---|
| Product | `product-db.yaml` | `product-db-credentials` | `product_db` |
| Cart | `cart-db.yaml` | `cart-db-credentials` | `cart_db` |
| Order | `order-db.yaml` | `order-db-credentials` | `order_db` |
| Payment | `payment-db.yaml` | `payment-db-credentials` | `payment_db` |

Prasyarat: cluster `ecommerce-abl` aktif, namespace `ecommerce` tersedia,
dan Secret service terkait berisi key `username` serta `password`.
Kredensial tidak ditulis ke manifest. PostgreSQL memakai image resmi
[`postgres:17.11-bookworm`](https://github.com/docker-library/docs/blob/master/postgres/README.md).

Ikuti [panduan tim](../../docs/panduan-deployment-tim.md) untuk membuat Secret
sebelum database, lalu memasang aplikasi. Panduan tersebut juga mencatat bukti
runtime. Berdasarkan output WSL pengguna pada 16 November 2026, database Product
1/1 Running dan PVC Bound; startup, migrasi, serta POST/GET aplikasi Product
berhasil. Database lain dan persistensi setelah restart belum terverifikasi.

Validasi dan terapkan dari root repository dengan Bash/Ubuntu WSL. Pilih service
yang sedang dikerjakan, contoh Product:

```bash
SERVICE=product
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f "deploy/kubernetes/${SERVICE}-db.yaml"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f "deploy/kubernetes/${SERVICE}-db.yaml"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce rollout status "statefulset/${SERVICE}-db" --timeout=180s
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get pods,pvc,services
```

Nilai SERVICE: `product`, `cart`, `order`, atau `payment`. Jalankan perintah yang
memakai variabel tersebut pada terminal yang sama.
Hasil yang dituju: Pod `<service>-db-0` berstatus `1/1 Running`, PVC
`data-<service>-db-0` berstatus `Bound`, serta Service `<service>-db` port 5432.
Service memakai `clusterIP: None` (headless); ini sesuai rancangan StatefulSet.
Database dan aplikasi berada di namespace `ecommerce`. Database tidak memakai
sidecar karena template Pod menonaktifkan injeksi Istio.
NetworkPolicy membatasi ingress ke database dari Pod aplikasi terkait jika CNI mendukung
penegakan NetworkPolicy. CNI kindnet bawaan Minikube tidak memberikan jaminan
penegakan tersebut.

Jika belum siap, periksa tanpa menampilkan Secret:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce describe pod "${SERVICE}-db-0"
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce logs "${SERVICE}-db-0" -c postgres --tail=100
```

StatefulSet menggunakan PVC 1 GiB dari StorageClass default Minikube.
Restart Pod mempertahankan data pada PVC; penghapusan cluster bukan backup.
Nilai Secret dipakai saat inisialisasi volume kosong. Mengubah Secret saja
tidak mengubah password role pada database yang sudah diinisialisasi.
Migrasi tabel dijalankan oleh Flyway ketika aplikasi terkait mulai, bukan ketika
manifest database diterapkan. Rollback Helm tidak mengembalikan data atau
membatalkan migrasi. Jangan menghapus PVC untuk mengatasi kegagalan aplikasi
tanpa mempertimbangkan data yang sudah tersimpan.

Tahap ini hanya menyiapkan database yang dipilih. Aplikasi, image, serta routing
gateway dikerjakan terpisah. Untuk menghentikan database
sementara tanpa menghapus PVC:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce scale "statefulset/${SERVICE}-db" --replicas=0
```

Terapkan kembali manifest untuk mengembalikan satu replica.
