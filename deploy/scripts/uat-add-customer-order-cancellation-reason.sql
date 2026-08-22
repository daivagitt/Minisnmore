-- UAT helper: capture why a customer cancelled an order.
-- Mirrors liquibase 20260822120000_add_customer_order_cancellation_reason.xml

ALTER TABLE customer_order
  ADD COLUMN IF NOT EXISTS cancellation_reason varchar(500);
