# Mini’s n More — Client confirmation and implementation gaps

**Document version:** 1.0  
**Date:** 14 October 2026  
**Prepared for:** Mini’s n More business and operations team  
**Source of truth:** Application codebase and UAT site (`https://uat.minisnmore.com`) audited for this handover.

This companion document lists **unresolved business policies**, **capabilities that need live confirmation**, and **known product limitations**. It is intended for sign-off before the operations manual is treated as complete.

---

## 1. Summary

| Category | Count (approx.) |
|----------|-----------------|
| Client confirmation required | 18 |
| Verified limitation / missing admin UI | 12 |
| Environment-specific (UAT vs production) | 4 |
| Recommended live demonstration | 8 |

---

## 2. Client confirmation required

Each item includes: the question, why it matters, what the code does today, and who should confirm.

### 2.1 Return eligibility window

| Field | Detail |
|-------|--------|
| **Question** | How many days after **physical delivery** may a customer request a return or exchange? |
| **Why it matters** | Wrong window causes rejected customer requests or policy disputes. |
| **Verified behaviour** | Default **3 days** (`DEFAULT_RETURN_WINDOW_DAYS`). Admins can change **Return window (days)** on **Site settings** (`/admin/settings`), up to 90 days. Eligibility also requires paid, non-cancelled order and no open return/exchange. The window starts from **`shipment_delivered_at` only**. Dispatch `shipment_date` and Zoho/Ledgerly pull times are not used. If delivery has not been recorded, the customer is not eligible yet. |
| **Confirm with** | Operations lead (policy length only) |

### 2.2 Refund processing timeline (customer-facing)

| Field | Detail |
|-------|--------|
| **Question** | What refund timeline do you promise customers (business days)? |
| **Why it matters** | Site settings store `refund_min_business_days` and `refund_max_business_days` for display/policy text—not automatic Razorpay SLA. |
| **Verified behaviour** | Defaults exist in admin settings; actual Razorpay refund speed depends on bank/network. |
| **Confirm with** | Finance + customer support |

### 2.3 Cancellation before dispatch

| Field | Detail |
|-------|--------|
| **Question** | May customers cancel after the order is **hand-wrapped** but before a courier AWB is booked? |
| **Why it matters** | Drives warehouse workload and customer expectations. |
| **Verified behaviour** | Customer **cancellation requests** are allowed until tracking reaches **awaiting_pickup** (AWB booked). Packing/hand-wrap alone does not block cancellation. Admin approval performs real cancellation. |
| **Confirm with** | Operations + warehouse |

### 2.4 Cancellation after AWB booked

| Field | Detail |
|-------|--------|
| **Question** | What is the customer message when cancellation is no longer allowed? |
| **Verified behaviour** | Once tracking is **awaiting_pickup**, **shipped**, **out_for_delivery**, **delivery_failed**, or **delivered**, cancellation is blocked; customer must use **returns** if eligible. |
| **Confirm with** | Customer support |

### 2.5 COD refund method

| Field | Detail |
|-------|--------|
| **Question** | For cancelled or returned COD orders where cash was collected, who approves refunds and which methods are allowed? |
| **Verified behaviour** | Admin records refund via **Cancellations** or return refund flows: method (UPI, bank transfer, cash, other) + reference (min 4 characters). |
| **Confirm with** | Finance |

### 2.6 Exchange price difference

| Field | Detail |
|-------|--------|
| **Question** | Who may **waive** exchange price differences, and under what circumstances? |
| **Verified behaviour** | System supports COLLECT, REFUND, NOT_REQUIRED, WAIVED, and PENDING settlement; outbound booking blocked while PENDING. |
| **Confirm with** | Operations manager |

### 2.7 Inventory source of truth

| Field | Detail |
|-------|--------|
| **Question** | For day-to-day selling, is **website stock**, **Zoho Books**, or **Ledgerly** authoritative? |
| **Verified behaviour** | Storefront reserves and sells from **website database stock**. Zoho **stock webhook** and **Pull inventory** overwrite local quantities per SKU. Ledgerly alignment docs state Ledgerly can be source of truth when that integration is active—confirm which environment uses Ledgerly vs Zoho-only. |
| **Confirm with** | Technical lead + finance |

