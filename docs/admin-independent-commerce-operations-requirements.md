# Admin-Independent End-to-End Commerce Operations

## Product Requirements Document: Pre-Order, Order Operations, Cancellation, Return, Exchange and Zoho Synchronization

| Document field | Value |
|---|---|
| Status | Proposed for product and engineering sign-off |
| Version | 1.0 |
| Date | 2026-09-08 |
| Product | Mini's n More |
| Primary users | Customer, Admin, Operations, Finance, Support |
| Primary objective | Run the complete commerce lifecycle from the Mini's n More admin, with every mapped business action automatically reflected in Zoho |

---

## 1. Purpose

This document defines the required behavior for pre-purchase administration, advance pre-orders, normal order operations, customer cancellation, return, exchange, refund, reverse-pickup and replacement-delivery workflows.

The Mini's n More platform and admin portal must be the operational system of record. When Zoho is connected, every supported admin action must automatically reflect in Zoho and supported Zoho changes must be pulled back into the local admin. The admin must not perform the same work twice. Zoho, Ledgerly and similar systems are non-blocking integrations: their temporary availability, configuration or response must not determine whether an admin can view, approve, reject, receive, inspect, refund, exchange or close a customer request.

The requirements incorporate:

- the three supplied cancellation, return and exchange sequence diagrams;
- the behavior present in the codebase on 2026-09-08;
- gaps found between the diagrams and the current implementation; and
- the requirement that admin operations remain independent of Zoho.

This is a product and functional requirements document. Proposed endpoint and data names are illustrative and may be refined during technical design, provided the behaviors and acceptance criteria remain intact.

---

## 2. Business objectives

1. Give admin users one complete local workspace for catalog, inventory, pre-orders, orders, fulfillment, cancellations, returns and exchanges.
2. Prevent Zoho outages or configuration errors from interrupting customer service.
3. Integrate reverse pickup and replacement delivery with XpressBees while retaining a manual-carrier fallback.
4. Make every stock and money movement traceable, idempotent and recoverable.
5. Prevent premature stock restoration and over-refunds.
6. Give customers clear status, tracking and failure notifications.
7. Support online payments, COD and manual refund methods correctly.
8. Preserve an immutable audit trail of every administrative and automated decision.

### Success measures

- 100% of eligible post-order cases can be completed while Zoho integration is disabled.
- No duplicate refund, pickup or replacement shipment is created by a repeated request or webhook.
- No returned unit becomes sellable before inspection marks it restockable.
- Every non-terminal case has a visible next action or an actionable failure state.
- Admin can identify integration failures without those failures changing the local business outcome.
- Customers receive status updates for approval, rejection, pickup, pickup failure, refund, replacement dispatch and closure.
- Every mapped admin action automatically synchronizes to Zoho within the configured target time, or appears in an actionable retry queue.
- Admin can push, pull, reconcile and monitor Zoho data without leaving the Mini's n More admin portal.

---

## 3. Scope

### 3.1 In scope

- Product, variant, category, price, tax, image and sellability administration.
- Local inventory availability, reservation, adjustment and reconciliation.
- Explicit advance pre-order products and customer pre-orders, when enabled.
- Customer and admin-assisted order creation and confirmation.
- Payment, allocation, picking, hand-wrapping, packing, shipment, delivery and COD operations.
- Automatic push of mapped admin business actions to Zoho and controlled pull from Zoho.
- Whole-order and item-level cancellation requests, subject to fulfillment rules.
- Item-level and quantity-level returns.
- Item-level and quantity-level exchanges for another sellable variant.
- Admin approval, rejection and exception handling.
- Refund calculation, initiation, reconciliation and manual recording.
- Reverse pickup booking, tracking, retry and failure handling.
- Warehouse receipt, inspection and item disposition.
- Replacement inventory reservation, packing, shipping and delivery.
- Customer and admin notifications.
- Local operational reporting and audit history.
- Automatic non-blocking synchronization to every enabled Zoho/Ledgerly integration.
- Migration from the current return/exchange implementation.

### 3.2 Out of scope for the first release

- International return logistics.
- Marketplace orders not created in Mini's n More.
- Automated fraud scoring.
- Automatic vendor return booking.
- Complex exchanges across multiple source orders.
- Store-credit wallets, unless separately approved.

These exclusions must not prevent an admin from recording the equivalent action manually with a reason and audit history.

---

## 4. Definitions

| Term | Definition |
|---|---|
| Case | A cancellation, return or exchange request with its own reference, items, history and outcome. |
| Local system | The Mini's n More application database, services and admin portal. |
| Operational source of truth | The system whose status controls storefront and admin behavior. This must be the local system. |
| Accounting sync | Automatic, non-blocking push/pull synchronization between local business events and an enabled Zoho, Ledgerly or other accounting platform. |
| Pre-purchase operations | Catalog, pricing, tax, stock and readiness work performed before a customer order is placed. |
| Advance pre-order | A customer order accepted against configured future capacity with a promised availability/dispatch window. |
| Reverse shipment | Movement of goods from the customer back to the warehouse/admin. |
| Replacement shipment | Outbound shipment of exchange goods to the customer. |
| AWB | Air waybill/tracking number assigned by a carrier. The “AWP” label in the supplied exchange diagram is interpreted as AWB. |
| Inspection | Warehouse assessment of quantity, identity and condition after receipt. |
| Disposition | What happens to a received item: restock, quarantine, repair, discard or return to vendor. |
| Refund | A money movement back to the customer, distinct from case approval. |
| Integration health | Technical state of an optional external sync; it is not a customer order or case status. |

---

## 5. Governing principles

### P-01: Local operational authority

All order, case, stock, payment, refund, shipment, inspection and customer-visible statuses must be stored and controlled locally.

### P-02: Zoho synchronization is automatic but non-blocking

When Zoho is enabled, every supported admin action must automatically create the corresponding Zoho update. The admin must not open Zoho or repeat the same action there. “Independent of Zoho” means that Mini's n More remains usable during a Zoho outage; it does not mean that the systems are disconnected.

The following actions must work when Zoho is disabled, unauthenticated, rate-limited, slow or unavailable:

- load an order or case;
- check locally available eligibility data;
- submit, approve or reject a request;
- cancel an order;
- reserve or release stock;
- book or manually record a pickup;
- receive and inspect an item;
- issue or record a refund;
- allocate, pack and dispatch a replacement;
- generate customer-facing documents and notifications; and
- close the case.

No admin button may call Zoho synchronously as a prerequisite for completing its local business transaction. The local action must immediately display a separate Zoho state such as `sync pending`, `synced` or `sync failed`.

### P-03: Integrations follow local events

After a local action commits, an integration event must automatically be queued for every enabled downstream system. Integration delivery must be asynchronous, retryable and idempotent. A failed sync must show as an integration warning, not roll back or conceal the local action. Successful delivery must update the local record with the Zoho reference and synchronization time.

### P-04: External updates cannot silently overwrite local decisions

Inbound Zoho or Ledgerly data must not regress or override a locally confirmed cancellation, refund, inspection, stock disposition, replacement shipment or case closure. Conflicts must be recorded for review.

### P-05: Stock follows physical reality

Cancellation may release stock that never left the warehouse. A returned or exchanged item must not be added to sellable stock until it has been received, inspected and explicitly marked restockable.

### P-06: Money movement is separate from workflow approval

Approval authorizes the next action; it does not imply that money was refunded. Refund state and references must be recorded separately from case state.

### P-07: Every side effect is idempotent

Repeated API calls, webhook deliveries, page refreshes and job retries must not create duplicate refunds, inventory movements, pickup requests, AWBs, replacement shipments or accounting entries.

---

## 6. System authority matrix

