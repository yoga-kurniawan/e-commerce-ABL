-- Tambahkan optimistic locking version, validasi nama non-blank untuk baris baru/terubah,
-- serta partial index untuk query produk aktif. Migrasi V1-V3 tidak diubah.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT ck_products_version CHECK (version >= 0);
ALTER TABLE products ADD CONSTRAINT products_name_not_blank CHECK (btrim(name) <> '') NOT VALID;
CREATE INDEX idx_products_active_id ON products (id) WHERE active = true;
