# Zoho webhooks and details

Zoho Books / Inventory **pushes** events to Mini’s n More. That is separate from MiniMS **calling** Zoho (checkout creates sales orders, admin pull/push, schedulers).

Secrets live only in `/etc/mini-and-more/prod.env`. Do not paste them here.

Full payload examples and workflow criteria: [`docs/zoho-webhook-setup-and-purpose.md`](../zoho-webhook-setup-and-purpose.md).

## Production URLs

All HTTPS on [https://minisnmore.com](https://minisnmore.com).

| Webhook | Method | URL | Nginx target |
| --- | --- | --- | --- |
| Invoice status | POST | `/api/v1/zoho/invoice-webhook` | Catalog `:9184` (bypass gateway) |
| Invoice (internal alias) | POST | `/internal/v1/zoho/invoice-webhook` | Catalog `:9184` |
| Shipment | POST | `/internal/v1/zoho/shipment-webhook` | Gateway `:9180` → catalog |
| Sales order status | POST | `/internal/v1/zoho/order-status-webhook` | Gateway `:9180` |
| Item / catalog | POST | `/internal/v1/zoho/item-webhook` | Gateway `:9180` |
| Stock | POST | `/api/v1/zoho/stock-webhook` | Gateway `:9180` → `/api/` |

Invoice is special: Nginx proxies it **straight to catalog core** so a gateway restart cannot drop paid/void events.

Admin Zoho UI (OAuth, bulk sync) uses `/api/v1/admin/zoho/` with a **600s** proxy timeout.

## Secrets

| Webhook | Env var | Zoho custom header |
| --- | --- | --- |
| Invoice | `ZOHO_INVOICE_WEBHOOK_SECRET` | `X-Zoho-Invoice-Secret` |
| Shipment | `ZOHO_SHIPMENT_WEBHOOK_SECRET` | `X-Zoho-Shipment-Secret` |
| Order status | `ZOHO_ORDER_STATUS_WEBHOOK_SECRET` | `X-Zoho-Order-Status-Secret` |
| Item | `ZOHO_ITEM_WEBHOOK_SECRET` | `X-Zoho-Item-Secret` |
| Stock | `ZOHO_STOCK_WEBHOOK_SECRET` | `X-Zoho-Stock-Secret` |

Zoho may also send `X-Zoho-Webhook-Signature`. MiniMS accepts either the HMAC signature or the shared-secret header.

Secret rules (Zoho): 12–50 characters, alphanumeric only, no punctuation.

A `401` from MiniMS means the header does not match `prod.env`. Restart catalog (and gateway for `/internal` routes) after changing secrets.

## What each webhook does

| Event | MiniMS effect |
| --- | --- |
| Invoice created/edited (Paid / Sent / Void) | Local invoice id, payment/void state, PDF cache opportunity |
| Shipment created/edited | Tracking, “on the way”, delivered — **this is the delivery source of truth** |
| Sales order status (Invoiced / Void / Cancelled / Fulfilled) | Accounting state. **`fulfilled` does not mark the store order delivered** |
| Item changed | One product/SKU refresh instead of a full catalog pull |
| Stock | Absolute `stock_on_hand` for that SKU (variants need `sku`) |

### Shipment rules that matter operationally

Three Zoho Inventory workflow rules should all call the **same** shipment webhook:

1. Package created (package slip not empty) → MiniMS can show packed / hand-wrapped.
2. Status **Shipped** → on the way (not delivered).
3. Status **Fulfilled** (or Delivered) → MiniMS delivered.

Do not invent a MiniMS custom field to fake delivery. A later sales-order “fulfilled” from invoicing must not undo a shipment already marked delivered.

Payload **must** include `salesorder_id` or `salesorder_number` so MiniMS can find the local order. Default Zoho preview JSON is not enough.

`eventId` is used for idempotency. Duplicate posts return `200` and are ignored.

## Outbound Zoho (not webhooks)

Configured in `/admin/zoho` and env:

- OAuth: `ZOHO_CLIENT_ID`, `ZOHO_CLIENT_SECRET`, `ZOHO_ORG_ID`, `ZOHO_DATA_CENTER=in`
- Redirect: `https://minisnmore.com/admin/zoho` (must match the Zoho API console)
- Flags such as `ZOHO_AUTO_SYNC_ENABLED`, `ZOHO_ORDER_TRACKING_ENABLED`

If those flags are `false`, schedulers and order-page tracking pulls stop. **Webhooks still work.** Use that only when testing whether Zoho actually POSTs.

## How to verify

```text
# Nginx saw Zoho
grep zoho /var/log/nginx/access.log | tail

# Catalog / gateway logs
pm2 logs mini-prod-ms-catalog-core --lines 100
pm2 logs mini-prod-ms-gateway --lines 100
```

| HTTP | Meaning |
| --- | --- |
| 200 | Accepted or duplicate `eventId` |
| 400 | Bad JSON or missing required fields |
| 401 | Secret mismatch |
| 404 | No matching MiniMS order/product |
| 503 | Secret missing in env, or catalog down |

Postgres tables (in `miniandmore`):

```text
zoho_invoice_webhook_event
zoho_shipment_webhook_event
zoho_order_status_webhook_event
zoho_item_webhook_event
zoho_stock_webhook_event
```

## Zoho console checklist

1. API console app: production redirect `https://minisnmore.com/admin/zoho`.
2. Workflow **Actions** (webhooks) created with compact JSON payloads (placeholders must not wrap across lines).
3. Workflow **Rules** on Invoices, Shipment Orders, Sales Orders, Items.
4. Org id matches `ZOHO_ORG_ID`.
5. After OAuth, `/admin/zoho` shows connected; do not full-sync catalog during peak.