| Business data/action | Authoritative system | Integrated-system behavior |
|---|---|---|
| Customer order and line snapshots | Mini's n More | Automatically push to Zoho/Ledgerly; permitted external records may also be pulled and mapped |
| Customer cancellation/return/exchange case | Mini's n More | Automatically send every supported operational and financial outcome to Zoho/Ledgerly |
| Case eligibility and decision | Mini's n More | None required |
| Available, reserved, quarantined and sellable stock | Mini's n More | Push local changes and pull external balances for reconciliation; conflicts follow configured authority rules |
| Payment verification and online refund execution | Mini's n More + Razorpay response/webhook | Zoho/Ledgerly receive financial event |
| COD/manual refund record | Mini's n More admin | Accounting system receives event |
| Reverse and outbound logistics status | Mini's n More + carrier events | XpressBees or another carrier |
| Customer-facing order/case/tracking status | Mini's n More | Never computed solely from Zoho |
| Accounting journal/credit note | Accounting platform may own statutory ledger | Create/update automatically from local actions and pull its document number/status back locally |
| Audit trail | Mini's n More | Must not depend on external logs |

---

## 7. Roles and permissions

| Role | Minimum permissions |
|---|---|
| Customer | Submit eligible request, provide reason/evidence, choose replacement, view status/tracking, cancel before admin processing where allowed. |
| Support admin | View order/case, request more information, approve/reject within configured limits, communicate with customer, request/reschedule pickup. |
| Warehouse admin | Record pickup handover, receipt, inspection and disposition; pack replacement. |
| Finance admin | Approve exceptional refund, issue/retry/record refunds, reconcile gateway/manual refunds. |
| Super admin | Override eligibility or transition with mandatory reason, configure policy, manage integration retries and permissions. |
| System/integration worker | Apply valid automated transitions and integration results using a named service identity. |

### Permission requirements

- RBAC must be enforced by the backend, not only by hiding frontend controls.
- Monetary limits and manual refund permissions must be configurable by role.
- An override must capture actor, timestamp, previous value, new value and reason.
- No user may approve their own exceptional manual adjustment above the configured maker-checker threshold.

---

## 8. Pre-Purchase and Pre-Order Requirements

This document uses “pre-order” in both common business senses:

1. **Pre-purchase administration**: work completed before a customer places an order, including catalog, price and inventory setup.
2. **Advance pre-order sale**: accepting an order for a product that will become available or dispatch later.

Advance pre-order functionality may be feature-flagged. Pre-purchase administration and its Zoho synchronization are mandatory.

### 8.1 Catalog and merchandising administration

| ID | Requirement |
|---|---|
| PRE-01 | Admin must create and manage products, variants, SKUs, categories, descriptions, images, tax configuration, MRP, selling price and active/sellable state locally. |
| PRE-02 | Saving a mapped catalog business change must automatically enqueue the corresponding Zoho create/update operation when Zoho is enabled. |
| PRE-03 | Admin must see the local save result immediately and a separate per-record Zoho status: `pending`, `synced`, `failed`, `conflict` or `not configured`. |
| PRE-04 | Zoho item/group IDs returned after creation must be stored locally and used for later idempotent updates. |
| PRE-05 | Admin must be able to pull supported products, variants, images, prices, taxes and active states from Zoho using preview and conflict-resolution controls. |
| PRE-06 | Product publication to the storefront must use locally valid data and must not require a live Zoho call. |
| PRE-07 | A failed Zoho product sync must not discard the local edit; it must enter the retry queue with an actionable error. |
| PRE-08 | Deactivation, SKU rename and variant restructuring must show their storefront, stock, open-order and Zoho impact before confirmation. |
| PRE-09 | SKU and external-ID mappings must remain unique, versioned and auditable. |
| PRE-10 | Bulk import/export must validate every row, provide a preview and report partial failures without silently dropping data. |

### 8.2 Pricing, tax and promotion readiness

| ID | Requirement |
|---|---|
| PRE-11 | Admin must manage locally effective selling prices, MRP, tax treatment and effective dates. |
| PRE-12 | Price/tax changes must be snapshotted on new orders and must not rewrite historical order values. |
| PRE-13 | Mapped price and tax changes must synchronize automatically to Zoho, with unsupported fields clearly identified. |
| PRE-14 | Coupons and storefront promotions remain local unless an explicit Zoho mapping exists; the final applied discount and tax breakdown must be sent with the order. |
| PRE-15 | Concurrent local and Zoho price edits must create a conflict instead of last-write-wins data loss. |

### 8.3 Inventory readiness and procurement-facing data

| ID | Requirement |
|---|---|
| PRE-16 | Admin must view local on-hand, reserved, available, quarantine, damaged and expected/pre-order quantities by SKU and location. |
| PRE-17 | Authorized stock receipts and corrections must create append-only local inventory movements and automatically sync mapped adjustments to Zoho. |
| PRE-18 | Zoho stock pulls/webhooks must reconcile against local balances; they must not silently replace reservations or physical-disposition records. |
| PRE-19 | Admin must see discrepancies by SKU/location and resolve them using `keep local`, `accept Zoho adjustment` or an explicit reconciled quantity with a reason. |
| PRE-20 | Serviceability and sellability checks used by the storefront must operate from local cached/authoritative data. |

### 8.4 Advance pre-order sale

| ID | Requirement |
|---|---|
| PRE-21 | Admin can enable advance pre-order per product/variant with sale start/end, expected availability/dispatch window, quantity capacity and per-customer limit. |
| PRE-22 | The storefront must clearly label a pre-order before cart and payment and show the expected dispatch window. |
| PRE-23 | A customer must explicitly acknowledge the pre-order promise and any mixed-cart shipment policy. |
| PRE-24 | Pre-order capacity must be reserved atomically so confirmed pre-orders cannot exceed the configured capacity. |
| PRE-25 | Payment policy must be configurable as full payment or approved deposit; outstanding balance must be tracked separately. |
| PRE-26 | A confirmed pre-order must automatically create/update the corresponding Zoho customer and sales-order data with pre-order flag, promised date/window and local reference. |
| PRE-27 | Zoho stock state must not reject or reverse a locally valid pre-order solely because physical stock has not arrived; pre-order quantity/capacity is a separate local concept. |
| PRE-28 | Admin can record incoming availability, allocate it to confirmed pre-orders in a deterministic order and release each order into normal fulfillment. |
| PRE-29 | Changing the promised date must capture a reason, synchronize to Zoho and notify affected customers. Material delay may require customer consent under policy. |
| PRE-30 | Customer/admin can cancel an eligible pre-order under its configured policy; reservation/capacity and money are released/refunded exactly once. |
| PRE-31 | If supply is short, admin can prioritize only through an authorized, audited override and must provide offer/wait/cancel/refund options to affected customers. |
| PRE-32 | Mixed carts must follow a configured split-shipment or hold-until-complete policy that is shown before purchase and represented correctly in Zoho documents. |

### 8.5 Pre-order acceptance criteria

- A product/variant created or edited in local admin automatically appears in Zoho or enters a visible retry queue.
- Catalog and storefront remain usable while Zoho is unavailable.
- A Zoho pull cannot silently overwrite a concurrent local price, SKU or stock-reservation change.
- Two customers cannot acquire the last pre-order capacity simultaneously.
- A confirmed advance pre-order appears in Zoho with the local reference and promised window.
- Receiving supply locally can allocate and release pre-orders into fulfillment without an admin repeating the action in Zoho.
- A promised-date change updates Zoho and notifies every affected customer exactly once.

---

## 9. Order and Fulfillment Operations Requirements

### 9.1 Separate operational dimensions

Order, payment, fulfillment, shipment and accounting synchronization must use separate statuses. A change in one dimension must not imply an unverified change in another.

Suggested normalized fulfillment flow:

```text
PENDING_CONFIRMATION
  -> CONFIRMED
  -> ALLOCATED
  -> PICKING
  -> HAND_WRAPPING
  -> PACKING
  -> READY_TO_SHIP
  -> SHIPPED
  -> DELIVERED
  -> CLOSED

Exception branches: ON_HOLD, PARTIALLY_FULFILLED, FAILED_DELIVERY,
RETURN_TO_ORIGIN and CANCELLED
```

### 9.2 Order capture and confirmation

