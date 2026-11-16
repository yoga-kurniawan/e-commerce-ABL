-- Nullable for existing V1 rows; earlier migrations remain unchanged.
ALTER TABLE products ADD COLUMN description VARCHAR(1000);
