# Product Service API V2

Product melayani `/api/v1/products` dan `/api/v2/products` dari database yang sama.
Implementasi V2 mengikuti `specs/openapi-v2.yaml` dan keputusan DEC-020 sampai
DEC-022. Versi artefak Maven adalah `0.2.0-SNAPSHOT`; tag image Product untuk
build manual adalah `ecommerce/product-service:0.2.0-local`.

## Perbedaan V1 dan V2

| Operasi | V1 | V2 |
|---|---|---|
| POST | name, price, stock | Tambahan description opsional, maksimum 1000 karakter; absen/null disimpan null |
| GET daftar | Seluruh produk aktif | Filter name, paginasi opsional, header X-Total-Count |
| GET detail | id, name, price, stock | Ditambah description, termasuk ketika nilainya null |
| PUT | Mengganti name/price/stock, mempertahankan description | Mengganti seluruh representasi; description absen/null mengosongkan nilai |
| PATCH | Field lama; field absen dipertahankan | description absen dipertahankan, null menghapus, string kosong tetap string kosong |
| DELETE | Soft delete, 204 tanpa body | Sama; data dan description tetap tersimpan, kedua versi tidak lagi menemukan produk |

Respons V1 tidak mengekspos description atau header X-Total-Count. Tidak ada
reaktivasi produk melalui PUT/PATCH. Kedua versi mengembalikan 404 untuk produk
yang sudah nonaktif. POST V2 menghasilkan Location relatif dengan path V2,
sehingga client me-resolve alamat terhadap origin gateway yang dipakai.

## Filter dan paginasi

`GET /api/v2/products` mengembalikan array terurut berdasarkan ID menaik.
`name` melakukan contains case-insensitive; `%`, `_`, dan backslash diperlakukan
sebagai teks literal. Filter kosong sama dengan tanpa filter. Panjang filter
maksimum 255 karakter.

Paginasi hanya aktif jika `page` atau `size` dikirim. Default page adalah 0 dan
size 20. Page harus nonnegatif; size harus 1 sampai 100. Tanpa page/size, seluruh
hasil yang cocok dikembalikan. Header `X-Total-Count` selalu memuat jumlah produk
aktif yang cocok sebelum paginasi. Halaman di luar jangkauan menghasilkan `[]`
dengan total yang tetap benar. Offset dihitung sebagai long; query pagination
dijalankan PostgreSQL, bukan dengan memuat seluruh katalog ke memori aplikasi.

## Penyimpanan dan kompatibilitas

Flyway `V3__add_product_description.sql` menambahkan kolom nullable
`products.description VARCHAR(1000)`. Migrasi V1 dan V2 tidak diubah. Baris lama
mendapat description null tanpa mengubah harga, stok, ID, maupun status aktif.
Flyway menerapkan migrasi saat aplikasi mulai. Nomor migrasi V3 bukan versi API;
V1 dan V2 API memakai skema database yang sama.

Domain tetap bebas Spring/JPA. DTO HTTP V2 terpisah dari V1. Use case bersama
memakai presence field agar PUT/PATCH V1 mempertahankan data V2 yang tidak
dikenal. Pencarian memakai port ProductSearch dan query database terparameter.

## Pengujian otomatis

Docker harus aktif. Dari root repo:

```bash
./mvnw -pl services/product-service -am verify
bash tests/integration/deploy-local-test.sh
helm lint deploy/helm/ecommerce -f deploy/helm/ecommerce/stages/product.yaml
```

`ProductsApiV2Test` menjalankan database sementara PostgreSQL, MockMvc, dan satu
alur POST/GET HTTP nyata. Test memeriksa kompatibilitas V1/V2, null/absent/empty,
panjang description, input invalid tanpa perubahan data, pagination default,
filter literal, urutan ID, total sebelum paging, page besar, dan soft delete.
`ProductSchemaTest` juga memigrasikan data dari skema V2 ke V3 dan menjalankan
migrate ulang untuk memastikan data historis tetap ada. `ProductDescriptionTest`
memeriksa batas deskripsi serta perilaku immutable domain.

## Menjalankan V2 dan menguji manual

Lokal: jalankan database Compose dan `bash scripts/run-local-service.sh product`.
Gunakan origin `http://127.0.0.1:8081`. Untuk Kubernetes gunakan gateway lokal
`http://127.0.0.1:18080` setelah deployment dan port-forward siap.

```bash
curl -i -X POST http://127.0.0.1:18080/api/v2/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard","description":"Mechanical 87 tombol","price":350000,"stock":20}'
curl -i 'http://127.0.0.1:18080/api/v2/products?name=keyboard&page=0&size=20'
```

Target POST 201; GET 200 dengan array serta X-Total-Count. Catat ID hasil POST,
lalu ganti angka 1 berikut dengan ID aktual:

```bash
curl -i http://127.0.0.1:18080/api/v2/products/1
curl -i -X PUT http://127.0.0.1:18080/api/v2/products/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard RGB","description":"Switch merah","price":375000,"stock":15}'
curl -i -X PATCH http://127.0.0.1:18080/api/v2/products/1 \
  -H 'Content-Type: application/json' -d '{"description":null}'
curl -i http://127.0.0.1:18080/api/v1/products/1
```

Target GET/PUT/PATCH 200; PATCH null menghapus description di V2, dan GET V1
tetap tidak memuat field description. Jika ingin memeriksa soft delete, gunakan
produk uji yang memang boleh dinonaktifkan:

```bash
curl -i -X DELETE http://127.0.0.1:18080/api/v2/products/1
curl -i http://127.0.0.1:18080/api/v2/products/1
```

Target DELETE 204 tanpa body, lalu GET 404.

## Memperbarui deployment Product

Kode yang di-push tidak otomatis mengganti image yang sedang berjalan. Helper
deployment membuat tag baru dan mempertahankan override release sebelumnya:

```bash
bash scripts/deploy-local.sh up product
bash scripts/deploy-local.sh check
```

Jika Cart/Order/Payment sudah terpasang pada release yang sama, gunakan tahap
tertinggi yang sudah aktif (misalnya `up payment`), bukan menurunkannya ke
Product. Helper mencegah downgrade tahap. Periksa `deploy/README.md` untuk
prasyarat Secret, cluster, dan kapasitas sumber daya. Helper membangun ulang
service aktif pada tahap yang dipilih.

Build manual Product:

```bash
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:0.2.0-local .
minikube -p ecommerce-abl image load ecommerce/product-service:0.2.0-local
```

Untuk cluster yang hanya berisi Product, lanjutkan:

```bash
helm upgrade --install abl deploy/helm/ecommerce --kube-context ecommerce-abl \
  -n ecommerce -f deploy/helm/ecommerce/stages/product.yaml --wait --timeout 5m
minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl apply \
  -f deploy/istio/gateway.yaml -f deploy/istio/product-route.yaml
```

Route Product mempertahankan path V1 sekaligus menambahkan path V2, tanpa rewrite.
Periksa rollout, log migrasi V3, analisis Istio, serta GET/POST melalui gateway.
Deployment runtime V2 harus diverifikasi terpisah dari kelulusan test otomatis.
