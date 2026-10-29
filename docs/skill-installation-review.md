# Pemasangan dan peninjauan delapan skill Codex

Tanggal: 29 Oktober 2026. Scope: repository `D:\GitHub_Local_Project\e-commerce-ABL`.

Delapan skill dipasang melalui helper `skill-installer` ke `.agents/skills/`. Folder tujuan diperiksa agar berada di dalam repository dan bukan symlink/junction. Tidak ada folder skill sebelumnya; seluruh delapan skill merupakan pemasangan baru. Source aplikasi, POM, kontrak API, konfigurasi deployment, dan keputusan bisnis tidak diubah dalam pekerjaan pemasangan ini.

## Skill yang terpasang

| Skill | Lokasi relatif | Jumlah file | Sumber |
|---|---|---:|---|
| hexagonal-architecture | `.agents/skills/hexagonal-architecture/` | 6 | rrezartprebreza/spring-boot-skills |
| spring-data-jpa | `.agents/skills/spring-data-jpa/` | 5 | rrezartprebreza/spring-boot-skills |
| flyway-migrations | `.agents/skills/flyway-migrations/` | 7 | rrezartprebreza/spring-boot-skills |
| testing-pyramid | `.agents/skills/testing-pyramid/` | 4 | rrezartprebreza/spring-boot-skills |
| http-interface-clients | `.agents/skills/http-interface-clients/` | 4 | rrezartprebreza/spring-boot-skills |
| systematic-debugging | `.agents/skills/systematic-debugging/` | 11 | obra/superpowers |
| devops-engineer | `.agents/skills/devops-engineer/` | 10 | Jeffallan/claude-skills |
| kubernetes-specialist | `.agents/skills/kubernetes-specialist/` | 12 | Jeffallan/claude-skills |

Total: 8 skill, 59 file di dalam folder skill. `SKILL.md`, contoh, template, references, metadata UI, dan script pendukung dipertahankan sebagaimana disalin installer. Skill upstream tidak diadaptasi atau ditulis ulang pada tahap ini.

## Versi sumber dan lisensi

Installer menerima commit tertentu, bukan nama branch yang dapat berubah selama pengunduhan. Catatan sumber per skill ada di [skill-sources.json](../.agents/skill-sources.json).

| Repository | Commit sumber | Lisensi yang disimpan |
|---|---|---|
| rrezartprebreza/spring-boot-skills | `c2c23fe4a8aac4278a1b0e6d15955b3dffee1f7c` | [MIT](../.agents/licenses/rrezartprebreza/spring-boot-skills/LICENSE) |
| obra/superpowers | `2ce8aa15d05bd57213e6f746f953aafb7ded7a8b` | [MIT](../.agents/licenses/obra/superpowers/LICENSE) |
| Jeffallan/claude-skills | `143243cb2da0e52b02a766fa69c1ff77a71cc73c` | [MIT](../.agents/licenses/Jeffallan/claude-skills/LICENSE) |

Lisensi diambil dari commit yang sama dengan skill. Pertahankan direktori `.agents/licenses/` bersama skill ketika membagikan repository.

## Konvensi proyek yang menjadi acuan penggunaan

- Java 17 dan versi Spring Boot dari parent `pom.xml` (saat pemasangan: 4.1.2).
- Package `domain`, `usecase`, dan `adapter`; output port berada di `usecase.port.out`.
- Domain entity berupa model bisnis tanpa JPA; persistence adapter memetakan ke JPA entity tersendiri.
- Identifier integer dan request/response mengikuti OpenAPI 3.0.3 proyek; error memakai `code` dan `message`.
- Product, Cart, Order, dan Payment masing-masing memiliki database sendiri. Foreign key dan JOIN hanya dalam database lokal layanan.
- Keputusan bisnis mengikuti `docs/decisions.md`. Pilihan utama yang telah diterima: create Cart lewat endpoint khusus, quantity duplikat ditambah, dan checkout memakai harga tersimpan saat produk masuk Cart.
- Pertanyaan yang belum diputuskan tetap terbuka. Contoh skill tidak menetapkan status baru, stock reservation, autentikasi, idempotency, presisi uang, atau lifecycle Cart untuk proyek ini.

## Temuan kesesuaian dan konflik

