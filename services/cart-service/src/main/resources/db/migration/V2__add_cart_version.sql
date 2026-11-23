-- Tambahkan kolom version untuk optimistic locking aggregate Cart. Migrasi V1 tidak diubah.
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE carts ADD CONSTRAINT ck_carts_version CHECK (version >= 0);