| ID | Requirement |
|---|---|
| OPS-01 | Customer checkout and authorized admin-assisted creation must create the order and immutable commercial snapshot locally. |
| OPS-02 | Order numbers must be generated locally and remain the primary operational reference; Zoho IDs are mappings. |
| OPS-03 | Confirmation must validate local price, tax, discount, serviceability, stock/capacity and payment policy. |
| OPS-04 | Every confirmed order must automatically enqueue the mapped Zoho contact, sales order and line-item operations. |
| OPS-05 | Zoho failure must not lose a confirmed order or require re-entry; admin sees a pending/failed sync state and retry. |
| OPS-06 | A repeated checkout callback, admin confirmation or sync retry must not create a duplicate local or Zoho order. |
| OPS-07 | Admin may place an order on hold and release it with a reason; both changes must reflect in Zoho where supported. |
| OPS-08 | Changes after confirmation must use explicit amendment/cancellation actions and an audit trail rather than editing the original snapshot silently. |

### 9.3 Payment and COD operations

| ID | Requirement |
|---|---|
| OPS-09 | Payment status must be verified from Razorpay/webhook or an authorized local COD/manual record, never inferred solely from Zoho. |
| OPS-10 | Captured, failed, partially paid, COD due, COD collected, partially refunded and refunded states must be represented locally. |
| OPS-11 | Confirmed payment/COD collection must automatically create or update the mapped Zoho invoice/payment data. |
| OPS-12 | Admin must reconcile pending/unknown gateway callbacks and COD collections without duplicate payment records. |
| OPS-13 | Payment and accounting sync errors must be shown separately; a Zoho error cannot change verified payment truth. |

### 9.4 Allocation and warehouse operations

| ID | Requirement |
|---|---|
| OPS-14 | Confirmed eligible quantities must be allocated/reserved locally before picking. |
| OPS-15 | Admin/warehouse can generate and complete pick, hand-wrapping and packing tasks by order/package and SKU. |
| OPS-16 | Partial allocation and fulfillment must preserve remaining quantities and prevent overselling. |
| OPS-17 | Each material warehouse action must update local status/inventory and automatically send its mapped package/inventory status to Zoho. |
| OPS-18 | Package creation must be local-first and must retain weight, dimensions, contained line quantities, packer and timestamps. |
| OPS-19 | Repacking, shortage, damage and substitution require explicit exception actions and audit reasons. Customer consent is required for a product substitution. |
| OPS-20 | Admin must be able to print locally generated pick lists, packing slips and labels without waiting for Zoho. Zoho document references may be added when synchronization completes. |

### 9.5 Shipment, delivery and return-to-origin

For normal forward orders, XpressBees—not Zoho—must be integrated directly with the Mini's n More admin portal. The admin portal is the operational control surface, while carrier events are the authority for physical movement. Zoho receives a synchronized copy of locally stored shipment and tracking data for accounting/document consistency.

The intended forward-shipment flow is:

```text
Admin marks a locally packed order Ready to ship
  -> Admin clicks Book XpressBees shipment
  -> Mini's n More calls XpressBees directly
  -> Mini's n More stores provider shipment ID, AWB, label and tracking URL
  -> Mini's n More queues the mapped package/shipment update to Zoho
  -> XpressBees webhooks/polling update local tracking status
  -> each accepted forward status change is queued to Zoho
  -> admin and customer read tracking from the local record
```

| ID | Requirement |
|---|---|
| OPS-21 | Admin can book XpressBees or record a manual carrier shipment, with AWB, label, tracking link and package mapping stored locally. |
| OPS-22 | Booking, cancellation and status updates must be idempotent and support authenticated webhook/poll reconciliation. |
| OPS-23 | Shipment creation and forward-only tracking changes must automatically update mapped Zoho package/shipment records. |
| OPS-24 | Customer-facing tracking must come from normalized local shipment state and remain available without a live Zoho call. |
| OPS-25 | Delivery confirmation must record its source and timestamp locally before updating order fulfillment and Zoho. |
| OPS-26 | Failed delivery and return-to-origin must have explicit reasons, attempt history, reship/cancel/refund next actions and stock handling. |
| OPS-27 | Delivery must not be inferred merely because a Zoho invoice is paid or a sales order says fulfilled; a reliable delivery/carrier signal is required. |
| OPS-28 | The order detail page must provide authorized actions to check serviceability, book shipment, download/print label, schedule pickup, cancel an eligible booking, retry a failed booking and refresh tracking directly through XpressBees. |
| OPS-29 | A successful local shipment action must enqueue its Zoho update automatically; Zoho unavailability must not invalidate the XpressBees booking or hide the AWB. |
| OPS-30 | Routine in-transit, out-for-delivery, delivered, failed-attempt and RTO statuses must originate from authenticated XpressBees events or reconciliation polling, not from an admin selecting an arbitrary status. |
| OPS-31 | A restricted manual correction must record reason, evidence, actor and timestamp, must not alter XpressBees history, and must queue the corrected mapped state to Zoho. |
| OPS-32 | Zoho tracking pulls may be retained temporarily for migration/reconciliation, but they must not overwrite a newer carrier-confirmed or authorized local shipment state. |

### 9.6 Admin operations workspace

The local admin must provide queues for:

- payment pending/unknown and COD collection;
- Zoho sync pending/failed/conflicted;
- allocation shortage and aging pre-orders;
- picking, hand-wrapping, packing and ready-to-ship;
- carrier booking failure and delivery exception;
- return-to-origin and reshipment; and
- cancellation, return, exchange and refund work described below.

Each queue must support ownership, priority, SLA, search/filter, safe bulk operations where applicable and a direct link to the full local audit timeline.

### 9.7 Order-operations acceptance criteria

- A confirmed order is saved once locally and created once in Zoho despite retries.
- Picking, wrapping, packing, shipment and delivery actions automatically reflect in Zoho without duplicate admin work.
- Warehouse operations continue during a Zoho outage and synchronize after recovery in their correct order.
- A partial shipment contains the correct local/Zoho line quantities and leaves the remainder open.
- COD collection updates local payment truth and automatically creates the mapped Zoho payment.
- Out-of-order Zoho/carrier callbacks do not regress a delivered or cancelled local order.

---

## 10. Common Post-Order Case Requirements

### CR-01: Unique case reference

Each request must receive a human-readable, immutable reference such as `CAN-...`, `RET-...` or `EXC-...`.

### CR-02: Immutable order snapshot

The case must retain the relevant order item description, SKU, selected variant, unit price, discount allocation, tax allocation and paid amount as they were at purchase. Later catalog edits must not alter eligibility or refund calculations.

### CR-03: Line and quantity selection

A return or exchange must identify one or more order lines and a quantity not greater than the remaining eligible quantity. Already cancelled, returned, exchanged or active-case quantities must be excluded.

### CR-04: Duplicate prevention

The system must prevent overlapping active requests for the same order-line quantity, while allowing independent cases for other eligible quantities.

### CR-05: Reasons and evidence

- A customer must select a configured reason and may add notes and evidence.
- Rejection, override, manual closure and failed-pickup cancellation require an admin reason.
- Evidence files must be access-controlled, malware-scanned where supported and retained according to policy.

### CR-06: Policy snapshot

Eligibility must be evaluated from a versioned policy snapshot saved on the case, including request window, non-returnable rules, fulfillment state and any exception used. A later policy change must not silently change an existing case.

### CR-07: Status history

Every status change must append an audit event containing:

- case and order references;
- old and new statuses;
- actor type and actor identifier;
- timestamp;
- reason/comment;
- request/correlation identifier; and
- relevant external reference, if any.

### CR-08: Notes

The admin must support internal-only notes separately from customer-visible comments.

### CR-09: Concurrency

Transitions must reject stale updates and invalid state changes. Double-clicking an action or processing concurrent webhooks must have the same outcome as processing it once.

### CR-10: SLA and queues

The admin dashboard must show case age, current owner, next action, due time and overdue indicator. SLA values must be configurable by case type and state.

---

## 11. Cancellation requirements

### 11.1 Target flow

```text
Customer submits cancellation request
  -> local eligibility check
  -> PENDING_REVIEW
      -> admin APPROVES
          -> stop/release fulfillment locally
          -> release reserved stock
          -> refund if money was collected
          -> notify customer
          -> CLOSED
      -> admin REJECTS
          -> retain order fulfillment
          -> notify customer with reason
          -> CLOSED
```

The default behavior follows the supplied cancellation diagram: the customer requests and the admin decides. A configurable auto-approval rule may approve safe pre-fulfillment requests, but it must create the same case, audit events and local side effects as an admin approval.

### 11.2 Functional requirements

