# Database Product untuk Minikube

Prasyarat: cluster `ecommerce-abl` aktif, namespace `ecommerce` tersedia,
dan Secret `product-db-credentials` berisi key `username` serta `password`.
Kredensial tidak ditulis ke manifest. PostgreSQL memakai image resmi
[`postgres:17.11-bookworm`](https://github.com/docker-library/docs/blob/master/postgres/README.md).

Validasi dan terapkan dari root repository:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply --dry-run=server -f deploy/kubernetes/product-db.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply -f deploy/kubernetes/product-db.yaml
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce rollout status statefulset/product-db --timeout=180s
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce get pods,pvc,services
```

Hasil yang dituju: Pod `product-db-0` berstatus `1/1 Running`, PVC
`data-product-db-0` berstatus `Bound`, serta Service `product-db` tersedia.
Database ini tidak memakai sidecar; Pod aplikasi nantinya memakai injeksi Istio.
NetworkPolicy membatasi ingress ke database dari Pod Product jika CNI mendukung
penegakan NetworkPolicy. CNI kindnet bawaan Minikube tidak memberikan jaminan
penegakan tersebut.

Jika belum siap, periksa tanpa menampilkan Secret:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce describe pod product-db-0
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce logs product-db-0 -c postgres --tail=100
```

StatefulSet menggunakan PVC 1 GiB dari StorageClass default Minikube.
Restart Pod mempertahankan data pada PVC; penghapusan cluster bukan backup.
Nilai Secret dipakai saat inisialisasi volume kosong. Mengubah Secret saja
tidak mengubah password role pada database yang sudah diinisialisasi.
Migrasi tabel Product dijalankan oleh Flyway ketika aplikasi Product mulai.

Tahap ini hanya menyiapkan database Product. Aplikasi, image, database service
lain, serta routing gateway dikerjakan terpisah. Untuk menghentikan database
sementara tanpa menghapus PVC:

```bash
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl -n ecommerce scale statefulset/product-db --replicas=0
```

Terapkan kembali manifest untuk mengembalikan satu replica.
