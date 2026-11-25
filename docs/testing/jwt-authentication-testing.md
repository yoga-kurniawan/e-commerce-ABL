# Hasil verifikasi JWT dan kepemilikan data

Tanggal: 25 November 2026. Baseline implementasi: `470b33e`.

## Maven

`DOCKER_HOST=unix:///home/exia/.docker/desktop/docker.sock ./mvnw verify`
berhasil. Semua 800 test lulus, tanpa failure/error/skipped:

| Modul | Test |
| --- | ---: |
| security-support | 9 |
| Product | 136 |
| Cart | 113 |
| Order | 146 |
| Payment | 396 |

Test security-support memakai JWT dengan tanda tangan RSA nyata: tanpa token,
token malformed, kedaluwarsa, salah issuer/audience/signature, sub hilang,
identitas customer invalid, akses data sendiri/orang lain, serta CUSTOMER vs ADMIN.
Test MVC Cart/Order/Payment mengaktifkan filter dan method security, memeriksa
V1/V2 dan memastikan use case mutasi tidak dipanggil saat akses ditolak.
Test bisnis lama menonaktifkan security secara eksplisit di environment Surefire;
kelulusan test tersebut sendiri bukan bukti otorisasi.

## Integrasi nyata

`python3 tests/security/jwt-e2e.py` berhasil dengan **60 assertion HTTP**.
Keycloak 26.8.2 berjalan di namespace identity Minikube; token didapat melalui
login password akun lokal customer-a (101), customer-b (202), dan api-admin.
Keempat JAR backend dari build ini berjalan sebagai proses sementara, menggunakan
empat database terpisah pada satu PostgreSQL 17.11 sementara di Docker.

Cakupan V1 dan V2:

- Tanpa JWT atau JWT malformed ditolak 401.
- Customer tidak bisa menambah Product; admin bisa.
- Pemalsuan customerId pada create Cart ditolak 403.
- Customer lain ditolak GET, POST item, PUT, PATCH dan DELETE item Cart.
- Checkout cart orang lain ditolak 403 walaupun customerId body cocok dengan
  identitas pemanggil. Checkout pemilik berhasil; dua Product memicu lookup paralel,
  membuktikan relay JWT pada executor yang sudah ada.
- GET/PATCH Order orang lain ditolak 403.
- Create/GET/PATCH Payment orang lain ditolak 403, termasuk replay menggunakan
  Idempotency-Key milik customer pemilik. Pemilik dapat membuat/membaca/mengubah
  reference Payment.
- List Order/Payment, cancel Order dan clear Cart V2 memeriksa pemilik.
- Cancel order pemilik yang masih memiliki Payment PENDING tetap menghasilkan
  409 sesuai aturan bisnis lama, membuktikan lookup Payment berhasil dan aturan
  bisnis tidak dilewati oleh autentikasi.

Container PostgreSQL dan empat proses backend sementara dibersihkan setelah test.
Database/PVC Product–Cart lama di Minikube tidak disentuh; image deployment bisnis
Minikube belum diganti oleh pengujian ini. Keycloak lokal dan PVC identity tetap ada.
Password fixture berada di Secret dan `.secrets/auth.env`, tidak ikut Git.

## Konfigurasi dan script

- Build Docker Product, Cart, Order, Payment berhasil dengan tag `jwt-local`;
  masing-masing menyertakan library security-support dari reactor build.
- Helm lint serta render chart berhasil; semua deployment merender JWT enabled,
  issuer, audience dan JWKS endpoint internal.
- YAML kedua OpenAPI valid tanpa duplicate key; bearerAuth dan respons 401/403
  didokumentasikan untuk semua operasi bisnis.
- 19 orchestration checks `tests/scripts/deploy-local-test.sh` lulus.
- Bootstrap Keycloak dan rerun berhasil; user profile customer_id hanya dapat
  diedit admin, unmanaged attributes tetap disabled (default Keycloak).

Panduan menjalankan, login dan batas penggunaan lokal ada di
[JWT authentication](../guides/jwt-authentication.md).
