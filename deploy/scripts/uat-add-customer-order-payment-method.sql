-- UAT helper: Cash on Delivery support (payment method + COD handling fee).
-- Mirrors liquibase 20260808070000_add_customer_order_payment_method.xml

ALTER TABLE customer_order
  ADD COLUMN IF NOT EXISTS payment_method varchar(12) NOT NULL DEFAULT 'online';

ALTER TABLE customer_order
  ADD COLUMN IF NOT EXISTS cod_fee decimal(21,2) NOT NULL DEFAULT 0;