| ID | Requirement |
|---|---|
| CAN-01 | The customer can request whole-order cancellation and, when enabled, cancellation of eligible unfulfilled line quantities. |
| CAN-02 | The request must capture a reason before submission. |
| CAN-03 | Eligibility must use local fulfillment, allocation, shipment and payment data; it must not query Zoho to decide. |
| CAN-04 | Cancellation must normally be blocked after carrier handover/shipment. Admin may redirect the customer to a return flow after delivery. |
| CAN-05 | Admin can approve, reject or request more information. Rejection requires a customer-safe reason. |
| CAN-06 | Approval must atomically mark the eligible order quantity cancelled and release only the stock reservations associated with that quantity. |
| CAN-07 | If an outbound label exists but the parcel has not been handed over, the system must attempt carrier cancellation asynchronously and flag any manual action needed. The local case is not blocked by carrier failure. |
| CAN-08 | For captured online payment, approval must create a refund record and submit it to Razorpay. |
| CAN-09 | For unpaid COD, no refund is created. For collected COD, a finance admin must select and record an approved refund method. |
| CAN-10 | Customer-visible status must distinguish `cancellation requested`, `cancelled — refund pending`, `cancelled — refunded`, and `cancellation rejected`. |
| CAN-11 | A pending cancellation must prevent a conflicting shipment unless an authorized admin explicitly rejects or overrides the request. |
| CAN-12 | Zoho/Ledgerly cancellation or credit-note synchronization must occur after local approval and must not block it. |

### 11.3 Cancellation acceptance criteria

- With Zoho disabled, an eligible cancellation can be requested, approved, refunded and closed.
- Approving the same request twice creates at most one inventory release and one refund.
- An unpaid COD cancellation never calls a refund gateway.
- A Razorpay timeout leaves the order locally cancelled with `refund pending/failed`, visible retry controls and no duplicate refund.
- Rejection leaves fulfillment and reserved stock unchanged.
- Shipment cannot proceed while a cancellation is pending unless an authorized override is audited.

---

## 12. Return requirements

### 12.1 Target flow

```text
REQUESTED
  -> APPROVED -> PICKUP_REQUESTED -> PICKUP_SCHEDULED
       -> PICKED_UP -> IN_TRANSIT -> RECEIVED -> INSPECTED
            -> ACCEPTED -> REFUND_PENDING -> REFUNDED -> CLOSED
            -> REJECTED_AFTER_INSPECTION -> RETURN_TO_CUSTOMER / CLOSED
       -> PICKUP_FAILED -> RESCHEDULED or CANCELLED
  -> REJECTED -> CLOSED
  -> CANCELLED_BY_CUSTOMER (only before operational processing)
```

### 12.2 Eligibility

| ID | Requirement |
|---|---|
| RET-01 | The order must be locally recorded as delivered. |
| RET-02 | The request must be within the configured return window measured from a reliable local delivery timestamp. The current code uses 3 days; this value must remain configurable and be confirmed by product before release. |
| RET-03 | The selected quantity must be paid/collected, not cancelled, not already returned/exchanged and not part of another active case. |
| RET-04 | Product/category/SKU exclusions and hygiene rules must be evaluated from a versioned local policy. |
| RET-05 | An authorized admin can grant an exception with a mandatory reason; the system must display both normal eligibility and override details. |

### 12.3 Approval and reverse pickup

| ID | Requirement |
|---|---|
| RET-06 | Admin can approve, reject or request more information from `REQUESTED`. |
| RET-07 | Approval must not restore sellable stock and must not issue the refund. |
| RET-08 | Admin can book an XpressBees reverse pickup using the locally stored pickup address and contact details. |
| RET-09 | The system must store carrier name, provider request ID, reverse AWB, tracking URL, pickup date/window and raw provider status. |
| RET-10 | If carrier integration is unavailable, admin can select `manual carrier` or `customer self-ship`, enter tracking details and continue the same local workflow. |
| RET-11 | XpressBees booking and webhook processing must be idempotent. |
| RET-12 | Pickup failures must capture a standardized reason such as customer unavailable, item unavailable, customer changed mind, address issue, carrier capacity or other. |
| RET-13 | After pickup failure, admin can reschedule, change pickup mode or cancel/close the case with a reason. The case must never be left in a state with no valid next action. |
| RET-14 | The customer and assigned admin must be notified of scheduled pickup, pickup failure, reschedule and successful collection. |

### 12.4 Receipt, inspection and disposition

| ID | Requirement |
|---|---|
| RET-15 | Warehouse can record partial or complete receipt against the expected case items. |
| RET-16 | Receipt must capture received quantity, date/time, receiver and optional package evidence. |
| RET-17 | Inspection must capture identity match, condition, completeness, damage/hygiene checks, evidence and notes for each line. |
| RET-18 | Inspection outcome must be `accepted`, `partially accepted` or `rejected`, with line-level accepted quantities. |
| RET-19 | Each accepted unit must receive a disposition: `restockable`, `quarantine`, `repair`, `discard` or `return_to_vendor`. |
| RET-20 | Only a `restockable` disposition may increase sellable stock. Other outcomes update the appropriate non-sellable bucket or adjustment ledger. |
| RET-21 | A rejected-after-inspection item must follow a documented resolution: return to customer, exceptional refund or disposal with customer consent. |

### 12.5 Refund

| ID | Requirement |
|---|---|
| RET-22 | Refund must be calculated at line and quantity level from the paid order snapshot, including prorated discounts and tax. |
| RET-23 | Shipping, COD fee, donation and promotional adjustments must follow configurable policy and be shown as separate calculation lines. |
| RET-24 | The admin must see the proposed refund breakdown before confirmation. |
| RET-25 | The refund amount must never exceed the remaining refundable amount for the payment/order line. |
| RET-26 | Online payments must be refunded through the original supported gateway where possible. |
| RET-27 | Collected COD or exceptional offline refunds must support authorized manual methods such as bank transfer or UPI, with transaction reference, date, proof and actor. |
| RET-28 | `refund pending`, `refund processing`, `refund failed` and `refunded` must be distinct states. |
| RET-29 | A gateway failure must retain a retryable refund record and must not reverse item receipt, inspection or disposition. |
| RET-30 | A refund completion webhook/reconciliation job must safely resolve uncertain gateway outcomes without issuing another refund. |

### 12.6 Return acceptance criteria

- A two-item order can return one item without refunding the entire order.
- Approval does not increase stock; only accepted inspection with `restockable` disposition does.
- XpressBees pickup failure can be rescheduled or manually closed and notifies admin/customer.
- A manually shipped return can be received, inspected and refunded without any carrier API.
- A paid COD return can complete through an audited manual refund.
- Accounting sync failure does not change the local `refunded` state.

---

## 13. Exchange requirements

### 13.1 Target flow

```text
REQUESTED
  -> validate source item, target variant and serviceability
  -> APPROVED + reserve replacement stock
  -> reverse pickup lifecycle
  -> RECEIVED -> INSPECTED
       -> ACCEPTED
           -> settle price difference
           -> ALLOCATED -> PACKING -> READY_TO_SHIP
           -> SHIPPED -> DELIVERED -> CLOSED
       -> CONVERTED_TO_REFUND -> refund lifecycle -> CLOSED
       -> REJECTED_AFTER_INSPECTION -> resolution -> CLOSED
  -> REJECTED -> release any hold -> CLOSED
```

### 13.2 Selection and reservation

| ID | Requirement |
|---|---|
| EXC-01 | Customer selects the source order line/quantity and requested replacement SKU/size/color from locally sellable variants. |
| EXC-02 | The request screen must show availability but must state that selection is not guaranteed until approval/reservation. |
| EXC-03 | Approval must atomically reserve the exact replacement quantity locally. A simple availability check without reservation is insufficient. |
| EXC-04 | The reservation must have a configurable expiry/exception policy and may not silently expire while a pickup is progressing normally. |
| EXC-05 | Rejection, customer cancellation, conversion to refund or case closure without exchange must release the reservation exactly once. |
| EXC-06 | If the requested variant is unavailable at approval, admin can offer another variant, waitlist with consent or convert to return/refund. Customer consent must be recorded for a changed replacement. |

### 13.3 Reverse logistics and inspection

