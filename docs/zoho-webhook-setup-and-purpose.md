# Zoho Webhooks Setup and Purpose

Last updated: 2026-09-02

This document explains how Mini's n More uses Zoho webhooks, what each webhook is for, what to configure in Zoho Workflow Actions and Workflow Rules, and how this differs from the app's own outbound Zoho API calls.

Do not commit real webhook secrets in this file. Use the values configured in the server environment, such as `/etc/mini-and-more/uat.env` on UAT.

## Mental Model

A webhook is an event-driven HTTP POST from Zoho to MiniMS.

MiniMS has receiver endpoints in the backend, for example:

```text
POST /api/v1/zoho/invoice-webhook
POST /internal/v1/zoho/shipment-webhook
```

Those endpoints do nothing by themselves until Zoho is configured to call them. The setup has two sides:

| Side | What happens |
| --- | --- |
| MiniMS backend | Provides webhook receiver APIs, validates secret/signature, deduplicates `eventId`, updates local DB |
| Zoho | Workflow Rule detects an event, Workflow Action sends an HTTP POST to MiniMS |

So yes, webhooks are only for event-driven pushing. We are "creating webhooks" in Zoho because Zoho needs to know which MiniMS URL to call when a Zoho event happens. MiniMS is not polling in that path.

This is separate from outbound API sync:

| Direction | Trigger | Example |
| --- | --- | --- |
| MiniMS -> Zoho | Checkout/admin/scheduler/manual action | Create contact, sales order, invoice, stock adjustment |
| Zoho -> MiniMS | Zoho workflow webhook | Invoice paid, shipment updated, sales order status changed |

## Security

Zoho supports a secured webhook secret. Zoho may send a default signature header:

```text
X-Zoho-Webhook-Signature
```

MiniMS also accepts simpler shared-secret headers per webhook:

```text
X-Zoho-Invoice-Secret
X-Zoho-Shipment-Secret
X-Zoho-Order-Status-Secret
X-Zoho-Item-Secret
```

Secret rules:

```text
Length: 12-50 characters
Allowed: alphanumeric only
Avoid: spaces, hyphens, underscores, punctuation
```

The value entered in Zoho must exactly match the matching env var on the server.

| Webhook | Server env var |
| --- | --- |
| Invoice | `ZOHO_INVOICE_WEBHOOK_SECRET` |
| Shipment | `ZOHO_SHIPMENT_WEBHOOK_SECRET` |
| Sales order status | `ZOHO_ORDER_STATUS_WEBHOOK_SECRET` |
| Item/catalog | `ZOHO_ITEM_WEBHOOK_SECRET` |
| Stock | `ZOHO_STOCK_WEBHOOK_SECRET` |

## Invoice Status Webhook

Purpose: update MiniMS when a Zoho invoice changes state. This is used for paid/sent/void updates, local order lifecycle changes, invoice ID updates, and invoice PDF cache opportunities.

Create in Zoho:

```text
Webhook name: MNM Invoice Status Webhook
Module: Invoices
Method: POST
UAT URL: https://uat.minisnmore.com/api/v1/zoho/invoice-webhook
Production URL: https://minisnmore.com/api/v1/zoho/invoice-webhook
Content-Type: application/json
Secure this webhook: Yes
Custom header name: X-Zoho-Invoice-Secret
Custom header value: same value as ZOHO_INVOICE_WEBHOOK_SECRET
```

Use compact JSON to avoid Zoho editor line-wrap problems:

```json
{"eventId":"${INVOICE.INVOICE_ID}${INVOICE.STATUS}${INVOICE.LAST_MODIFIED_TIME}","invoice":{"invoice_id":"${INVOICE.INVOICE_ID}","invoice_number":"${INVOICE.INVOICE_NUMBER}","salesorder_id":"${INVOICE.SALESORDER_ID}","reference_number":"${INVOICE.REFERENCE_NUMBER}","status":"${INVOICE.STATUS}","last_modified_time":"${INVOICE.LAST_MODIFIED_TIME}"}}
```

