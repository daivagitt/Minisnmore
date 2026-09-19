-- Adds cached-invoice-PDF columns to customer_order.
-- Additive only — safe to run multiple times (IF NOT EXISTS).
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_invoice_pdf_url VARCHAR(600);
