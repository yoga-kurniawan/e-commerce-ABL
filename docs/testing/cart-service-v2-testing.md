# Perbaikan build dan verifikasi Cart V2

Tanggal: 19 November 2026. Baseline: `eaf1fce`.

Kompilasi awal Cart gagal: controller dan domain kekurangan kurung penutup,
CartItemOutputV2 memuat salinan deklarasi file lain, package UpdatedCartItemOutputV2
tidak sesuai, dan mapper memuat karakter sisa. Setelah sintaks diperbaiki,
adapter V2 juga memerlukan import DTO/use case yang benar serta pemanggilan
mapper V2 agar unitPrice tampil pada respons V2 tanpa mengubah respons V1.
Route Istio Cart ditambahkan untuk collection dan detail `/api/v2/carts`.

Validasi setelah perbaikan:

```bash
./mvnw -o -pl services/product-service,services/cart-service -am verify
```

Hasil: **BUILD SUCCESS**. Product: 119 test; Cart: 96 test.
Failures, errors, dan skipped semuanya nol. Kedua JAR versi 0.2.0-SNAPSHOT
berhasil dibuat. Docker Desktop dipakai untuk PostgreSQL sementara Testcontainers.

Delapan kasus baru pada CartsApiV2ControllerTest memeriksa:

- PUT/PATCH mengganti quantity dan mengembalikan unitPrice snapshot, walaupun
  harga katalog Product berubah; V1 tetap tidak mengekspos unitPrice.
- DELETE satu item dan DELETE seluruh item; cart/customerId tetap tersimpan,
  cart_items dihapus dari database, dan clear cart kosong tetap 204.
- Lima variasi input quantity invalid pada PUT dan PATCH menghasilkan 400 dan
  tidak mengubah quantity tersimpan.
- Stok tidak cukup menghasilkan 409; item atau cart yang tidak ada menghasilkan 404.

Test baru memakai Spring Boot, MockMvc, serta PostgreSQL nyata pada container
sementara. ProductLookup di-mock, sehingga hasil ini bukan bukti integrasi
runtime Cart ke Product melalui Istio. Test V1 yang sudah ada juga tetap lulus.
Deployment ulang image dan pengujian gateway Cart V2 belum dilakukan pada tahap
perbaikan laporan ini.

## Verifikasi deployment lokal setelah perbaikan

Pada 19 November 2026, Cart pertama kali dipasang di cluster `ecommerce-abl`.
Release sebelumnya hanya mengaktifkan Product. Image Cart yang diuji:
`ecommerce/cart-service:0.2.0-77e99d9`; database memakai manifest
`deploy/kubernetes/cart-db.yaml`, PVC 1Gi dan Secret acak terpisah dari Git.
Helm mempertahankan values release sebelumnya, menambahkan stage `cart.yaml`,
dan override `services.cart.image.tag=0.2.0-77e99d9`. Route `cart-route.yaml`
diterapkan untuk V1 dan V2. Order dan Payment tetap nonaktif.

Rollout pertama otomatis rollback karena tidak cukup memori untuk pod Product
pengganti. Istiod semula meminta 2Gi. Konfigurasi lokal `deploy/istio/install.yaml`
kini meminta 512Mi dan mengganti satu pod Istiod tanpa surge. Saat penggantian
Istiod, control plane dapat tidak tersedia sementara; konfigurasi ini khusus
Minikube lokal. Setelah konfigurasi diterapkan dengan `istioctl install`, retry
Helm berhasil: release `abl` revisi 6 berstatus deployed, Product dan Cart 2/2
Running, kedua database 1/1 Running. Image Product tetap `0.2.0-local`.

**29 request HTTP melalui port-forward ingress gateway berhasil diverifikasi**
(header `server: istio-envoy`):

- Membuat Product uji dan cart baru, lalu menambahkan item: 201.
- PUT quantity 3: 200, unitPrice 350000 dan subtotal 1050000.
- Setelah harga Product menjadi 400000, PATCH quantity 1 tetap memakai snapshot
  unitPrice 350000 dan subtotal 350000: 200. Lookup Product sungguhan digunakan.
- GET V1 tidak memuat unitPrice; GET V2 memuat unitPrice.
- PUT/PATCH dengan body kosong, quantity null, nol, negatif, atau pecahan: 400.
  Quantity melebihi stok: 409; input gagal tidak mengubah quantity tersimpan.
- DELETE item: 204; penghapusan ulang item yang sama: 404.
- Tambah kembali lalu DELETE seluruh item dua kali: 204. GET cart tetap 200,
  items kosong, total nol, customerId dipertahankan. Cart tidak ditemukan: 404.
- Product uji di-soft-delete: 204, kemudian GET menghasilkan 404.

SQL PostgreSQL setelah pengujian memastikan cart uji 1 dan 2 tetap tersimpan
dan jumlah item untuk keduanya nol. Produk uji 7 dan 8 telah di-soft-delete;
data lama tidak dipakai untuk mutasi pengujian. `istioctl analyze` tidak menemukan
masalah validasi dan proxy Product, Cart, serta ingress terhubung ke Istiod.

Rollback aplikasi bila diperlukan: `helm rollback abl 3 --kube-context
ecommerce-abl -n ecommerce --wait --timeout 5m`. Revisi 3 hanya mengaktifkan
Product; database/PVC Cart terpisah tetap dipertahankan. Periksa pod dan GET
Product melalui gateway setelah rollback.