Workflow rule:

```text
Rule name: MNM Send Invoice Status to Website
Module: Invoices
Workflow type: Event Based
Trigger: Created or Edited
Execute when: Status is changed
```

Criteria must use OR:

```text
Status is Paid
OR Status is Sent
OR Status is Void
OR Status is Voided
```

Do not use `Paid AND Sent AND Void`. One invoice cannot have all statuses at once, so that rule will never fire.

## Shipment Webhook

Purpose: update MiniMS tracking fields and delivery state when Zoho Inventory shipment/order shipment changes. This is the primary webhook path for courier/tracking updates when polling is disabled.

Create in Zoho:

```text
Webhook name: MiniMS Shipment Webhook
Module: Shipment Orders
Method: POST
UAT URL: https://uat.minisnmore.com/internal/v1/zoho/shipment-webhook
Production URL: https://minisnmore.com/internal/v1/zoho/shipment-webhook
Content-Type: application/json
Secure this webhook: Yes
Custom header name: X-Zoho-Shipment-Secret
Custom header value: same value as ZOHO_SHIPMENT_WEBHOOK_SECRET
```

Zoho's default preview may look like this:

```json
{"shipment_order":{"shipment_order_id":"...","created_time":"..."}}
```

That default shape is not enough for MiniMS because the backend must know which local order to update. Include `salesorder_id` or `salesorder_number`.

Use this compact JSON:

```json
{"eventId":"${SHIPMENT_ORDER.SHIPMENT_ORDER_ID}${SHIPMENT_ORDER.STATUS}${SHIPMENT_ORDER.LAST_MODIFIED_TIME}","shipment_order":{"shipment_order_id":"${SHIPMENT_ORDER.SHIPMENT_ORDER_ID}","salesorder_id":"${SHIPMENT_ORDER.SALESORDER_ID}","salesorder_number":"${SHIPMENT_ORDER.SALESORDER_NUMBER}","tracking_number":"${SHIPMENT_ORDER.TRACKING_NUMBER}","reference_number":"${SHIPMENT_ORDER.REFERENCE_NUMBER}","carrier":"${SHIPMENT_ORDER.CARRIER}","delivery_method":"${SHIPMENT_ORDER.DELIVERY_METHOD}","shipment_date":"${SHIPMENT_ORDER.DATE}","status":"${SHIPMENT_ORDER.STATUS}","detailed_status":"${SHIPMENT_ORDER.DETAILED_STATUS}","status_message":"${SHIPMENT_ORDER.STATUS_MESSAGE}","last_modified_time":"${SHIPMENT_ORDER.LAST_MODIFIED_TIME}"}}
```

If Zoho rejects a placeholder, remove only that field or reinsert it from Zoho's placeholder picker. Keep the JSON keys unchanged.

Unresolved `${SHIPMENT_ORDER.*}` placeholders and empty strings are treated as blank. `shipment_order_id` identifies the webhook; it does not by itself stamp a shipment date. `reference_number` is not used as an AWB when it matches the sales order number.

Required fields for MiniMS:

```text
eventId
shipment_order.shipment_order_id
shipment_order.salesorder_id OR shipment_order.salesorder_number
shipment_order.status OR tracking_number OR carrier OR delivery_method OR shipment_date OR shipment_order_id
```

Use three Zoho Inventory workflow rules on module **Shipment Order**, all pointing at the same MiniMS Shipment Webhook:

```text
1) Package created
   Trigger: shipment order is created
   Criterion: Package Slip# is not empty
   MiniMS: HAND_WRAPPED when the payload is not yet Shipped and has no AWB/date

2) Ontheway
   Trigger: shipment order is created
   Criterion: Shipment Status is Shipped
   MiniMS: tracking_status shipped (on the way); not delivered

3) deliverd
   Trigger: shipment order is edited / Shipment Status updated
   Criterion: Shipment Status is Fulfilled (Zoho often has no Delivered option here)
   MiniMS: FULFILLED — shipment webhook status Fulfilled/Delivered is enough; no custom field
```

