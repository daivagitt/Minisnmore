-- UAT helper: add Exchange support columns to order_return.
-- Mirrors liquibase 20260822150000_add_exchange_support_to_order_return.xml
-- Idempotent: safe to re-run.

ALTER TABLE order_return ADD COLUMN IF NOT EXISTS request_type varchar(16) NOT NULL DEFAULT 'RETURN';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS order_item_id bigint;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS replacement_variant_sku varchar(120);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS replacement_size varchar(40);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS replacement_color varchar(40);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_return__order_item_id'
  ) THEN
    ALTER TABLE order_return
      ADD CONSTRAINT fk_order_return__order_item_id
      FOREIGN KEY (order_item_id) REFERENCES order_item (id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_order_return__order_item_id ON order_return (order_item_id);
CREATE INDEX IF NOT EXISTS idx_order_return__request_type ON order_return (request_type);
