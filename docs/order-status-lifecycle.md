# Order status lifecycle

Reference for post-order tracking: delivery, returns, and final closure on Minisnmore.

The platform uses **four related status fields** on an order. They are stored separately and combined for customer-facing labels in the storefront.

---

## 1. Order status (`status`)

Canonical fulfillment state on `customer_order.status`.

| Value | Meaning |
|-------|---------|
| `PENDING` | Order created; waiting for online payment confirmation |
| `CONFIRMED` | Order accepted and ready for fulfillment |
| `HAND_WRAPPED` | A package exists for the linked sales order in Zoho |
| `FULFILLED` | Delivered / order complete |
| `CANCELLED` | Order cancelled |

### How it is set

| Payment type | On place order | After payment | After wrapping | On delivery |
|--------------|----------------|---------------|----------------|-------------|
| **Online (Razorpay)** | `PENDING` | `CONFIRMED` (after verify-payment / sync-payment) | `HAND_WRAPPED` | `FULFILLED` |
| **COD** | `CONFIRMED` immediately | stays `CONFIRMED` until collected | `HAND_WRAPPED` | `FULFILLED` |

`FULFILLED` is set only from an explicit delivery signal, such as a Zoho package converted to a shipment and marked `fulfilled`, a delivery challan marked `delivered`, or an equivalent Ledgerly delivery signal. Zoho sales-order `fulfilled` is ignored because invoice/payment processing can set it without delivery.

Online orders that never complete payment remain `PENDING` until the 30-minute inventory hold expires and the order is released/cancelled.

---

## 2. Payment status (`payment_status`)

Tracks money movement independently of shipment.

| Value | Meaning |
|-------|---------|
| `PENDING` | Not paid (online checkout incomplete, or COD not yet collected) |
| `PAID` | Payment received |
| `FAILED` | Online payment failed |
| `REFUNDED` | Refund completed (usually after an approved return) |

### COD note

COD orders are `CONFIRMED` at placement but `payment_status` stays `PENDING` until cash/UPI is collected on delivery. Admin can mark COD collected via `POST /api/v1/admin/orders/{orderNumber}/mark-cod-collected`.

---

## 3. Tracking status (`tracking_status`)

**Customer-facing** delivery progress. Not stored as a single DB enum — computed at read time by `OrderTrackingStatusResolver` and returned on order detail / public track endpoints.

| Value | Storefront label | When |
|-------|------------------|------|
| `pending` | Awaiting payment | Payment not `PAID` and order not cancelled |
| `confirmed` | Order confirmed | Accepted, but wrapping has not yet been marked complete |
| `hand_wrapped` | Hand-wrapped | A package was created for the linked Zoho sales order |
| `awaiting_pickup` | Waiting for pickup | ExpressBees AWB/label exists, but the courier has not scanned pickup yet |
| `shipped` | On the way | Courier pickup / in-transit, manual AWB, shipment date, or Zoho/Ledgerly shipped signal |
| `out_for_delivery` | Out for delivery | ExpressBees reports `OUT FOR DELIVERY` (or `OFD`) for the booked AWB |
| `delivery_failed` | Delivery attempt unsuccessful | ExpressBees reports undelivered / delivery failed (reattempt expected) |
| `delivered` | Delivered | Order `FULFILLED` or an explicit Zoho/Ledgerly delivered signal |
| `cancelled` | Cancelled | Order `CANCELLED` or cancelled in external system |

### ExpressBees → customer update

| ExpressBees status | Customer sees | Customer notification |
|--------------------|---------------|-----------------------|
| Pickup / in-transit | On the way | None for ExpressBees (manual carriers still email on ship) |
| `OUT FOR DELIVERY` | Out for delivery | Out-for-delivery email with the ExpressBees tracking link and the account order link |
| Undelivered / failed delivery | Delivery attempt unsuccessful | Delivery-failed email (once) |
| `Delivered` | Delivered | Delivered email |

The tracking link is only available once an AWB exists and always uses
`https://shipmentv2.xpressbees.com/orders/tracking/{AWB}` (override with `XPRESSBEES_TRACKING_URL_TEMPLATE`).
Pickup / in-transit scans move the tracker to "On the way" without emailing; manual (non-ExpressBees)
shipments still send the out-for-delivery email when they ship because no OFD scan is available.
Once a shipment reaches OFD, later weaker hub/in-transit scans do not regress the customer status.
Exchange replacements booked through ExpressBees follow the same OFD email rule using the outbound AWB
(webhook must deliver an OFD scan; there is no booking-time fallback for XB outbound).

### Inputs to tracking status

- `status`, `payment_status`
- `tracking_number`, `shipment_date`, `tracking_carrier`
- `shipment_logistics_mode`, `shipment_carrier_status` (ExpressBees booked ≠ shipped)
- `zoho_order_status` (from Zoho Books sales order / delivery challan pull)
- `ledgerly_order_status` (from Ledgerly tracking pull)

