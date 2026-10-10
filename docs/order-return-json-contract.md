# Order & return JSON contract

Snake_case payloads for commerce endpoints under `/api/v1/*`.  
For status transitions and business rules, see [order-status-lifecycle.md](./order-status-lifecycle.md).  


All responses use the catalog v1 envelope:

```json
{ "success": true, "data": <payload>, "meta": { "requestId": "...", "timestamp": "..." } }
```

---

## Naming conventions

| Area | JSON key style |
|------|----------------|
| Orders, returns, profile, addresses, coupons (validate) | **snake_case** |
| CMS admin DTOs (products, categories, …) | **camelCase** — mapped in `backend-data.ts` |
| Create-order / Razorpay checkout responses | **mixed camelCase** — see [Payment checkout responses](#payment-checkout-responses) |

Enum-like status fields are returned as **lowercase strings** in JSON (e.g. `"confirmed"`, `"paid"`), not `CONFIRMED`.

---

## Profile

### `GET /api/v1/account/profile`

```json
{
  "full_name": "Jane Doe",
  "phone": "9876543210"
}
```

### `PUT /api/v1/account/profile`

**Request / response:** same shape as GET.

| Field | Constraints |
|-------|-------------|
| `full_name` | 2–120 chars; letters, spaces, `'`, `.`, `-` |
| `phone` | Valid 10-digit Indian mobile (optional `+91` prefix) |

---

## Orders — create

### `POST /api/v1/orders`

**Request:**

```json
{
  "items": [
    {
      "product_id": "42",
      "product_slug": "soft-cotton-jabla",
      "product_name": "Soft Cotton Jabla",
      "product_image": "https://cdn.example/img.jpg",
      "size": "0-3M",
      "color": "Pink",
      "sku": "SKU-001",
      "qty": 1,
      "price": 599.00
    }
  ],
  "shipping_address": {
    "full_name": "Jane Doe",
    "email": "jane@example.com",
    "phone": "9876543210",
    "line1": "12 Rose Lane",
    "line2": "",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001",
    "baby_name": "Arya"
  },
  "shipping_mode": "standard",
  "payment_method": "online",
  "coupon_code": "WELCOME10",
  "donation": 0,
  "notes": "Gift wrap please"
}
```

| Field | Notes |
|-------|-------|
| `payment_method` | `"online"` or `"cod"` (optional; defaults to online) |
| `shipping_mode` | e.g. `"standard"`, `"express"` |
| `donation` | Required number ≥ 0 |
| `items` | 1–50 line items; cart is client-only — full list sent at checkout |

### Payment checkout responses

Create-order responses use **camelCase** for Razorpay integration fields. `totals` inside the response uses snake_case for fee keys.

**COD example (`data`):**

```json
{
  "orderId": 123,
  "orderNumber": "MNM-20260818-ABC",
  "paymentMethod": "cod",
  "cod": true,
  "amount": 64800,
  "currency": "INR",
  "totals": {
    "subtotal": 599,
    "discount": 0,
    "shipping": 0,
    "cod_fee": 49,
    "tax": 0,
    "total": 648
  }
}
```

**Online (Razorpay) — additional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `razorpayOrderId` | string | Razorpay order id for checkout |
| `keyId` | string | Razorpay publishable key |
| `demoMode` | boolean | Demo / test gateway |
| `gatewayMode` | string | Gateway configuration hint |

`amount` is in **paise** (integer). `totals.total` is in **INR** (decimal).

### `POST /api/v1/orders/retry-payment`

**Request:** `{ "orderId": 123 }`

**Response:** same shape as online create-order (without `totals`); reopens the existing Razorpay session.

---

## Orders — payment confirmation

### `POST /api/v1/orders/verify-payment`

**Request (mixed — `orderId` camelCase, Razorpay ids snake_case):**

```json
{
  "orderId": 123,
  "razorpay_order_id": "order_xxx",
  "razorpay_payment_id": "pay_xxx",
  "razorpay_signature": "..."
}
```

**Response (`data`):**

```json
{
  "ok": true,
  "orderNumber": "MNM-20260818-ABC",
  "orderId": 123
}
```

### `POST /api/v1/orders/sync-payment`

**Request:** `{ "orderId": 123 }`

**Response (`data`):** verify-payment fields plus:

```json
{
  "paid": true,
  "payment_status": "paid"
}
```

---

## Orders — read

### `GET /api/v1/orders`

**Response:** `{ "orders": [ <order_summary>, ... ] }`

### `GET /api/v1/orders/{orderNumber}`

**Response:** `{ "order": <order_detail> }`

### Order summary (`orders[]`, base fields)

| Field | Type | Description |
|-------|------|-------------|
| `id` | number | Internal order id |
| `order_number` | string | Public order number (`MNM-…`) |
| `status` | string | `pending`, `confirmed`, `hand_wrapped`, `fulfilled`, `cancelled` |
| `payment_status` | string | `pending`, `paid`, `failed`, `refunded` |
| `payment_method` | string | `online`, `cod` |
| `total` | number | Order total (INR) |
| `created_at` | string | ISO-8601 instant |
| `order_items` | array | Line items — see below |
| `tracking_status` | string | `pending`, `confirmed`, `hand_wrapped`, `awaiting_pickup`, `shipped`, `out_for_delivery`, `delivered`, `cancelled` |
| `tracking_number` | string | Carrier tracking id (may be empty) |
| `tracking_carrier` | string | Carrier name (may be empty) |
| `shipment_date` | string | Dispatch date `YYYY-MM-DD` or empty. This is **not** the return-window clock. |
| `shipment_delivered_at` | string | Physical delivery instant (ISO-8601) or empty. Return eligibility uses this field. |

### Order detail (additional fields)

| Field | Type |
|-------|------|
| `subtotal`, `discount`, `shipping`, `cod_fee`, `tax`, `donation` | number |
| `coupon_code` | string \| null |
| `shipping_address` | object (same keys as create request) |
| `notes` | string \| null |
| `razorpay_order_id`, `razorpay_payment_id` | string \| null |

### `order_items[]`

| Field | Type |
|-------|------|
| `id` | number |
| `product_id`, `product_slug`, `product_name`, `product_image` | string |
| `size`, `color`, `sku` | string |
| `qty` | number |
| `price` | number |

### `GET /api/v1/orders/track/{orderNumber}` (public, no JWT)

**Response:** limited tracking only — no totals, line items, or internal ids.

```json
{
  "order": {
    "order_number": "MNM-20260818-ABC",
    "status": "confirmed",
    "tracking_status": "confirmed",
    "tracking_number": "",
    "tracking_carrier": "",
    "tracking_url": "",
    "shipment_date": "",
    "shipment_delivered_at": ""
  }
}
```

---

## Payments history

### `GET /api/v1/payments`

**Response:** `{ "payments": [ ... ] }`

| Field | Type |
|-------|------|
| `id` | number |
| `order_number` | string |
| `payment_status` | string |
| `total` | number |
| `created_at` | string (ISO-8601) |
| `razorpay_payment_id` | string \| null |

---

## Returns — customer

### `GET /api/v1/order-returns`

**Response:** array of return objects (not wrapped in `{ returns: … }`).

### `POST /api/v1/order-returns`

**Request:**

```json
{
  "order_number": "MNM-20260818-ABC",
  "reason": "Product arrived damaged in transit",
  "image_urls": ["https://cdn.example.com/my-bucket/returns/20260930-abc.jpg"]
}
```

| Field | Constraints |
|-------|-------------|
| `reason` | 10–4000 characters |
| `image_urls` | Optional. Up to **5** absolute `http`/`https` URLs (max **600** chars each). When storage is configured, each URL must be a photo uploaded via `POST /api/v1/order-returns/uploads` (object key under `returns/`). |

### `POST /api/v1/order-returns/exchange`

Same optional `image_urls` as create return (see above), plus `order_item_id`, `replacement_size`, and `replacement_color`.

### `POST /api/v1/order-returns/uploads`

Multipart upload for return/exchange photos. **Auth required.**

| Part | Value |
|------|--------|
| `file` | Image file (jpeg/png/webp/gif; size limits enforced server-side) |

**Response `data`:**

```json
{ "publicUrl": "https://cdn.example.com/my-bucket/returns/20260930-abc.jpg" }
```

Use `publicUrl` values in `image_urls` when creating a return or exchange.

### `POST /api/v1/order-returns/{returnNumber}/cancel`

**Response:** updated return object.

### Return object (`data` or list item)

| Field | Type |
|-------|------|
| `id` | number |
| `return_number` | string | e.g. `RET-MNM-…` |
| `status` | string | `requested`, `approved`, `rejected`, `received`, `refunded`, `failed`, `cancelled` |
| `reason` | string |
| `image_urls` | string[] \| omitted when empty |
| `rejection_reason` | string \| null |
| `refund_amount` | number \| null |
| `requested_at`, `resolved_at` | string (ISO-8601) \| null |
| `order_number` | string |
| `order_total` | number |

### `GET /api/v1/order-returns/eligibility/{orderNumber}`

**Eligible:**

```json
{
  "order_number": "MNM-20260818-ABC",
  "enabled": true,
  "window_days": 3,
  "tracking_status": "delivered",
  "delivered_at": "2026-09-29T09:57:19.311890Z",
  "return_deadline": "2026-10-02T09:57:19.311890Z",
  "eligible": true
}
```

**Not eligible:**

```json
{
  "order_number": "MNM-20260818-ABC",
  "enabled": true,
  "window_days": 3,
  "tracking_status": "delivered",
  "delivered_at": "2026-09-20T10:00:00Z",
  "return_deadline": "2026-09-23T10:00:00Z",
  "eligible": false,
  "message": "Return window has expired (3 days from delivery)"
}
```

---

## Returns — admin

Base path: `/api/v1/admin/order-returns`

### `GET /api/v1/admin/order-returns?status=requested`

Optional `status` filter — same lowercase values as customer return `status`.

### Admin return object (extra fields vs customer)

| Field | Type |
|-------|------|
| `admin_notes` | string \| null |
| `razorpay_refund_id` | string \| null |
| `ledgerly_credit_note_id` | string \| null |
| `order_id` | number |
| `customer_login` | string |

### `POST .../{returnNumber}/approve`

### `POST .../{returnNumber}/mark-received`

### `POST .../{returnNumber}/refund`

Optional body: `{ "admin_notes": "optional note" }` (max 2000 chars).

### `POST .../{returnNumber}/reject`

**Request:**

```json
{
  "rejection_reason": "Outside return window",
  "admin_notes": "optional"
}
```

`rejection_reason`: 5–1000 characters.

---

## Coupon validate (checkout)

### `POST /api/v1/coupons/validate`

**Request:** `{ "code": "WELCOME10", "subtotal": 599 }`

**Success (`data`):** `{ "ok": true, "code": "WELCOME10", "discount": 59.90 }`

**Failure (`data`):** `{ "ok": false, "error": "Invalid or expired coupon" }`

---

## Source of truth in code

| Resource | Java class |
|----------|------------|
| Orders, profile, coupons, payments | `backend/.../InternalAccountOrderResource.java` |
| Customer returns | `backend/.../InternalOrderReturnResource.java` |
| Admin returns | `backend/.../InternalAdminOrderReturnResource.java` |
| Tracking status computation | `backend/.../OrderTrackingStatusResolver.java` |