| ID | Requirement |
|---|---|
| EXC-07 | Exchange uses the same reverse pickup, failure, manual carrier, receipt, inspection and disposition requirements as a return. |
| EXC-08 | The original item must not re-enter sellable stock before restockable inspection. |
| EXC-09 | If inspection rejects the original item, the replacement reservation must not be consumed until the authorized resolution is chosen. |
| EXC-10 | Admin can convert an accepted exchange to a refund if replacement fulfillment becomes impossible, without creating a second overlapping case. |

### 13.4 Price difference

| ID | Requirement |
|---|---|
| EXC-11 | Exchange valuation must use the configured policy: original paid unit allocation versus replacement selling price at request/approval time. The chosen price and policy version must be snapshotted. |
| EXC-12 | If replacement price is higher, the difference must be collected and locally confirmed before dispatch unless an authorized waiver is recorded. |
| EXC-13 | If replacement price is lower, the difference must be refunded through the normal refund workflow or handled according to an explicitly configured no-difference policy shown before customer confirmation. |
| EXC-14 | Tax and discount implications must be represented in the adjustment breakdown and downstream accounting event. |

### 13.5 Replacement fulfillment

| ID | Requirement |
|---|---|
| EXC-15 | After acceptance and settlement, admin can allocate, pack and mark the replacement ready to ship locally. |
| EXC-16 | XpressBees outbound shipment booking must store provider shipment ID, AWB, label, carrier, tracking URL and status. |
| EXC-17 | Manual shipment must be supported when XpressBees is unavailable. |
| EXC-18 | Carrier webhook/poll updates must map to normalized local statuses without allowing status regression. |
| EXC-19 | The customer must receive replacement dispatch and delivery notifications containing the local case reference and tracking link. |
| EXC-20 | The exchange closes only after confirmed delivery or an authorized manual resolution. Generating an AWB alone is not completion. |

### 13.6 Exchange acceptance criteria

- Two admins cannot reserve the last unit for two different exchange cases.
- Reserved stock is not available for a new storefront order.
- Returned stock is not marked sellable at approval or pickup.
- Higher/lower price differences are settled and audited before closure.
- Replacement dispatch and delivery work with Zoho completely disabled.
- If XpressBees fails, admin can enter another carrier/AWB and complete the case.

---

## 14. Refund and payment subsystem

### 14.1 Refund record

Every refund attempt must have its own local record with:

- refund reference and idempotency key;
- order and case references;
- payment method and original transaction reference;
- requested, approved and completed amounts;
- currency;
- method/provider;
- local status;
- provider refund/transaction reference;
- failure category and safe error message;
- attempt count and timestamps;
- initiating and approving actor; and
- reconciliation result.

### 14.2 Refund rules

- A case may have multiple attempts but only one successful money movement for the same refundable allocation.
- The system must distinguish technical failure from business rejection.
- Raw provider errors and credentials must not be exposed to customers.
- A finance admin must be able to search and reconcile `pending`, `unknown` and `failed` refunds.
- The local order payment status must support partial refunds rather than changing the whole order to fully refunded after a partial return.
- Refund status must be derived from local refund records and confirmed provider/manual evidence, not from Zoho credit-note state.

---

## 15. Inventory subsystem

### 15.1 Required stock buckets

The local model must distinguish at least:

- on hand;
- reserved for customer order;
- reserved for exchange;
- available to sell;
- in reverse transit;
- awaiting inspection/quarantine;
- restockable/returned;
- damaged/discarded; and
- returned to vendor.

An implementation may derive some balances from an append-only inventory movement ledger instead of storing every balance directly.

### 15.2 Inventory movements

Each movement must contain SKU, quantity, source bucket, destination bucket, business reason, order/case reference, actor, timestamp and idempotency key.

Required movement points:

- order allocation and release;
- cancellation approval;
- exchange reservation and release;
- pickup/return in transit where tracked;
- warehouse receipt to quarantine;
- inspection to sellable/non-sellable disposition;
- replacement dispatch; and
- authorized corrections.

Zoho or Ledgerly stock sync must publish the resulting local movement or balance asynchronously. An external mismatch must create a reconciliation alert; it must not silently replace local stock.

---

## 16. XpressBees and carrier integration

The current client only checks serviceability. The target capability must cover normal forward-order delivery, reverse pickup and replacement outbound delivery directly from the admin portal.

| ID | Requirement |
|---|---|
| LOG-01 | Check serviceability using locally stored address data. |
| LOG-02 | Create reverse pickup and receive a provider request/shipment identifier. |
| LOG-03 | Generate/store reverse AWB and tracking URL. |
| LOG-04 | Cancel or reschedule an eligible pickup. |
| LOG-05 | Create outbound replacement shipment, AWB and label. |
| LOG-06 | Receive authenticated webhooks and/or poll status where webhooks are unavailable. |
| LOG-07 | Preserve raw provider status while mapping it to a normalized local status. |
| LOG-08 | Deduplicate events and reject invalid signatures. |
| LOG-09 | Record attempt history and actionable failure reason. |
| LOG-10 | Support a manual carrier path without fabricating an XpressBees reference. |
| LOG-11 | Create a normal forward-order shipment and return provider shipment ID, AWB, label and tracking URL. |
| LOG-12 | Schedule or request origin pickup for booked forward shipments and expose the result in admin. |
| LOG-13 | Cancel an eligible forward shipment and retry a failed booking without creating a second AWB unintentionally. |
| LOG-14 | Normalize forward events including picked up, in transit, out for delivery, delivered, failed attempt and RTO initiated/in transit/received. |
| LOG-15 | For every accepted forward, reverse or replacement event, update the local shipment first and then publish the mapped change asynchronously to Zoho. |
| LOG-16 | Store the carrier event time and ingestion time so delayed or out-of-order events cannot regress shipment state. |

Carrier errors must be categorized as retryable, customer-action-required, admin-action-required or terminal.

---

## 17. Zoho and Ledgerly independence

### 17.1 Required integration pattern

```text
Admin/customer action
  -> validate using local data
  -> commit local business transaction
  -> append local audit + outbox event in the same transaction
  -> return success to user
  -> background worker sends event to enabled accounting integrations
       -> success: record synced external reference
       -> failure: retry with backoff, then show dead-letter/manual retry
```

### 17.2 Functional requirements

| ID | Requirement |
|---|---|
| INT-01 | Zoho integration must be independently enableable/disableable by environment and organization. |
| INT-02 | Missing Zoho credentials must not disable local admin pages or actions. |
| INT-03 | No local transaction may be held open while waiting for Zoho. |
| INT-04 | Outbound events must use stable event IDs and idempotency metadata. |
| INT-05 | Integration status must be stored separately as `not_configured`, `pending`, `syncing`, `synced`, `failed` or `ignored`. |
| INT-06 | Admin must see external sync state and error summary without confusing it with case status. |
| INT-07 | Authorized users can retry, ignore with reason or export failed events. |
| INT-08 | Retry policy must use exponential backoff, a maximum automated attempt count and a dead-letter queue. |
| INT-09 | Inbound webhooks must be authenticated, deduplicated, audited and prevented from regressing protected local states. |
| INT-10 | External IDs must be nullable mapping fields, never primary business identifiers. |
| INT-11 | Customer pages and messages must never display an integration failure as though their cancellation, return or exchange failed. |
| INT-12 | Operational reporting must work from local data without live external API calls. |
| INT-13 | Every supported admin create, update, approve, reject, cancel, refund, stock, shipment and closure action must automatically enqueue its required Zoho changes when Zoho is enabled. |
| INT-14 | Admin must never be required to repeat a Mini's n More action manually in Zoho. |
| INT-15 | Zoho document IDs, numbers and statuses produced by a successful push must be pulled/stored against the corresponding local record. |
| INT-16 | Admin must be able to start scoped incremental or full push/pull synchronization and monitor progress without leaving the admin portal. |
| INT-17 | Scheduled sync, manual sync and webhook processing must share the same mappings, validation, idempotency and conflict rules. |
| INT-18 | Pull synchronization must support preview/dry-run for bulk destructive changes and report created, updated, unchanged, conflicted and failed counts. |
| INT-19 | A sync failure must retry automatically and expose a `Retry now` action; retrying must not repeat a successful Zoho operation. |
| INT-20 | Sync completion targets and alert thresholds must be configurable and monitored so `asynchronous` does not become indefinitely delayed. |

