# Zoho, XpressBees, and Mini’s n More admin integrations

This document separates three related integration surfaces:

1. Zoho Books / Inventory callbacks into Mini’s n More.
2. XpressBees forward-shipment and reverse-pickup callbacks into Mini’s n More.
3. Authenticated Mini’s n More admin actions that call those providers.

Inbound callbacks are not admin APIs. Callback URLs are unauthenticated at the JWT layer so providers can reach them, but every callback is authenticated with a provider-specific HMAC or shared secret. Admin operations require an authenticated `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` session.

Secrets live only in `/etc/mini-and-more/prod.env`. Do not paste them here.

Full payload examples and workflow criteria: [`docs/zoho-webhook-setup-and-purpose.md`](../zoho-webhook-setup-and-purpose.md).

## Production callback URLs

All HTTPS on [https://minisnmore.com](https://minisnmore.com).

Use the `/api/...` URL as the canonical provider-facing address. `/internal/...` aliases exist for compatibility and internal routing; do not configure a new provider with them.

| Provider / event | Method | Canonical URL | Route after Nginx |
| --- | --- | --- | --- |
| Zoho invoice status | POST | `/api/v1/zoho/invoice-webhook` | Direct to catalog `:9184` |
| Zoho shipment | POST | `/api/v1/zoho/shipment-webhook` | Gateway `:9180` → catalog `:9184` |
| Zoho sales-order status | POST | `/api/v1/zoho/order-status-webhook` | Gateway `:9180` → catalog `:9184` |
| Zoho item / catalog | POST | `/api/v1/zoho/item-webhook` | Gateway `:9180` → catalog `:9184` |
| Zoho stock | POST | `/api/v1/zoho/stock-webhook` | Gateway `:9180` → catalog `:9184` |
| XpressBees forward shipment | POST | `/api/v1/webhooks/xpressbees/shipments` | Gateway `:9180` → catalog `:9184` |
| XpressBees return / exchange | POST | `/api/v1/webhooks/xpressbees/returns` | Gateway `:9180` → catalog `:9184` |

Invoice is special: Nginx proxies it **straight to catalog core** so a gateway restart cannot drop paid/void events.

Admin Zoho UI (OAuth, bulk sync) uses `/api/v1/admin/zoho/` with a **600s** proxy timeout.

## Callback secrets and headers

| Callback | Environment variable | Accepted shared-secret header |
| --- | --- | --- |
| Invoice | `ZOHO_INVOICE_WEBHOOK_SECRET` | `X-Zoho-Invoice-Secret` |
| Shipment | `ZOHO_SHIPMENT_WEBHOOK_SECRET` | `X-Zoho-Shipment-Secret` |
| Order status | `ZOHO_ORDER_STATUS_WEBHOOK_SECRET` | `X-Zoho-Order-Status-Secret` |
| Item | `ZOHO_ITEM_WEBHOOK_SECRET` | `X-Zoho-Item-Secret` |
| Stock | `ZOHO_STOCK_WEBHOOK_SECRET` | `X-Zoho-Stock-Secret` |
| XpressBees shipment and return | `XPRESSBEES_WEBHOOK_SECRET` | `X-XpressBees-Secret` |

Zoho may instead send its event-specific signature header or `X-Zoho-Webhook-Signature`. XpressBees may instead send `X-Hmac-SHA256`, `X-XpressBees-Signature`, or `X-XpressBees-Webhook-Signature`. MiniMS verifies the HMAC against the exact raw request body; middleware must not reformat the JSON before verification.

Secret rules (Zoho): 12–50 characters, alphanumeric only, no punctuation.

A `401` from MiniMS means the header does not match `prod.env`. Restart catalog (and gateway for `/internal` routes) after changing secrets.

## What each Zoho webhook does

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

## XpressBees integration

### Outbound calls made by Mini’s n More

The catalog service logs in to `XPRESSBEES_BASE_URL` with the configured credentials. Admin order and return actions can:

- check courier serviceability/rates;
- create a forward shipment and store its AWB/tracking URL;
- cancel an active forward shipment;
- create a reverse pickup for an approved return or exchange; and
- record a manual carrier instead when XpressBees is not used.

The core settings are `XPRESSBEES_EMAIL`, `XPRESSBEES_PASSWORD`, `XPRESSBEES_XB_KEY`, `XPRESSBEES_WEBHOOK_SECRET`, origin/warehouse values, and pickup vendor code. Defaults and the complete non-secret key list live in `deploy/env/prod.env.example` and `backend/src/main/resources/config/application.yml`.

### Forward-shipment callback

`POST /api/v1/webhooks/xpressbees/shipments` accepts either:

- an order reference: `order_number`, `order_no`, `OrderNo`, or `reference`; or
- an AWB: `awb`, `awb_number`, `AWBNumber`, or `AirWayBillNO`.

It also requires a carrier status in `status`, `shipment_status`, `event`, or `milestone`. The handler updates the current order’s shipment status. A delayed event for an old AWB after rebooking is acknowledged as `skipped: true` and cannot overwrite the new shipment.

### Return/exchange callback

`POST /api/v1/webhooks/xpressbees/returns` accepts a return reference such as `return_number`, `order_no`, or `reference`, or finds the return by its reverse/outbound AWB. It updates reverse pickup progress and, for exchanges, outbound replacement progress. Delivered and out-for-delivery events can advance the local workflow and trigger the corresponding once-only notification.

Both handlers are idempotent. Providers should send a stable event id. When none is supplied, MiniMS derives one from the local reference, normalized status, and AWB. Replayed completed events return `200` without applying the transition twice.

## Mini’s n More admin integration operations

The admin UI is [https://minisnmore.com/admin](https://minisnmore.com/admin). These are browser/API operations initiated by an authorised administrator, not webhooks.

| Admin area | Main operations | Public API family |
| --- | --- | --- |
| Zoho | Connect OAuth, test connection, sync/pull products, pull stock, retry/sync orders, pull tracking | `/api/v1/admin/zoho/**` |
| Zoho settings | Read/update the stored integration settings | `/api/v1/admin/zoho-settings` |
| Orders | Create package, book/cancel XpressBees or manual shipment | `/api/v1/admin/orders/**` |
| Returns/exchanges | Approve/reject, book reverse pickup, receive/QC/refund, create replacement shipment | `/api/v1/admin/order-returns/**` |

Operational rules:

1. Use the UI for normal work; do not invoke admin endpoints from a provider dashboard.
2. A Zoho bulk operation can take several minutes. Nginx and the gateway allow 600 seconds for `/api/v1/admin/zoho/**`.
3. Do not repeat a button click only because the browser appears slow. Check logs and the provider/local record first.
4. Confirm package weight, dimensions, items, address, payment type, and serviceability before booking XpressBees.
5. Do not manually move an order/return past a carrier-confirmed state unless the provider dashboard and local audit trail have been reconciled.
6. Admin requests use the logged-in JWT/cookie and role checks. Callback secrets do not grant admin access.

## How to verify

```text
# Nginx saw provider callbacks
grep -Ei 'zoho|xpressbees' /var/log/nginx/access.log | tail -50

# Catalog / gateway logs
pm2 logs mini-prod-ms-catalog-core --lines 100
pm2 logs mini-prod-ms-gateway --lines 100
```

| HTTP | Meaning |
| --- | --- |
| 200 | Accepted or duplicate `eventId` |
| 400 | Bad JSON or missing required fields |
| 401 | Secret mismatch |
| 404 | No matching MiniMS order, return, AWB, or product |
| 503 | Secret missing in env, or catalog down |

Postgres tables (in `miniandmore`):

```text
zoho_invoice_webhook_event
zoho_shipment_webhook_event
zoho_order_status_webhook_event
zoho_item_webhook_event
zoho_stock_webhook_event
xpressbees_shipment_webhook_event
xpressbees_return_webhook_event
```

For a safe authentication test, send valid JSON with an intentionally invalid secret. The expected result is `401`; this proves DNS, TLS, Nginx, gateway/catalog routing, and endpoint registration without changing an order. Do not paste the real secret into shell history. Run a successful end-to-end test only with a dedicated test order/return and a provider-generated event.

## Zoho console checklist

1. API console app: production redirect `https://minisnmore.com/admin/zoho`.
2. Workflow **Actions** (webhooks) created with compact JSON payloads (placeholders must not wrap across lines).
3. Workflow **Rules** on Invoices, Shipment Orders, Sales Orders, Items.
4. Org id matches `ZOHO_ORG_ID`.
5. After OAuth, `/admin/zoho` shows connected; do not full-sync catalog during peak.

## XpressBees console checklist

1. Production account/API URL matches `XPRESSBEES_BASE_URL` and the configured authentication mode.
2. Warehouse address, origin pincode, phone, and pickup vendor code are complete.
3. Forward callback is `https://minisnmore.com/api/v1/webhooks/xpressbees/shipments`.
4. Reverse/return callback is `https://minisnmore.com/api/v1/webhooks/xpressbees/returns`.
5. The provider and `XPRESSBEES_WEBHOOK_SECRET` use the same HMAC/shared secret.
6. A test forward shipment and reverse pickup each produce an AWB, a `200` callback, and a row in the matching audit table.