### 2.8 Product catalog source of truth

| Field | Detail |
|-------|--------|
| **Question** | Are new products created in **Admin → Products** first, or in **Zoho Books** first? |
| **Verified behaviour** | Both directions supported: admin create + **Push to Zoho**, or **Pull from Zoho**. Bulk pull can deactivate SKUs missing from Zoho on **full** pull only. |
| **Confirm with** | Merchandising + operations |

### 2.9 Peak-traffic Zoho jobs

| Field | Detail |
|-------|--------|
| **Question** | When may operations run **Pull all products**, **Sync all products**, or **Pull inventory**? |
| **Verified behaviour** | Long-running (minutes), high API usage; nginx timeout 600s on admin Zoho routes. |
| **Confirm with** | Operations + technical team |

### 2.9 Ledgerly vs Zoho for orders

| Field | Detail |
|-------|--------|
| **Question** | Are live orders synced to **Zoho Books only**, **Ledgerly only**, or **both**? |
| **Verified behaviour** | Code references both `ZohoSyncService` and `LedgerlyIntegrationClient` for orders, cancellations, credit notes, and tracking. Exact routing depends on deployment flags and org setup—not visible in admin UI alone. |
| **Confirm with** | Technical team (mandatory before reconciliation SOP) |

### 2.10 Return shipping cost

| Field | Detail |
|-------|--------|
| **Question** | Who pays for reverse pickup (customer vs brand)? |
| **Verified behaviour** | System books reverse logistics (ExpressBees or manual); no automatic fee charge to customer card in code audit. |
| **Confirm with** | Policy owner |

### 2.11 Partial returns

| Field | Detail |
|-------|--------|
| **Question** | Are partial line quantities and partial acceptance after inspection allowed? |
| **Verified behaviour** | Supported in return workflow (proportional refund). |
| **Confirm with** | Warehouse QC |

### 2.12 Admin-initiated order cancel

| Field | Detail |
|-------|--------|
| **Question** | Who may use **Cancel order** on the Orders screen vs only the **Cancellations** desk? |
| **Verified behaviour** | Orders workspace exposes admin cancel when `can_admin_cancel` / `can_cancel` flags allow; separate from customer cancellation **request** workflow. |
| **Confirm with** | Operations lead |

### 2.13 Shipment cancel vs order cancel

| Field | Detail |
|-------|--------|
| **Question** | Train staff that **Cancel shipment** does not refund the customer. |
| **Verified behaviour** | Documented in technical guide: voids AWB, order stays active. |
| **Confirm with** | Training lead |

### 2.14 Staff role assignments

| Field | Detail |
|-------|--------|
| **Question** | Which real people hold Super Admin, Admin, Operations, Sales, Customer Support? |
| **Verified behaviour** | RBAC at `/admin/rbac`; Super Admin can assign all roles; Admin can assign Sales, Operations, Customer Support only. |
| **Confirm with** | Business owner |

### 2.15 M2 post-order UI flag

| Field | Detail |
|-------|--------|
| **Question** | Should **Cancellations** and **Returns & exchanges** appear on UAT/production? |
| **Verified behaviour** | Controlled by build flag `VITE_SHOW_M2_POST_ORDER_UI` (defaults **on**). When off, nav entries hidden but code remains. |
| **Confirm with** | Technical team per environment |

### 2.16 OAuth and Zoho redirect URLs

| Field | Detail |
|-------|--------|
| **Question** | Production OAuth redirect is documented as `https://minisnmore.com/admin/zoho`. Is UAT `https://uat.minisnmore.com/admin/zoho` registered in Zoho console? |
| **Verified behaviour** | Mismatch breaks Connect to Zoho Books. |
| **Confirm with** | Zoho administrator |

### 2.17 Escalation contacts

