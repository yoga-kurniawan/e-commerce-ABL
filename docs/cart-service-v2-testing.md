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
