-- Add order placement timestamp for account order lists (created_at in API).
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS placed_at timestamp;

UPDATE customer_order
SET placed_at = inventory_reserved_until - INTERVAL '30 minutes'
WHERE placed_at IS NULL AND inventory_reserved_until IS NOT NULL;

UPDATE customer_order
SET placed_at = CURRENT_TIMESTAMP
WHERE placed_at IS NULL;