Do **not** use a MiniMS custom field to fake delivery.

- Package / create shipment as **Shipped** → on the way only.
- Edit that shipment until Zoho shows **Fulfilled** or **Delivered** → MiniMS delivered.
- Live Zoho "Mark as Delivered" often sends a **blank** `STATUS` in the webhook even though the sales order GET has `packages[].status = delivered` and `shipment_delivered_date`. MiniMS now reads that sales-order package when the webhook status is empty.
- A later sales-order sync (invoiced/fulfilled) does **not** undo a shipment that MiniMS already marked delivered.
- Sales-order `fulfilled` from invoicing is still ignored and does not mark the store order delivered.

## Sales Order Status Webhook

Purpose: update MiniMS when a Zoho sales order status changes. This is useful for accounting/order state changes such as invoiced or voided, but it is not the best source for courier tracking.

Create in Zoho:

```text
Webhook name: MiniMS Sales Order Status Webhook
Module: Sales Orders
Method: POST
UAT URL: https://uat.minisnmore.com/internal/v1/zoho/order-status-webhook
Production URL: https://minisnmore.com/internal/v1/zoho/order-status-webhook
Content-Type: application/json
Secure this webhook: Yes
Custom header name: X-Zoho-Order-Status-Secret
Custom header value: same value as ZOHO_ORDER_STATUS_WEBHOOK_SECRET
```

Payload:

```json
{"eventId":"${SALESORDER.SALESORDER_ID}${SALESORDER.STATUS}${SALESORDER.LAST_MODIFIED_TIME}","salesorder":{"salesorder_id":"${SALESORDER.SALESORDER_ID}","salesorder_number":"${SALESORDER.SALESORDER_NUMBER}","status":"${SALESORDER.STATUS}","reference_number":"${SALESORDER.REFERENCE_NUMBER}","last_modified_time":"${SALESORDER.LAST_MODIFIED_TIME}"}}
```

Typical criteria:

```text
Status is Invoiced
OR Status is Void
OR Status is Cancelled
OR Status is Fulfilled
```

Important: MiniMS treats sales order `fulfilled` carefully because Zoho can mark accounting documents as fulfilled without proving delivery. Shipment/order-shipment events are preferred for real delivery status.

## Item/Catalog Webhook

Purpose: update one MiniMS product when a Zoho item changes, instead of doing a full catalog pull.

Create in Zoho:

```text
Webhook name: MiniMS Item Webhook
Module: Items
Method: POST
UAT URL: https://uat.minisnmore.com/internal/v1/zoho/item-webhook
Production URL: https://minisnmore.com/internal/v1/zoho/item-webhook
Content-Type: application/json
Secure this webhook: Yes
Custom header name: X-Zoho-Item-Secret
Custom header value: same value as ZOHO_ITEM_WEBHOOK_SECRET
```

Payload:

```json
{"eventId":"${ITEM.ITEM_ID}${ITEM.LAST_MODIFIED_TIME}","item":{"item_id":"${ITEM.ITEM_ID}","sku":"${ITEM.SKU}","name":"${ITEM.NAME}","status":"${ITEM.STATUS}","rate":"${ITEM.RATE}","stock_on_hand":"${ITEM.STOCK_ON_HAND}","last_modified_time":"${ITEM.LAST_MODIFIED_TIME}"}}
```

## Stock Webhook

Purpose: reconcile MiniMS stock for one SKU from Zoho. This should send absolute stock, not a delta.

Create in Zoho:

```text
Webhook name: MiniMS Stock Webhook
Module: Items or Inventory adjustment source
Method: POST
UAT URL: https://uat.minisnmore.com/api/v1/zoho/stock-webhook
Production URL: https://minisnmore.com/api/v1/zoho/stock-webhook
Content-Type: application/json
Secure this webhook: Yes
Custom header name: X-Zoho-Stock-Secret
Custom header value: same value as ZOHO_STOCK_WEBHOOK_SECRET
```