---

## 4. Return status (`return_status`)

Separate entity: one or more return requests per order (`order_return` table). Lifecycle is independent until refund closes the loop.

| Value | Meaning | Who moves it |
|-------|---------|--------------|
| `REQUESTED` | Customer submitted a return | Customer (`POST /api/v1/order-returns`) |
| `APPROVED` | Admin accepted the return | Admin |
| `REJECTED` | Admin declined | Admin |
| `RECEIVED` | Returned item received in warehouse | Admin |
| `REFUNDED` | Refund processed — **final closure** for the return | Admin (triggers Razorpay refund + Ledgerly credit note) |
| `FAILED` | Refund or downstream sync failed | System / admin retry |
| `CANCELLED` | Customer withdrew the request | Customer |

### Return eligibility rules

- Order must be **`payment_status = PAID`**
- Order must **not** be `CANCELLED`
- Order must be **delivered** (`shipment_delivered_at` is set; dispatch `shipment_date` does not count)
- Within **3 days** of that physical delivery timestamp (`RETURN_WINDOW_DAYS`)

Check: `GET /api/v1/order-returns/eligibility/{orderNumber}`

### Admin return flow

```
REQUESTED → APPROVED → RECEIVED → REFUNDED
                ↘ REJECTED
REQUESTED → CANCELLED (customer)
```

On `REFUNDED`:

- `payment_status` on the order → `REFUNDED`
- Razorpay refund issued (online orders)
- Ledgerly credit note created
- Local stock restored for returned line items

---

## End-to-end happy path

```text
Place order
  status: PENDING (online) or CONFIRMED (COD)
  payment_status: PENDING
  tracking_status: pending / confirmed

Payment confirmed (online) or COD placed
  status: CONFIRMED
  payment_status: PAID (online) or PENDING (COD until collection)

Hand-wrapped (Zoho package created)
  status: HAND_WRAPPED
  tracking_status: hand_wrapped

ExpressBees label booked
  tracking_number set
  tracking_status: awaiting_pickup  (not yet "On the way")

Courier pickup / in-transit
  tracking_status: shipped
  (+ shipment_date from pickup scan, manual AWB, Zoho, or Ledgerly)

ExpressBees OUT FOR DELIVERY
  tracking_status: out_for_delivery
  (out-for-delivery email sent with https://shipmentv2.xpressbees.com/orders/tracking/{AWB})

Delivered
  status: FULFILLED
  tracking_status: delivered
  (a Zoho shipment marked fulfilled is sufficient; invoice/payment fulfillment is not)

(Optional, within 7 days)
  return_status: REQUESTED → APPROVED → RECEIVED → REFUNDED
  payment_status: REFUNDED
```

---

## Customer vs admin vs operations

| Audience | Primary fields |
|----------|----------------|
| **Customer** (account, track page) | `tracking_status`, `payment_status`, `status` (summarised in UI) |
| **Admin** (orders, COD pending, returns) | All of the above + return workflow |
| **Operations / integrations** | Zoho sync status, Ledgerly sync status, raw Zoho/Ledgerly order status strings |

Integration sync fields (`zoho_sync_status`, `ledgerly_sync_status`, etc.) are **back-office only** — they do not appear as customer order statuses.

---

## API surfaces (reference)

| Purpose | Endpoint |
|---------|----------|
| Create order | `POST /api/v1/orders` |
| Verify / sync payment | `POST /api/v1/orders/verify-payment`, `sync-payment` |
| List / detail orders | `GET /api/v1/orders`, `GET /api/v1/orders/{orderNumber}` |
| Public tracking | `GET /api/v1/orders/track/{orderNumber}` |
| Request return | `POST /api/v1/order-returns` |
| Return eligibility | `GET /api/v1/order-returns/eligibility/{orderNumber}` |
| Admin returns | `GET/POST /api/v1/admin/order-returns/...` |
| Mark COD collected | `POST /api/v1/admin/orders/{orderNumber}/mark-cod-collected` |

Order and return JSON uses **snake_case** keys (`order_number`, `payment_status`, `tracking_status`, etc.). Full payload reference: [order-return-json-contract.md](./order-return-json-contract.md).

---

## Source of truth in code

| Concept | Location |
|---------|----------|
| Order status enum | `backend/.../enumeration/OrderStatus.java` |
| Payment status enum | `backend/.../enumeration/PaymentStatus.java` |
| Return status enum | `backend/.../enumeration/OrderReturnStatus.java` |
| Tracking status resolver | `backend/.../service/OrderTrackingStatusResolver.java` |
| Return rules & workflow | `backend/.../service/OrderReturnService.java` |
| Storefront labels | `src/lib/order-status.ts` |