### 17.3 Minimum outbound event catalogue

- forward shipment booked/cancelled;
- origin pickup scheduled/completed/failed;
- forward shipment picked up/in transit/out for delivery/delivered;
- delivery attempt failed or RTO initiated/completed;
- order cancelled;
- order line cancelled;
- refund requested/completed/failed;
- returned item received;
- inspection accepted/rejected;
- inventory disposition posted;
- exchange price adjustment;
- replacement dispatched/delivered; and
- case closed.

### 17.4 Independence acceptance test

In a clean test environment, disable all Zoho credentials and block Zoho network access. An admin must still be able to complete:

1. an unpaid COD cancellation;
2. a paid online cancellation and Razorpay refund;
3. a return with manual reverse shipment and manual COD refund;
4. an exchange with XpressBees reverse pickup and replacement delivery; and
5. all related stock movements, notifications, reports and audit lookup.

The resulting integration events may remain `not_configured` or `pending`; the local cases must reach their correct terminal statuses.

### 17.5 Zoho synchronization management

The Mini's n More admin is the control panel for Zoho. Authorized admins must be able to connect/disconnect an organization, verify credentials, view last successful push/pull, run a sync, inspect failures, resolve conflicts and retry—all without using the Zoho interface for routine operations.

When Zoho is connected, the intended experience is:

```text
Admin clicks an action in Mini's n More
  -> local action succeeds and is visible immediately
  -> Zoho sync is queued automatically
  -> status shows Sync pending
  -> Zoho receives the equivalent supported update
  -> Zoho identifiers/statuses are saved locally
  -> status shows Synced

If Zoho is unavailable
  -> local action remains successful
  -> status shows Sync failed / retrying
  -> automatic retry continues
  -> admin may use Retry now or resolve a conflict
```

#### Supported synchronization directions

| Data area | Mini's n More to Zoho | Zoho to Mini's n More | Conflict rule |
|---|---|---|---|
| Products, variants, SKU details and images | Create/update supported fields | Full/incremental import and webhook pull | Configurable field-level authority; bulk changes require preview |
| Price, tax and catalog status | Push approved local changes | Pull approved external changes | Detect concurrent edits; never silently overwrite |
| Stock | Push committed local inventory movements/balances | Pull external balances/adjustments | Reconcile differences; applying an external adjustment requires an audited rule/action |
| Customers/contact mappings | Create/update as required for documents | Pull permitted contact fields | Match using stored external ID plus validated fallback keys |
| Orders and line items | Create/update/cancel from local actions | Pull Zoho document IDs/status and optionally import approved external orders | Existing local order and price snapshots cannot be silently replaced |
| Invoices and payments | Create/update from confirmed local transactions | Pull document number, status and reconciliation data | Payment gateway/local evidence controls customer payment state |
| Refunds and credit notes | Create automatically after local refund outcome | Pull credit-note number/status | Zoho failure never changes a confirmed gateway/manual refund outcome |
| Shipment and tracking | Push locally recorded XpressBees/manual shipment data and accepted carrier transitions where supported | Pull only for reconciliation/migration or external shipments | XpressBees event controls physical movement for XpressBees shipments; accept forward-only valid transitions and flag regressions |
| Cancellation, return and exchange outcomes | Push related order, credit-note, stock and reference updates | Pull acknowledgement/reconciliation data | Local case decision remains authoritative |

“Sync everything” means every supported and authorized mapped field in these areas, not an uncontrolled database overwrite. Secrets, internal notes, evidence files and fields with no valid Zoho destination must remain local.

#### Admin sync controls

- connection and organization health;
- enable/disable and direction by data area;
- automatic schedule and webhook health;
- `Sync now` for all data or a selected entity/date range;
- push-only, pull-only and bidirectional modes;
- dry-run/preview for full pull or high-impact changes;
- live progress and created/updated/unchanged/conflicted/failed counts;
- per-record external ID, last-synced time and current sync status;
- retry one, retry selected and retry all safe failures;
- conflict comparison with an explicit keep-local, accept-Zoho or merge action; and
- searchable integration audit/history and downloadable error report.

#### Action-to-Zoho reflection examples

| Admin action | Expected automatic Zoho reflection |
|---|---|
| Book a normal XpressBees shipment | Create/update the mapped Zoho package/shipment with provider ID, AWB, carrier, label/tracking link and local reference |
| Cancel an eligible shipment | Cancel/update the mapped Zoho shipment after XpressBees confirms cancellation |
| XpressBees reports pickup, transit, delivery failure, delivery or RTO | Update the mapped Zoho shipment/fulfillment state after the carrier event is validated and stored locally |
| Approve cancellation | Cancel/update the corresponding order/document and release/account for stock as supported |
| Complete online or COD refund | Create/update the appropriate credit note/refund/payment record with local case reference |
| Receive and accept a return | Create the configured return/credit-note and accepted inventory adjustment |
| Mark returned item restockable | Increase the mapped Zoho item/location stock through an idempotent adjustment |
| Discard or return an item to vendor | Post the configured non-sellable/vendor inventory adjustment |
| Approve an exchange | Record the case/reference; reserve stock locally (Zoho is not used as the reservation lock) |
| Dispatch replacement | Create/update the mapped shipment/package and tracking details |
| Close a case | Attach/finalize the local case reference and final accounting status where supported |

The exact Zoho API object used for each action must be documented in the technical mapping specification and covered by integration contract tests.

---

## 18. Admin portal requirements

### 18.1 Unified work queue

Admin must be able to filter and search by:

- case type and reference;
- order number;
- customer name, phone or email subject to permission;
- status and next action;
- request reason;
- assigned owner;
- age/SLA breach;
- carrier and AWB;
- refund state/method;
- integration sync state; and
- created/requested date range.

### 18.2 Case detail

The detail page must show:

- order and payment summary;
- selected items/quantities and immutable price snapshot;
- eligibility result and policy version;
- customer reason/evidence;
- current status, next valid actions and owner;
- reverse and replacement shipment cards;
- inspection and disposition form;
- refund/price-difference calculation and transactions;
- customer-visible and internal communications;
- full audit timeline; and
- a separate integration-health panel for Zoho/Ledgerly.

Actions must be driven by valid backend transitions. The UI must not display an action that the current actor cannot perform, and the backend must still enforce it.

### 18.3 Operational safeguards

- Destructive or monetary actions require confirmation with an impact summary.
- Invalid transitions return a clear conflict message and refreshed case state.
- Long-running provider actions show pending state and may be safely retried.
- Bulk actions are prohibited for refunds and inspection unless separately designed with per-case validation.
- Personally identifiable information and refund proof are visible only to authorized roles.

---

## 19. Customer experience requirements

- Show eligibility and the applicable window before request creation.
- Allow eligible line, quantity and exchange variant selection.
- Display all financial effects before confirmation, including non-refundable charges and price difference.
- Provide a reference immediately after submission.
- Present a normalized timeline rather than raw admin/provider states.
- Display pickup schedule, reverse tracking and replacement tracking when available.
- Allow customer cancellation only before approval/pickup processing according to policy.
- Explain rejection or failed pickup in customer-safe language and give a support path.
- Never expose internal notes, raw provider errors, external accounting state or secrets.
- Meet applicable accessibility and responsive-design standards used by the storefront.

Suggested customer status labels:

| Internal grouping | Customer label |
|---|---|
| Requested/review | Request received |
| Approved/pickup creation | Approved — arranging pickup |
| Pickup scheduled | Pickup scheduled |
| Picked up/in transit | Item on the way to us |
| Received/inspection | Received — under inspection |
| Refund pending | Refund being processed |
| Replacement fulfillment | Preparing replacement |
| Replacement shipped | Replacement on the way |
| Rejected | Request not approved |
| Terminal success | Return completed / Exchange completed / Order cancelled |

---

## 20. Notification requirements

