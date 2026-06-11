-- Convert customer_order.shipping_address_json values stored as PostgreSQL large-object OIDs into plain JSON text.
UPDATE customer_order
SET shipping_address_json = convert_from(lo_get(shipping_address_json::bigint), 'UTF8')
WHERE shipping_address_json IS NOT NULL
  AND shipping_address_json ~ '^[0-9]+$'
  AND length(shipping_address_json) < 12;
