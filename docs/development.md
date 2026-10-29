# Development lokal

[Kembali ke README](../README.md) · [Arsitektur](architecture.md) · [Pengujian](testing.md)

## 1. Lingkup

Setup ini menjalankan empat PostgreSQL lewat Docker Compose. Aplikasi Spring Boot dijalankan langsung pada host Ubuntu/WSL, masing-masing dalam terminal terpisah. Kubernetes dan Istio belum diperlukan.

File yang tersedia:

- `compose.local.yaml`: empat database, port loopback, healthcheck, dan persistent volume.
- `.env.example`: contoh konfigurasi tanpa password nyata.
- `scripts/run-local-service.sh`: membaca `.env` dan meneruskan konfigurasi ke Spring Boot tanpa mengubah kode service.

## 2. Buat konfigurasi pribadi

Dari root repository Ubuntu/WSL:

```bash
cp -n .env.example .env
nano .env
```

Isi keempat `*_DB_PASSWORD` dengan password lokal berbeda. Contoh bentuk pengisian: `PRODUCT_DB_PASSWORD='password-lokal-pilihanmu'`. Jangan menggunakan contoh tersebut sebagai password bersama tim.

Gunakan format `KEY=value` yang kompatibel Bash. Single quote melindungi spasi, `$`, `#`, dan `!`; hindari karakter single quote di dalam password untuk menjaga kompatibilitas parser Compose dan Bash. `.env` hanya milik developer dan diabaikan Git; `.env.example` tetap dikomit. Helper menjalankan `source .env`, sehingga isi file harus dipercaya dan tidak diambil dari pihak lain tanpa ditinjau.

| Service | Port aplikasi host | Database | User | Port DB host |
|---|---:|---|---|---:|
| Product | 8081 | product_db | product_user | 5433 |
| Cart | 8082 | cart_db | cart_user | 5434 |
| Order | 8083 | order_db | order_user | 5435 |
| Payment | 8084 | payment_db | payment_user | 5436 |

Nilai dapat disesuaikan di `.env`. Port DB dalam container tetap 5432. Image database memakai keluarga PostgreSQL 17; pin digest setelah tim menguji image yang dipilih.

## 3. Validasi dan jalankan database

```bash
docker info
docker compose --env-file .env -f compose.local.yaml config --quiet
docker compose --env-file .env -f compose.local.yaml up -d --wait
docker compose --env-file .env -f compose.local.yaml ps
```

Password kosong akan ditolak oleh interpolasi Compose. `--wait` menunggu healthcheck; jika gagal, baca `ps` dan log layanan terkait. Jangan membagikan keluaran `config` tanpa `--quiet` karena dapat memuat password hasil interpolasi.

Contoh diagnosis:

```bash
docker compose --env-file .env -f compose.local.yaml logs --tail=50 product-db
```

Compose membaca `.env`; Spring Boot sendiri tidak otomatis membacanya. Helper pada bagian 5 menyediakan penghubung untuk aplikasi host. Environment yang sudah diekspor di terminal dapat mengalahkan nilai Compose; gunakan terminal bersih bila hasil konfigurasi tidak sesuai `.env`.

## 4. Periksa database

```bash
docker compose --env-file .env -f compose.local.yaml exec product-db \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Di dalam psql:

```sql
SELECT current_database(), current_user;
```

```text
\dt
\q
```

Ganti `product-db` dengan `cart-db`, `order-db`, atau `payment-db`. Healthcheck membuktikan server siap menerima koneksi, bukan kelulusan autentikasi aplikasi atau migrasi bisnis. Tabel bisnis belum muncul sebelum migrasinya ditulis dan dijalankan.

## 5. Jalankan service

Periksa Java dan Maven:

```bash
java -version
bash mvnw -version
bash mvnw validate
```

Jalankan satu command berikut pada tiap terminal, dari root repository yang sama:

```bash
bash scripts/run-local-service.sh product
```

```bash
bash scripts/run-local-service.sh cart
```

```bash
bash scripts/run-local-service.sh order
```

```bash
bash scripts/run-local-service.sh payment
```

Helper mengatur `SERVER_PORT`, datasource standar Spring Boot, `ddl-auto=validate`, Flyway, serta property `clients.product-url`, `clients.cart-url`, dan `clients.order-url` melalui `SPRING_APPLICATION_JSON`. Bean REST client tetap perlu diimplementasikan untuk memakai property tersebut. Helper menimpa `SPRING_APPLICATION_JSON` untuk proses aplikasi yang dijalankannya; gunakan run configuration sendiri bila membutuhkan konfigurasi JSON tambahan.

Tidak ada controller bisnis atau entity yang otomatis dibuat oleh helper. Endpoint bisnis dapat tetap 404 sampai diimplementasikan. Startup juga dapat gagal jika migrasi/entity yang ditulis tidak sesuai skema; baca penyebab pada log.

## 6. Persistent storage dan kredensial

Empat named volume terpisah menyimpan data. Compose memberi prefix nama berdasarkan `COMPOSE_PROJECT_NAME`; pertahankan nilainya agar memakai data yang sama. Tidak ada fixed container name, sehingga pengelolaan dilakukan memakai nama service Compose.

Mengganti password/user/database pada `.env` setelah volume terinisialisasi tidak otomatis memigrasikan role atau data lama. Perubahan kredensial memerlukan perubahan database yang sesuai. Jangan menghapus volume untuk mengatasi error tanpa memastikan datanya boleh dibuang.

Database hanya dipublish ke `127.0.0.1` pada port host yang ditetapkan. Ini setup lokal, bukan rancangan PostgreSQL high availability atau akses produksi.

## 7. Migrasi dan tests

Lokasi migrasi: `services/<service>-service/src/main/resources/db/migration/`.

- Jika belum ada migrasi, mulai dari V1; jika versi sebelumnya sudah diterapkan, tambahkan versi berikutnya.
- Flyway mengelola perubahan skema; Hibernate validate memeriksa mapping JPA.
- Domain bisnis berbeda dari JPA entity; relasi/FK hanya dalam database layanan yang sama.
- Source service, migrasi, dan test belum otomatis dilengkapi oleh setup database ini.
- Generated context test memerlukan konfigurasi datasource pengujian. Jangan menjalankan tests terhadap database development berisi data penting tanpa mengatur isolasi fixture/profile.

Pengembangan domain/unit test tidak membutuhkan cluster. Lihat [testing.md](testing.md) untuk rencana tests dan [decisions.md](decisions.md) untuk aturan bisnis yang belum ditentukan.

## 8. Menghentikan database

```bash
docker compose --env-file .env -f compose.local.yaml stop
```

Untuk menghapus container/network sambil mempertahankan named volume:

```bash
docker compose --env-file .env -f compose.local.yaml down
```

Panduan rutin tidak memakai opsi penghapusan volume. Jalankan kembali dengan `up -d --wait` untuk memakai data yang sama.