| Skill / bagian | Temuan pada file yang dipasang | Penyesuaian saat digunakan |
|---|---|---|
| Hexagonal architecture | Contoh menggunakan `domain.port`, `application`, dan `infrastructure` | Pertahankan lokasi `usecase.port.out` dan struktur `domain/usecase/adapter` proyek. Prinsip dependency dan pemisahan domain/JPA sesuai rancangan. |
| Hexagonal, JPA, Flyway, HTTP client, tests | Banyak contoh memakai UUID | Pertahankan ID integer sesuai OpenAPI; pilih mapping dan generator ID numerik yang sesuai, tanpa mengganti kontrak menjadi UUID. |
| Hexagonal dan testing | Contoh memakai `ApiResponse`, `success`, `data`, serta nested `error` | Respons mengikuti schema OpenAPI; error tetap `{code,message}`. Ubah assertion dan DTO contoh saat implementasi. |
| JPA | Contoh class `Order` adalah JPA entity dengan method bisnis; contoh memakai DRAFT, EUR, serta Lombok | Gunakan sebagai contoh persistence di adapter, bukan domain entity proyek. Jangan menambah status DRAFT, mata uang EUR, atau dependency Lombok hanya untuk menyalin contoh. |
| JPA | Menganjurkan pagination/limit untuk list endpoint | GET Product saat ini mengembalikan array. Jangan mengganti bentuk respons atau memotong daftar diam-diam; perubahan kontrak pagination memerlukan keputusan tersendiri. |
| JPA dan Flyway | Contoh menetapkan skala uang, version, index, dan constraint tertentu | Periksa keputusan bisnis/teknis yang masih terbuka sebelum menetapkan skala atau constraint final. Prinsip migrasi dan mapping harus diterapkan sesuai schema layanan. |
| Flyway | Contoh `orders.user_id` mereferensikan tabel `users` lokal | Jangan menyalin foreign key lintas service. Rancangan proyek belum menyediakan User Service atau tabel customer terverifikasi. |
| Testing pyramid | Contoh memakai PostgreSQL 16, JWT, Spring Security, fixture user, dan response envelope | Gunakan PostgreSQL 17 untuk pengujian kompatibilitas target. Sesuaikan tests dengan dependency dan fitur aktual; contoh security bukan dasar untuk menambahkan autentikasi. |
| Testing pyramid | Menampilkan proporsi 70/20/10 dan dependency Testcontainers | Proporsi merupakan panduan, bukan target jumlah test wajib. Cocokkan artifact/import dengan dependency management POM; Testcontainers belum ditambahkan dalam pekerjaan ini. |
| HTTP interface clients | Contoh memakai `spring.http.serviceclient.<group>.*` dan `/orders` | Setup proyek meneruskan `clients.product-url`, `clients.cart-url`, dan `clients.order-url`. Implementasi perlu bridge/wiring yang nyata dan prefix `/api/v1`; jangan menganggap property lama langsung mengonfigurasi proxy baru. Pertahankan client di outbound adapter di balik output port. |
| Systematic debugging | Merujuk `superpowers:test-driven-development` dan `superpowers:verification-before-completion` | Kedua skill tambahan itu belum terpasang karena permintaan hanya mencakup delapan skill. Rujukan workflow lintas skill belum lengkap; jangan mengklaim keduanya tersedia. Tidak ada instalasi tambahan otomatis. |
| Systematic debugging | Menyertakan `find-polluter.sh`, fixtures `test-pressure-*`, serta catatan pembuatan skill | Script Bash memerlukan environment yang sesuai seperti WSL/Git Bash. Fixtures upstream merupakan materi pengujian skill, bukan instruksi untuk menjalankan skenario pada proyek. |
| DevOps | Contoh image/pipeline menggunakan Python/Node dan `pytest` | Gunakan Maven Wrapper, build context root multi-module, JAR Spring Boot, dan Java 17 sesuai Dockerfile proyek. |
| DevOps | Instruksi umum meminta secret manager dan GitOps ArgoCD/Flux | Development lokal proyek sudah memakai `.env` yang diabaikan Git, Compose, dan helper Bash. Penambahan secret manager/GitOps merupakan perluasan scope, bukan syarat baru untuk mulai development. |
| Kubernetes | Contoh memakai tiga replica, user 1000, namespace production, serta probe `/healthz` dan `/ready` | Chart proyek memakai satu replica, user 10001, dan Actuator probes. Pertahankan konfigurasi proyek kecuali perubahan dibuktikan perlu dan didokumentasikan. |
| Kubernetes | Meminta ServiceAccount khusus, RBAC, dan NetworkPolicy; contoh Istio memakai command instalasi generik | Chart saat ini mematikan automount token; RBAC/NetworkPolicy belum lengkap. Catat sebagai evaluasi deployment, tanpa mengubah cluster pada tahap instalasi skill. Verifikasi versi Istio/cluster sebelum memakai contoh. |

Temuan di atas adalah hasil pembacaan file yang dipasang dan perbandingan dengan POM, dokumentasi, OpenAPI, Compose, helper lokal, dan Helm chart. Peninjauan ini tidak membuktikan seluruh contoh upstream dapat dikompilasi atau berjalan pada stack proyek.

## Pemeriksaan yang dilakukan

- Delapan direktori skill ditemukan dan sesuai manifest instalasi.
- Setiap `SKILL.md` memiliki delimiter frontmatter, `name` yang sesuai folder, dan `description`.
- Semua sumber dalam manifest memiliki commit 40 karakter hexadecimal dan status `installed`.
- Sebanyak 59 file ditemukan; tidak ada symlink dalam direktori skill.
- Sebanyak 21 rujukan lokal ke `references/`, `templates/`, dan `examples/` pada `SKILL.md` diperiksa dan seluruh target ditemukan. Pemeriksaan ini tidak mencakup semua link web atau dependency lintas skill.
- Tiga file lisensi MIT ditemukan.
- Pemeriksaan metadata merupakan pemeriksaan statis, bukan validasi penuh YAML, pengujian perilaku skill, kompilasi contoh, atau pengujian aplikasi.

## Cara menggunakan

Skill tersedia pada giliran berikutnya. Jika belum muncul pada pemilih skill, muat ulang sesi Codex. Mulai dengan empat skill implementasi Product:

```text
Gunakan $hexagonal-architecture, $spring-data-jpa,
$flyway-migrations, dan $testing-pyramid.

Baca docs/skill-installation-review.md, docs/architecture.md,
docs/decisions.md, dan specs/openapi.yaml terlebih dahulu.

Implementasikan GET dan POST Product Service mengikuti konvensi
proyek dan penyesuaian yang dicatat dalam laporan peninjauan.
Laporkan hasil verifikasi aktual dan bagian yang belum terverifikasi.
```

Gunakan `$http-interface-clients` saat mengintegrasikan consumer REST, `$systematic-debugging` saat menyelidiki error, serta `$devops-engineer` dan `$kubernetes-specialist` pada tahap deployment yang diminta. File upstream tetap asli; laporan ini mencatat penyesuaian yang harus diperhatikan saat penggunaan.