| Field | Detail |
|-------|--------|
| **Question** | Named contacts and response times for payment, Zoho, hosting, and courier failures. |
| **Verified behaviour** | Not stored in application. |
| **Confirm with** | Management |

### 2.18 Customer visibility of reverse tracking — resolved

| Field | Detail |
|-------|--------|
| **Question** | Should customers see reverse pickup AWB in account? |
| **Verified behaviour** | Yes. Account order detail, **Returns & exchanges**, pickup emails, and the customer return API expose reverse AWB and a **Track pickup** link (stored URL, or ExpressBees tracking page built from the AWB). |

---

## 3. Verified limitations and missing admin controls

| # | Gap | Impact | Safe workaround |
|---|-----|--------|-----------------|
| 1 | No single “failed Zoho sync queue” screen for non-technical users | Hard to retry without Orders/Zoho pages | Use **Zoho settings** test connection, per-order sync on order detail, **Retry** on cancellations/refunds; escalate with order number + timestamp |
| 2 | Full Zoho product pull (`full=true`) not exposed in admin UI | Requires API or developer | Use incremental pull; schedule full pull with technical team |
| 3 | Zoho API daily/minute quotas | Bulk jobs may stop mid-run with 429 | Spread pulls; avoid peak hours |
| 4 | `RETURNS_REFUNDS_ENABLED` is code constant (`true`) | Cannot disable returns from admin without deploy | Escalate to technical team if kill-switch needed |
| 5 | Ledgerly admin redirect | `/admin/integrations/ledgerly` redirects to `/admin` | Ledgerly operations not in dedicated admin UI |
| 6 | Sales role scope | Sales desk: **Products** only (`/sales/products`) | Use Operations/Admin for orders |
| 7 | Customer Support cannot fulfill | `canFulfill = mode !== "support"` on returns/cancellations | Support approves/rejects where permitted; operations books shipments |
| 8 | Screenshot library for handover | Admin interior screens require authenticated session | Capture during training with test data |
| 9 | Invoice webhook direct to catalog | Ops cannot fix nginx/gateway from admin | Technical escalation |
| 10 | Duplicate Zoho records | Manual reconciliation in Zoho Books | Do not delete website orders; escalate with IDs |
| 11 | Package short-ship / partial pack | Newer fulfilment feature in codebase | Confirm training on **Create package** dialogs |
| 12 | Test products visible on UAT storefront | Risk of confusion on UAT | Use clearly labelled test SKUs only |

---

## 4. UAT vs production differences

| Item | UAT (`uat.minisnmore.com`) | Production (`minisnmore.com`) |
|------|----------------------------|-----------------------------|
| Purpose | Testing and training | Live customers and payments |
| Zoho OAuth redirect | Must match Zoho app config for UAT host | Documented production redirect on `/admin/zoho` |
| Payment keys | May use Razorpay test/demo mode | Live Razorpay |
| Data | May contain test orders/products | Real PII and money |

**Client confirmation required:** Which environment is in scope for formal sign-off of this handover.

---

## 5. Recommended live demonstrations before acceptance

1. Staff login, password reset, and sign-out (`/admin/login`).
2. Create or edit a simple product and confirm on `/shop`.
3. Create a variant product; verify parent price/stock are sums of variants.
4. Place test order (online and COD) on agreed environment; verify order in **Orders**.
5. Mark COD collected (if applicable).
6. Create package / hand-wrap signal and book ExpressBees shipment (or manual AWB).
7. Walk through customer cancellation request → admin approve/reject.
8. Walk through return or exchange through refund or replacement completion.
9. Zoho: **Test connection**, single product push/pull, order sync status on one order.
10. Verify customer tracking page (`/track` or account order detail).

---

## 6. Sign-off

| Role | Name | Date | Accepted (Y/N) | Comments |
|------|------|------|----------------|----------|
| Client operations representative | | | | |
| Client finance / Zoho owner | | | | |
| Delivery / technical representative | | | | |

---

*This document should be updated when business rules are confirmed or when new releases change behaviour.*
