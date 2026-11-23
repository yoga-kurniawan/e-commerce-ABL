# Mutasi Order dan Payment API v2

Versi artefak tetap `0.2.0-SNAPSHOT`, mengikuti reactor proyek. Properti Maven
`api.version=2.0.0` pada kedua service dicatat oleh goal `build-info` ke
`META-INF/build-info.properties` saat build. Java dan versi Spring Boot mengikuti
konfigurasi proyek yang ada.

## Kontrak mutasi

| Service | Operasi | Perilaku |
| --- | --- | --- |
| Order | PATCH `/api/v2/orders/{orderId}` | Mengubah shippingAddress saat PENDING; respons menyertakan snapshot items |
| Order | POST `/api/v2/orders/{orderId}/cancel` | Pembatalan melalui pengecekan riwayat Payment; data pesanan tetap tersimpan |
| Payment | PATCH `/api/v2/payments/{paymentId}` | Mengisi transactionReference saat PENDING; status dan amount tidak diubah |
| Keduanya | PUT/DELETE pada resource detail | 405 METHOD_NOT_ALLOWED, sesuai spesifikasi |

PATCH Order menolak alamat yang tidak dikirim, null, kosong, atau hanya whitespace.
Atribut lain seperti status dan total diabaikan. Order hilang menghasilkan 404;
Order bukan PENDING menghasilkan 409. Respons v1 tetap tanpa items.

PATCH Payment memakai implementasi bersama v1/v2 yang sudah ada. Reference tidak
di-trim; null/blank, atribut asing, dan JSON duplikat ditolak. Pembayaran terminal
menolak perubahan dengan 409 PAYMENT_NOT_MODIFIABLE. Reference bukan bukti
pembayaran berhasil. PUT dan DELETE tidak ditambahkan karena riwayat pembayaran
harus dipertahankan.

## Build dan verifikasi

```sh
sh mvnw -B -ntp -pl services/order-service,services/payment-service -am test
```

Sebagian pengujian persistence/integrasi membutuhkan Docker untuk Testcontainers.
Untuk pengujian HTTP mutasi saja:

```sh
sh mvnw -B -ntp -pl services/order-service,services/payment-service -am test -Dtest=OrdersApiV2ControllerSliceTest,PaymentsApiV2HttpTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Persiapan deployment lokal

Build image dari root repository:

```sh
docker build -f services/order-service/Dockerfile -t ecommerce/order-service:0.2.0-local .
docker build -f services/payment-service/Dockerfile -t ecommerce/payment-service:0.2.0-local .
```

Nilai Helm untuk Order/Payment menggunakan tag `0.2.0-local`. Template deployment
sudah mengisi alamat antarservice melalui `SPRING_APPLICATION_JSON`, termasuk
`clients.payment-url` untuk pembatalan Order dan `clients.order-url` untuk Payment.
Konfigurasi aplikasi Order juga mencantumkan default Payment lokal port 8084.
Tidak ada migrasi database baru untuk PATCH ini.

Validasi manifest sebelum rollout:

```sh
helm lint deploy/helm/ecommerce
helm template abl deploy/helm/ecommerce
```

Jika image dijalankan pada Minikube, muat kedua image ke cluster sebelum upgrade
release. Verifikasi PATCH, penolakan PUT/DELETE, serta health readiness setelah
rollout. Jika perlu rollback release:

```sh
helm rollback abl <revision-sebelumnya> --wait
```

Perubahan ini hanya menyiapkan konfigurasi; tidak menjalankan deployment.