| Event | Customer | Assigned admin/operations |
|---|---|---|
| Request submitted | Email/SMS and in-app | Work-queue alert |
| Approved/rejected | Email/SMS and in-app | Audit event |
| More information requested | Email/SMS and in-app | Reminder/SLA |
| Pickup booked/rescheduled | Email/SMS with schedule/tracking | Work-queue update |
| Pickup failed | Email/SMS with next step | Immediate actionable alert |
| Item picked up | Email/SMS or in-app | Status update |
| Item received/inspection outcome | In-app/email | Warehouse/finance next action |
| Refund submitted/completed/failed | Email/in-app; failure wording must be customer-safe | Finance alert on failure |
| Replacement shipped/delivered | Email/SMS with tracking | Status update/exception alert |
| Case closed | Email/in-app summary | Audit event |

Notifications must use idempotency keys so provider retries do not send duplicate messages. Delivery failure must be logged and retryable but must not roll back the business action.

---

## 21. Reporting and audit requirements

Minimum reports:

- open cases by type, status, owner and age;
- approval/rejection rate by reason and SKU;
- pickup success/failure and carrier turnaround;
- received-to-inspected and inspected-to-refunded turnaround;
- refund pending/failed/unknown with amount at risk;
- exchange reservation age and stock exposure;
- returned-item disposition and loss value;
- replacement delivery performance;
- Zoho/Ledgerly sync backlog and failure age; and
- admin override and manual refund register.

Reports must use local data. Export must respect role permissions and log who exported what and when.

Audit records for monetary, stock, status and permission-sensitive actions must be immutable to ordinary admins and retained according to finance/legal policy.

---

## 22. Data requirements

The final schema may use separate entities or a common case model, but it must support the following concepts.

### 22.1 Case header

- id and public reference;
- type: cancellation, return or exchange;
- order id/reference and customer id snapshot;
- status and resolution;
- reason code, customer note and admin reason;
- policy version and eligibility snapshot;
- owner/team, SLA timestamps and priority;
- created, updated and closed timestamps;
- optimistic-lock version.

### 22.2 Case item

- order item id and purchased SKU snapshot;
- requested, approved, received, accepted and rejected quantities;
- unit price, allocated discount/tax and refundable amount;
- requested replacement SKU and snapshotted price;
- replacement reservation reference;
- inspection result and disposition.

### 22.3 Shipment

- direction: normal forward, reverse or replacement outbound;
- carrier/mode, provider ID, AWB and tracking URL;
- normalized and raw statuses;
- pickup/delivery address snapshot;
- schedule, attempt, handover, delivery and failure timestamps;
- failure reason and provider payload reference.

### 22.4 Financial adjustment

- refund or collection type;
- calculation lines and total;
- provider/method and transaction references;
- local status, idempotency key and attempt history;
- proof for authorized manual settlement.

### 22.5 Inventory movement, audit event and integration event

These must be durable local records linked to the order/case/item. Integration records must retain destination, event ID, attempt count, next retry, external ID, status and sanitized failure detail.

---

## 23. Proposed API capabilities

Exact paths are subject to technical design. The API must provide capabilities equivalent to:

### Customer

- get cancellation/return/exchange eligibility;
- create a cancellation/return/exchange request with line quantities;
- list and view own cases;
- cancel an eligible pending request;
- supply requested information/evidence; and
- retrieve normalized reverse/replacement tracking.

### Admin

- search queues and view complete case detail;
- check forward-shipment serviceability and book XpressBees for a locally packed order;
- download/print the forward AWB label, schedule pickup, cancel an eligible booking, retry booking and refresh tracking;
- approve, reject, request information and assign;
- request/reschedule/cancel pickup or record manual shipment;
- record receipt, inspection and disposition;
- reserve/change/release replacement SKU;
- calculate, issue, retry, reconcile or record manual refund;
- record/collect/refund exchange price difference;
- allocate, pack, dispatch and confirm replacement delivery;
- convert exchange to refund;
- close/override with a mandatory reason; and
- view/retry/ignore integration events.

Every mutating endpoint must accept or derive an idempotency key and enforce current-state validation.

### Webhooks

- Razorpay payment/refund status;
- XpressBees normal forward shipment and delivery/RTO status;
- XpressBees reverse pickup and shipment status;
- XpressBees replacement shipment status; and
- optional Zoho/Ledgerly acknowledgements or inbound reconciliation.

Webhooks must verify authenticity, retain an audit-safe payload reference, deduplicate events and return promptly before asynchronous processing.

---

## 24. Non-functional requirements

### Reliability

- Local admin actions must remain available during optional integration outages.
- Business transactions and their outbox events must commit atomically.
- Background jobs must be safe after process restart.
- Provider timeouts must produce recoverable `unknown/pending` states, never assumed success or failure.

### Performance

- Admin list and detail reads should meet the application's agreed interactive response target without calling Zoho live.
- Submitting a local decision should return after the local transaction and queueing work, not after optional accounting sync.
- Large audit timelines and reports must be paginated.

### Security and privacy

- Enforce JWT/RBAC on all case, refund, evidence and integration endpoints.
- Validate webhook signatures/secrets and implement replay protection.
- Encrypt sensitive credentials and payment/refund evidence at rest where applicable.
- Do not store full card or banking secrets.
- Mask PII in logs and external error reports.
- Record administrative access to sensitive refund data.

### Observability

- Propagate correlation IDs across local actions, jobs and provider calls.
- Emit metrics for case transitions, stuck cases, refund failures, pickup failures and integration backlog.
- Alert on growing dead-letter queues, expired exchange reservations and cases with no valid next action.
- Keep customer-facing errors separate from diagnostic details.

### Accessibility and usability

- Admin and customer workflows must be keyboard-operable and use readable status text in addition to color.
- Confirmation dialogs must state affected items, quantities, stock and money impact.
- Date/time display must use the configured business timezone while storing instants consistently.

---

## 25. Current implementation compared with target

This table is based on the codebase inspected on 2026-09-08.

| Area | Current behavior | Required change |
|---|---|---|
| Pre-purchase sync | Multiple Zoho catalog, image and stock push/pull services exist, but there is no single action-to-Zoho contract/status model covering every admin mutation. | Route every mapped admin business action through a consistent outbox, mapping, status, retry and conflict framework. |
| Advance pre-order | No dedicated pre-order product/capacity/promise workflow was found. | Add explicit pre-order configuration, capacity reservation, promised window, allocation and customer communication. |
| Order operations | Local order/payment/tracking states and Zoho/Ledgerly sync paths exist, but operational dimensions and external dependencies are mixed in places. | Separate payment/fulfillment/shipment/sync states and automatically mirror mapped actions after local commit. |
| Forward shipment/tracking | Admin order reads and the scheduled job pull fulfillment/tracking from Zoho; the UI displays that status. XpressBees is not the forward-shipment authority. | Integrate XpressBees directly with admin for booking, AWB/label, pickup and carrier status; store locally first and automatically mirror accepted changes to Zoho. |
| Cancellation decision | Eligible customer cancellation is executed directly; there is no persistent admin approval case. | Add cancellation case/review workflow, with configurable safe auto-approval. |
| Cancellation refunds | Paid online payments are refunded; collected COD needs manual handling. | Add first-class COD/manual refund record and reconciliation. |
| Return lifecycle | Rich local status enum and manual admin transitions exist. | Add line quantities, complete failure branches and stricter transition/audit model. |
| Return granularity | Return request represents the whole order. | Support item/quantity-level return and partial refund. |
| Exchange granularity | Item-level replacement selection exists. | Add quantity, durable reservation and price-difference settlement. |
| Stock restoration | Stock is restored at admin approval. | Restore sellable stock only after receipt, inspection and restockable disposition. |
| Exchange allocation | Replacement stock is checked at request and allocated after acceptance. | Reserve atomically at approval to prevent sell-out. |
| Reverse logistics | XpressBees client checks serviceability only; status progress is manual. | Implement pickup booking, reverse AWB, webhooks/polling, failed-pickup handling and manual fallback. |
| Replacement logistics | Statuses exist but no complete carrier booking/tracking workflow. | Add outbound booking, AWB/label, tracking events and manual carrier path. |
| Pickup failure | No complete business branch; `FAILED` is primarily refund/downstream failure. | Add explicit pickup failure reason, reschedule/change mode/cancel actions. |
| Refund scope | Return refund can use whole order total. | Calculate and cap refunds at accepted line/quantity allocation. |
| COD return refund | Razorpay path requires a Razorpay payment ID outside demo mode. | Route COD to authorized manual/offline refund workflow. |
| Notifications | Some request/approval/pickup/refund events exist. | Cover pickup failures and full exchange shipped/delivered/completed lifecycle. |
| Accounting dependency | Local records exist, but several operations synchronize accounting platforms in the request path. | Commit locally first; publish all accounting operations through outbox workers. |
| Documentation | Existing lifecycle document states a 7-day window and older statuses. | Update it after policy sign-off; code/UI currently indicate 3 days. |

