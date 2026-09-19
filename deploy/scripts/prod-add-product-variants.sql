-- UAT helper: product variants matrix + order_item.variant_sku
-- Mirrors liquibase 20260714150000_add_product_variants_and_order_item_variant_sku.xml

ALTER TABLE product
  ADD COLUMN IF NOT EXISTS item_structure varchar(40) DEFAULT 'SIMPLE';

ALTER TABLE product
  ADD COLUMN IF NOT EXISTS variants_json text;

ALTER TABLE order_item
  ADD COLUMN IF NOT EXISTS variant_sku varchar(120);
