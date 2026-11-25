# Kompatibilitas script test setelah JWT

25 November 2026; branch `feat/jwt-authentication`.

Script functional/integration/resilience/mesh dan k6 yang sebelumnya tanpa token
kini memakai JWT. Fixture Product memakai ADMIN; transaksi memakai CUSTOMER dan
customerId claim token. Helper bersama menangani role, refresh login lokal,
penolakan token eksternal kedaluwarsa, serta preflight sebelum fault injection.

Verifikasi:

| Pemeriksaan | Hasil |
| --- | --- |
| Happy path pada empat backend JWT nyata | 15 assertion lulus |
| Negative contracts pada backend JWT nyata | 16 assertion lulus |
| CRUD dan negative cases V2 pada backend JWT nyata | 67 assertion lulus |
| Happy path ulang sebagai customer-b (202) | 15 assertion lulus |
| k6 nyata, credential mode, 2s/1 VU | 25 HTTP request, 0 kegagalan HTTP/JWT |
| k6 nyata, external tokens/customer-b, 1s/1 VU | 16 HTTP request, 0 kegagalan HTTP/JWT |
| Regression shell JWT/fault/mesh dengan command doubles | 7 test lulus |
| Regression k6 role/refresh/expiry/identity/malformed claims | 5 pemeriksaan lulus |
| Regression helper deployment yang sudah ada | 19 pemeriksaan lulus |
| Bash syntax dan git diff whitespace | Lulus |

Pengujian HTTP nyata memakai JWT dari Keycloak dan empat JAR pada port
20081–20084, dengan database PostgreSQL sementara. Sebuah reverse proxy lokal
20080 dipakai untuk memeriksa mode single BASE_URL; ini **bukan** ingress Istio.
Data maupun proses sesi manual pengguna tidak dipakai sebagai fixture.

Tidak ada scale/restart atau perubahan policy pada cluster bisnis pengguna.
Enforcement Istio, rollout dan failover aktual belum diuji ulang terhadap deployment
JWT penuh; pemeriksaan script skenario tersebut menggunakan command doubles.
Checks meliputi preflight auth gagal/old image tanpa JWT berhenti sebelum mutasi,
restore jumlah replika awal, probe startup failure tidak dianggap penolakan aman,
serta body 403 backend tidak diterima sebagai bukti RBAC Envoy.

`tests/security/jwt-e2e.py` dan `tests/scripts/deploy-local-test.sh` diverifikasi
hash-nya tetap sama. Keduanya tidak diubah. Tidak ada perubahan kode backend JWT.
Panduan token dan menjalankan suite ada di [tests README](../../tests/README.md).