Payload:

```json
{"eventId":"${ITEM.ITEM_ID}${ITEM.STOCK_ON_HAND}${ITEM.LAST_MODIFIED_TIME}","sku":"${ITEM.SKU}","stock":"${ITEM.STOCK_ON_HAND}","zoho_item_id":"${ITEM.ITEM_ID}","last_modified_time":"${ITEM.LAST_MODIFIED_TIME}"}
```

For variant products, `sku` is required so MiniMS can update the correct variant row.

## Payload Rules

Use compact one-line JSON if Zoho says "syntax of JSON payload is incorrect". The most common cause is a placeholder split across lines.

Invalid:

```text
"${SHIPMENT_ORDER.SALESORDE
R_ID}"
```

Valid:

```text
"${SHIPMENT_ORDER.SALESORDER_ID}"
```

General rules:

```text
Every JSON key must be inside double quotes.
Every placeholder value should be inside double quotes.
Do not split placeholders across lines.
Do not leave trailing commas.
Use Zoho's placeholder picker when a placeholder name is rejected.
```

## UAT Testing Notes

For shipment webhook testing, UAT can disable Zoho tracking pulls so webhook behavior is visible in logs:

```text
ZOHO_AUTO_SYNC_ENABLED=false
ZOHO_ORDER_TRACKING_ENABLED=false
```

`ZOHO_AUTO_SYNC_ENABLED=false` disables scheduled Zoho retry/tracking jobs.

`ZOHO_ORDER_TRACKING_ENABLED=false` disables Zoho tracking refresh from the authenticated order/account page as well. That means viewing an order should not silently pull tracking from Zoho and mask whether the shipment webhook worked.

Manual admin actions can still call Zoho if an admin explicitly clicks a sync or pull button.

## How to Verify

Nginx access log shows whether Zoho reached MiniMS:

```text
POST /api/v1/zoho/invoice-webhook
POST /internal/v1/zoho/shipment-webhook
POST /internal/v1/zoho/order-status-webhook
POST /internal/v1/zoho/item-webhook
POST /api/v1/zoho/stock-webhook
```

Response meanings:

| HTTP status | Meaning |
| --- | --- |
| `200` | Webhook was accepted and processed, or duplicate event was ignored safely |
| `400` | Payload is missing required fields or invalid JSON/body mapping |
| `401` | Secret/signature mismatch or missing auth header |
| `404` | Auth and payload passed, but no matching MiniMS order/product was found |
| `503` | Required server secret/config missing or backend unavailable |

Database event tables show successful processed webhook events:

```text
zoho_invoice_webhook_event
zoho_shipment_webhook_event
zoho_order_status_webhook_event
zoho_item_webhook_event
zoho_stock_webhook_event
```

If a webhook returns `400`, check the Zoho workflow execution history body and compare it to the required fields above.

## Current UAT Findings from 2026-09-01

Invoice webhook:

```text
Initial failures were server-side:
- 503 before env was loaded
- 401 while nginx routed through gateway and the header was not reaching catalog core

Fixes applied:
- invoice webhook secret loaded in UAT env
- missing invoice webhook event table created
- nginx exact route points invoice webhook directly to catalog core
- catalog core restarted
```

Shipment webhook:

```text
Two Zoho shipment webhook calls returned 400 because the default payload did not include enough shipment/order fields.
Tracking still updated because the fallback Zoho tracking pull ran from the order view path.
UAT tracking pulls are now disabled with ZOHO_ORDER_TRACKING_ENABLED=false for cleaner webhook testing.
```

Order `MNM-443B4DB2DCE3`:

```text
Order state updated correctly.
Shipment webhook did not record a successful event.
Use the adjusted shipment_order payload above for the next test.
```