---

## 26. Migration and delivery plan

### Phase 1: Local authority and correctness

- Approve the policy decisions in section 27.
- Introduce case items/quantities, refund records, inventory movements, audit history and integration outbox.
- Move all Zoho/Ledgerly calls out of synchronous admin transactions.
- Correct premature return stock restoration.
- Add partial-payment/refund state.
- Preserve existing public references and migrate current open cases safely.

### Phase 2: Pre-purchase, pre-order and normal order operations

- Apply the shared action-to-Zoho contract to catalog, price, tax, stock, customer, order, payment, package, shipment and delivery actions.
- Add per-record sync state, conflict resolution, full/incremental push/pull and admin monitoring.
- Separate local order, payment, fulfillment, shipment and accounting-sync dimensions.
- Add optional advance pre-order configuration, capacity reservation, promise changes and release into fulfillment.
- Add order-operation queues for allocation, picking, hand-wrapping, packing, shipment and delivery exceptions.
- Replace Zoho-controlled forward tracking with direct admin-to-XpressBees booking and carrier-to-local status ingestion; keep Zoho as the asynchronous mirror/reconciliation target.

### Phase 3: Complete cancellation and warehouse workflow

- Add persistent cancellation review/auto-approval policy.
- Implement receipt, inspection, disposition and manual COD refunds.
- Add unified admin work queues and full customer timeline.

### Phase 4: Carrier automation

- Add normal forward-order XpressBees booking, AWB/label, pickup, status webhooks/polling, delivery failure and RTO handling.
- Add XpressBees reverse pickup, AWB, status webhook/polling and failure handling.
- Add replacement shipment booking and tracking.
- Retain manual carrier paths and test provider outage behavior.

### Phase 5: Exchange settlement and operational maturity

- Add atomic replacement reservations and expiry controls.
- Add exchange price-difference collection/refund.
- Add reconciliation dashboards, SLA alerts and reports.

### Migration constraints

- Existing open cases must retain their reference, type, current status and history where available.
- A migration must not restore or deduct stock merely because a record is transformed.
- Existing external IDs must remain nullable mappings.
- Transitional reads may support legacy records, but all new writes must use the new rules after cutover.
- Rollout must support feature flags by workflow and integration.

---

## 27. Product decisions required before implementation sign-off

| Decision | Recommended default | Why it matters |
|---|---|---|
| Return window | 3 calendar days from confirmed delivery | Matches current code/UI; existing documentation says 7 and must be reconciled. |
| Cancellation approval | Manual admin review, with optional auto-approval before allocation | Matches the supplied diagram while allowing later operational efficiency. |
| Cancellation granularity | Whole order initially; line quantity where fulfillment supports it | Avoids promising partial cancellation before allocation/packing supports it. |
| Return granularity | Item and quantity level | Prevents whole-order refunds for a single returned item. |
| Replacement reservation | Reserve on admin approval | Prevents the selected replacement selling out during reverse transit. |
| Reservation expiry | Do not expire during active pickup; alert/override after configured SLA | Protects customer promise without hiding aged stock. |
| Refund timing | After accepted inspection by default | Aligns refund to physical verification; exceptions require permission. |
| Original shipping/COD fee | Non-refundable unless entire order is defective/wrong or policy override | Must be displayed in refund calculation. |
| Lower-value exchange | Refund the difference | Transparent and auditable; confirm tax/accounting treatment. |
| Higher-value exchange | Collect difference before dispatch | Avoids shipping unpaid replacement value. |
| Rejected inspection | Return item to customer unless explicit consent/exception | Requires a defined shipping-cost policy. |
| Advance pre-order feature | Enabled only for explicitly configured SKUs | Prevents ordinary out-of-stock items from being sold accidentally. |
| Pre-order payment | Full payment by default; deposit mode only after finance approval | Simplifies refund, invoice and allocation behavior. |
| Pre-order allocation | Oldest confirmed eligible order first, with audited override | Makes limited incoming supply allocation predictable and fair. |
| Zoho sync target | Queue immediately and normally complete within 5 minutes | Defines an operational expectation while keeping admin actions non-blocking. |
| Conflict authority | Local operational state wins; catalog field authority is configurable | Protects fulfillment/refund truth while supporting deliberate Zoho catalog management. |
| Zoho role | Automatically synchronized, bidirectional external system managed from the local admin; never a runtime prerequisite or silent operational authority | Ensures every admin action reflects in Zoho without requiring duplicate work in Zoho. |
| Ledgerly role | Apply the same optional adapter boundary unless business explicitly selects it as inventory authority | Prevents replacing one hard dependency with another. |

---

## 28. Definition of done

The feature is complete only when:

1. Approved policy decisions are implemented consistently in backend, admin, storefront and documentation.
2. Pre-purchase, advance pre-order, normal fulfillment and all three post-order workflows pass happy-path, rejection, provider-failure, retry, duplicate and unauthorized-action tests.
3. Item/quantity refunds and inventory movements reconcile exactly to the original order snapshot.
4. Returned items never become sellable before a restockable inspection outcome.
5. Exchange reservations prevent overselling and are released exactly once on non-fulfillment.
6. XpressBees automation and manual carrier fallback both pass end-to-end tests.
7. Online and COD/manual refund paths both pass reconciliation tests.
8. Customer/admin notifications cover all material states without duplicates.
9. Audit history identifies every actor, transition, money movement, stock movement and override.
10. The Zoho-disabled independence test in section 17.4 passes in CI/UAT.
11. Optional integration failures appear in an operations queue and do not change local case outcomes.
12. Existing open cases are migrated without unplanned stock or refund side effects.
13. Operational runbooks, API contract and lifecycle documentation are updated and approved.
14. Every mapped admin action automatically reaches Zoho, and its returned external identifiers/statuses are visible locally.
15. Full, incremental and webhook-based Zoho push/pull tests pass for success, duplicate, conflict, timeout and retry scenarios.
16. Catalog, pre-order, payment, warehouse, shipping and delivery admin actions pass action-to-Zoho contract tests.

---

## Appendix A: Required status separation

Do not overload one status field with unrelated concerns. At minimum, expose separate status dimensions:

| Dimension | Examples |
|---|---|
| Case | requested, approved, rejected, processing, completed, cancelled |
| Reverse logistics | not_required, booking, scheduled, pickup_failed, picked_up, in_transit, received |
| Inspection | pending, accepted, partially_accepted, rejected |
| Refund | not_required, pending, processing, failed, refunded, partially_refunded |
| Replacement fulfillment | not_required, reserved, allocated, packing, shipped, delivered, failed |
| Accounting integration | not_configured, pending, synced, failed, ignored |

The API may also return a derived customer-facing summary, but the underlying dimensions must remain independently queryable and auditable.

## Appendix B: Minimum negative test catalogue

- Request outside policy window.
- Request for undelivered, cancelled or unpaid quantity.
- Two simultaneous cases for the same line quantity.
- Admin approves after the order was shipped concurrently.
- Last replacement unit is requested by two exchange cases.
- XpressBees create-pickup timeout followed by retry.
- Duplicate/out-of-order carrier webhook.
- Customer unavailable for pickup multiple times.
- Received quantity differs from expected quantity.
- Inspection partially accepts a multi-quantity return.
- Razorpay refund timeout with later success webhook.
- Duplicate refund submission.
- Manual COD refund without sufficient permission/reference.
- Zoho credentials missing, token expired, rate limited and network blocked.
- Zoho webhook attempts to regress a locally completed state.
- Notification provider fails after a successful local transition.
- Admin submits stale case version after another admin changes it.
- Exchange converted to refund after replacement was reserved.
- Replacement delivery fails/returns to origin.
