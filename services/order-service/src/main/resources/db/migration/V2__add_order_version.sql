ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT ck_orders_version CHECK (version >= 0);
