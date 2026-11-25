# JWT dan kepemilikan data

Implementasi mempertahankan URL, DTO dan response API V1/V2. Tidak ada tabel akun
baru pada database bisnis. Keycloak menangani login, password hash dan penerbitan
JWT RS256; setiap backend memvalidasi signature, issuer, audience, exp, nbf dan sub
melalui Spring Security Resource Server. Gateway tetap memakai aturan mTLS/RBAC
Helm yang sudah ada. JWT juga diperiksa jika backend diakses langsung.

## Identitas dan izin

JWT memuat `sub` identitas akun, `realm_access.roles` (`CUSTOMER`/`ADMIN`), serta
`customer_id` ID customer numerik positif yang dipetakan oleh admin Keycloak.
Atribut customer_id tidak boleh bisa diedit pengguna. Profile lokal membatasi
edit atribut itu ke admin, unmanaged attributes dimatikan, dan registrasi publik
dinonaktifkan. Sub bukan ID customer numerik dan tidak diambil dari request.

| Endpoint | CUSTOMER | ADMIN |
| --- | --- | --- |
| Product GET | Boleh | Boleh |
| Product POST/PUT/PATCH/DELETE | 403 | Boleh |
| Cart create | customerId body harus cocok dengan JWT | Boleh memilih customer |
| Cart GET/add/PUT/PATCH/DELETE/clear | Cart miliknya sendiri | Semua cart |
| Order create | customerId harus cocok; cart harus miliknya | Semua customer |
| Order GET/PATCH/cancel/list | Order/customer miliknya sendiri | Semua order |
| Payment create/GET/PATCH/list | Order terkait harus miliknya | Semua order |
| Hasil pembayaran internal/simulator | Tidak dibuka oleh JWT | Tidak dibuka oleh JWT |

Field customerId lama tetap wajib agar kontrak/validasi V1/V2 tidak berubah.
Namun field itu bukan bukti identitas: nilai yang berbeda dengan claim JWT
menghasilkan 403 sebelum mutasi. Pengecekan kedua versi API memakai method
security (`@PreAuthorize`) di adapter HTTP; domain dan aturan bisnis tidak
bergantung pada Spring Security.

Payment memverifikasi pemilik melalui endpoint GET Order yang sudah dilindungi.
Bearer token yang telah divalidasi diteruskan hanya ke HTTP service clients
internal. Relay juga bekerja pada executor lookup Product Order karena konteks
request sudah diteruskan executor. Error kepemilikan dari Order/Cart tetap 403,
bukan dianggap dependency outage. Pengecekan Payment dilakukan sebelum replay
idempotensi, sehingga key yang ditebak tidak bisa membuka respons milik orang lain.

Tanpa token/token invalid: 401. Token valid tetapi role/pemilik salah: 403.
Health probes dan endpoint Prometheus tetap tersedia untuk deployment/monitoring;
management port tetap harus dibatasi jaringan seperti konfigurasi mesh saat ini.

## Menjalankan di Minikube lokal

Konfigurasi Keycloak lokal menggunakan HTTP, embedded persistent database dan
password grant untuk CLI pengujian. Ini bukan konfigurasi identity provider
production. Untuk production gunakan HTTPS, database Keycloak terpisah, serta
Authorization Code + PKCE untuk aplikasi pengguna. JWT berumur 10 menit;
logout/password change tidak langsung mencabut access token yang sudah diterbitkan.

1. Jalankan `bash scripts/auth-local.sh up`. Script membuat namespace `identity`,
   Secret acak, realm, PVC dan Keycloak. Tidak memutar password pada rerun.
   Jika PVC ada tetapi Secret hilang, script berhenti agar kredensial lama dipulihkan.
   Password tersimpan lokal di `.secrets/auth.env` (ignored, mode 0600), jangan commit/kirim.
2. Di terminal terpisah: `bash scripts/auth-local.sh gateway` (port 18090).
3. Rebuild/deploy layanan dari kode ini. Helper tetap kumulatif:
   `bash scripts/deploy-local.sh up payment`. Ini membangun keempat image,
   memasang database yang diperlukan dan memperbarui Helm, bukan sekadar
   mengganti konfigurasi pada image lama. Pastikan kapasitas cluster memadai.
4. Di terminal terpisah: `bash scripts/deploy-local.sh gateway` (port 18080).
5. Login lalu kirim request:

```bash
TOKEN=$(python3 scripts/auth-token.py customer-a)
curl -i http://127.0.0.1:18080/api/v2/carts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":101}'
```

Akun lokal: `customer-a` → 101, `customer-b` → 202, `api-admin` → ADMIN.
Hanya akun admin yang boleh mengubah mapping customer_id; jangan mengubah mapping
akun setelah digunakan untuk transaksi tanpa proses migrasi identitas.

Values Helm `auth.enabled` default true; issuer lokal
`http://localhost:18090/realms/ecommerce`. `auth.jwkSetUri` memakai DNS internal
Keycloak, sehingga Pod tidak menghubungi localhost untuk mengambil public key.
Untuk menjalankan JAR di host, atur `APP_SECURITY_JWK_SET_URI` ke URL certs pada
localhost:18090. Pengaturan runtime lain: `APP_SECURITY_ISSUER`,
`APP_SECURITY_AUDIENCE` (default ecommerce-api). Tidak ada private signing key
pada backend. Jangan mematikan `APP_SECURITY_ENABLED` pada deployment terproteksi.

## Pengujian

```bash
./mvnw verify
# Fokus keamanan (tidak membutuhkan Docker):
./mvnw -pl services/security-support,services/cart-service,services/order-service,services/payment-service \
  -am -Dtest='*Jwt*Test' -Dsurefire.failIfNoSpecifiedTests=false test
# Setelah build JAR + auth-local up/gateway, memakai DB sementara terisolasi:
python3 tests/security/jwt-e2e.py
```

Test bisnis lama sengaja memakai `APP_SECURITY_ENABLED=false` dari konfigurasi
Surefire agar tetap menguji kontrak/domain secara terisolasi. Test JWT baru
secara eksplisit mengaktifkan security. Test tanda tangan menggunakan RSA nyata;
test HTTP per service memeriksa akses silang pengguna dan tidak adanya mutasi.
E2E memakai JWT Keycloak sungguhan, keempat backend, PostgreSQL sementara, serta
memeriksa relay token, checkout lintas customer, pembayaran dan replay idempotensi.
DB Minikube bisnis tidak dipakai E2E. Proses/JAR dan container sementara dibersihkan.

Implementasi mengikuti [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
dan [Keycloak realm import](https://www.keycloak.org/server/importExport).

Untuk helper pemeriksaan gateway gunakan token admin:

```bash
AUTH_TOKEN=$(python3 scripts/auth-token.py api-admin) bash scripts/deploy-local.sh check
```

Helper `run-local-service.sh` membangun JAR beserta modul security-support sebelum
menjalankan aplikasi. Untuk pemanggilan Maven `spring-boot:run` secara manual pada
satu modul saja, install parent/library dahulu:
`./mvnw -pl services/security-support -am -DskipTests install`.
