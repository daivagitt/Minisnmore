# Change Log M2 — Post-Order Workflows (September 2026)

## Summary

| NO | Module            | Type        | Description                                                                 |
|----|-------------------|-------------|-----------------------------------------------------------------------------|
| 1  | Cancel            | New Feature | Customers can request cancellation from their order page before dispatch    |
| 2  | Cancel            | New Feature | Admin approval cancels the order, restores stock, and starts an online refund |
| 3  | Cancel            | New Feature | COD cancels use a manual refund record (no automatic payment-gateway refund) |
| 4  | Cancel            | New Feature | Admin can reject a cancel request; the order continues as normal            |
| 5  | Returns           | New Feature | Customers can return delivered items within 3 days of delivery              |
| 6  | Returns           | New Feature | Admin return desk: approve, arrange pickup, inspect, then refund            |
| 7  | Returns           | New Feature | Failed pickup can be rescheduled or closed without a refund                 |
| 8  | Exchange          | New Feature | Customers can request a size/colour exchange when a replacement is in stock |
| 9  | Exchange          | New Feature | Admin runs exchange from pickup through replacement delivery and close      |
| 10 | Exchange          | New Feature | Price difference can be collected, refunded, or waived before shipping      |
| 11 | Exchange          | New Feature | After inspection, admin can reject the exchange or switch it to a refund    |
| 12 | Exchange          | New Feature | Failed pickup can cancel the exchange with customer notification            |
| 13 | ExpressBees       | New Feature | Return pickup and exchange outbound booking with live tracking updates      |
| 14 | Orders — Shipping | New Feature | Forward shipment can be booked even if Zoho sync is delayed or failed       |
| 15 | Emails            | New Feature | Customers get emails for cancel, return, and exchange status changes        |
| 16 | Admin — Access    | New Feature | Staff roles control who can approve cancels, returns, exchanges, and ship   |

---

## 1. Cancel — Customer cancel request

**Module Name:** Cancel  

**Type:** New Feature  

**What Was Wrong:**  
After placing an order, a customer had no way on the website to ask for cancellation before the parcel went out. Staff handled cancels over phone or chat, with no clear status on the order and no automatic email from Mini's n More.

**Short Description:**  
On **My Account → Orders**, the customer opens an order that has **not yet been dispatched** and submits a **Cancel request** with a short reason (at least 3 characters). The order shows as **cancellation requested**, the request appears in the admin cancellations list, and the customer receives a confirmation email. Cancellation is not offered once the order has already been dispatched. Courier pickup is not part of this cancel flow.

**Manual Testing Steps:**

1. Log in as a **customer** with an order that is paid/confirmed and **not yet shipped**.
2. Open **Account → Orders** and open that order.
3. Choose **Cancel order**, type a reason of at least 3 characters, and submit.
4. **Expected:** The order shows **cancellation requested**, and the customer receives a cancel-request email.
5. As admin, open **Cancellation requests** and confirm the same request is listed.
6. Try to submit another cancel on the same order while one is still pending.
7. **Expected:** The second request is not allowed.
8. On an order that is already **shipped or delivered**, try cancel.
9. **Expected:** Cancel is not allowed.
10. Try submitting with a blank or 1–2 character reason.
11. **Expected:** The form asks for a longer reason.

---

## 2. Cancel — Admin approve (online paid order)

**Module Name:** Cancel  

**Type:** New Feature  

**What Was Wrong:**  
Approving a customer cancel, putting stock back, and starting the online refund were not one clear action in admin.

**Short Description:**  
On **Admin → Cancellation requests**, staff choose **Approve & cancel order** for a pending request on an **online paid** order. The order becomes **Cancelled**, stock for those items is put back on hand, the online refund is started automatically, and the customer receives an acceptance / refund email. Accounting sync with Zoho or Ledgerly continues in the background and does not block the cancel.

**Manual Testing Steps:**

1. Create a pending customer cancel on an **online paid** order (see item 1).
2. Log in as admin and open **Cancellation requests**.
3. Open that request and choose **Approve & cancel order**.
4. **Expected:** Order status is **Cancelled**.
5. **Expected:** Stock for the cancelled items is available again.
6. **Expected:** An online refund is recorded for that payment (not created twice if you approve again by mistake).
7. **Expected:** Customer receives the cancellation accepted / refund email.
8. Confirm the order no longer appears as open for packing or shipping.

---

## 3. Cancel — Admin approve (COD) and record refund

**Module Name:** Cancel  

**Type:** New Feature  

**What Was Wrong:**  
For cash-on-delivery orders, there was no guided way to approve a cancel and record that money was returned to the customer when COD had already been collected.

**Short Description:**  
When admin approves a cancel on a **COD** order, the payment gateway is **not** called. If COD was already collected, staff **record the manual refund** (method and reference). The order is cancelled, stock is restored, and the customer is informed. The same refund cannot be recorded twice.

**Manual Testing Steps:**

1. Create a pending cancel on a **COD** order that was marked as **collected**.
2. In **Cancellation requests**, approve the cancel.
3. Record the manual refund with a payment method and a reference of at least 4 characters.
4. **Expected:** Order is cancelled; refund shows as recorded once; customer is notified.
5. Try to record the same COD refund again.
6. **Expected:** The system blocks the duplicate.
7. On a COD order that was **not** collected, confirm you are not forced through the “collected refund” path incorrectly.
8. Confirm no courier booking is created for a cancel.

---

## 4. Cancel — Admin reject

**Module Name:** Cancel  

**Type:** New Feature  

**What Was Wrong:**  
There was no in-app way for staff to decline a cancel request so packing and shipping could continue, with a clear message to the customer.

**Short Description:**  
On a pending cancellation, admin chooses **Reject** and enters a rejection reason (at least 5 characters). The cancel request is closed as rejected, the **order continues**, and the customer receives a **cancellation rejected** email.

**Manual Testing Steps:**

1. Create a pending customer cancel on an open order.
2. In **Cancellation requests**, choose **Reject**.
3. Enter a reason of at least 5 characters and confirm.
4. **Expected:** The request shows as rejected; the order is still active (not cancelled).
5. **Expected:** Customer receives the rejection email.
6. Try rejecting with a very short reason.
7. **Expected:** The system asks for a longer reason.
8. Confirm staff can still pack and ship that order after rejection.

---

## 5. Returns — Customer return request

**Module Name:** Returns  

**Type:** New Feature  

**What Was Wrong:**  
After delivery, customers could not start a return from their account with line quantities, a reason, and a clear status for staff to work.

**Short Description:**  
For a **paid and delivered** order, within **3 days of delivery**, the customer can open **Return** on the order, choose which items and quantities to send back, and submit a reason (at least 10 characters). The return starts as **Requested**, the customer gets an email, and the case appears under **Admin → Returns**. Returns are blocked if the order is unpaid, cancelled, not yet delivered, outside the 3-day window, or already has an open return/exchange that conflicts.

**Manual Testing Steps:**

1. Use a **paid, delivered** order whose delivery was within the last **3 days**.
2. As the customer, open the order and start a **Return**.
3. Select quantity for at least one line, enter a reason of 10+ characters, and submit.
4. **Expected:** Status shows return requested; customer gets an email; admin sees the case under **Returns**.
5. On an order delivered **more than 3 days** ago, try the same return.
6. **Expected:** Return is not allowed (window expired).
7. Try return on an unpaid, cancelled, or not-yet-delivered order.
8. **Expected:** Return is blocked with a clear message.
9. With a return already open, try starting another conflicting return.
10. **Expected:** The system blocks the duplicate open case.

---

## 6. Returns — Admin approve through refund

**Module Name:** Returns  

**Type:** New Feature  

**What Was Wrong:**  
Staff had no single returns desk to approve a return, arrange collection, inspect the goods, decide whether items go back to stock, and then refund the customer.

**Short Description:**  
On **Admin → Returns**, staff work each case in order: **Approve → Book pickup → Mark received → Inspect → Accept** (with disposition such as restockable or discard) **→ Refund**. Online orders are refunded through the payment gateway. Sellable stock increases **only** for units marked restockable. If accounting sync fails after the local refund is done, the refund status on Mini's n More still stays refunded so the customer is not left waiting.

**Manual Testing Steps:**

1. Open a **Requested** return in **Admin → Returns**.
2. **Approve**, then **Book pickup** (ExpressBees or manual / customer self-ship).
3. When the parcel arrives, **Mark received**, then **Inspect**.
4. **Accept** with disposition — try restockable on one unit and discard on another if you have partial quantity.
5. **Process refund**.
6. **Expected:** Return shows **Refunded**; online payment shows a refund; only restockable units increase sellable stock.
7. Confirm the customer receives the refund-related email.
8. Confirm you cannot jump ahead (for example book pickup before approve).

---

## 7. Returns — Failed pickup: reschedule or close

**Module Name:** Returns  

**Type:** New Feature  

**What Was Wrong:**  
If the courier could not collect the return (customer not available, no product ready, or customer changed mind), staff had no clear choice to try again or close the case without issuing a refund.

**Short Description:**  
When pickup fails, admin can **reschedule** a new pickup (the return stays open) or **close** the return (no refund). The customer is notified based on the outcome. Pickup cannot be booked before the return is approved.

**Manual Testing Steps:**

1. Take a return that is approved or already has pickup scheduled.
2. Mark **pickup failed** with a reason such as customer not available.
3. Choose **Reschedule**, then book pickup again.
4. **Expected:** The return stays open; no refund yet; a new pickup can be arranged.
5. On another failed pickup, choose **Close**.
6. **Expected:** The return is closed; **no** refund is issued; the customer is informed.
7. Try booking pickup while the return is still only **Requested**.
8. **Expected:** Booking is not allowed until after approve.

---

## 8. Exchange — Customer exchange request

**Module Name:** Exchange  

**Type:** New Feature  

**What Was Wrong:**  
Customers could not ask for a different size or colour from their order page, with an automatic stock check and a case for admin to review.

**Short Description:**  
On a delivered, eligible item that has sizes/colours, the customer chooses **Exchange**, picks the **replacement size/colour**, and submits a reason (at least 10 characters). The request is accepted only if that replacement is **in stock** and the same delivery rules as returns apply (paid, delivered, within **3 days**, no blocking open case). The case appears under **Admin → Returns** as an **Exchange**, and the customer gets an exchange-requested email.

**Manual Testing Steps:**

1. Use a delivered order with a product that has another size or colour **in stock**.
2. As the customer, open **Exchange** on that line, pick the replacement, and submit a valid reason.
3. **Expected:** An exchange case is created; the customer gets an email; admin sees **Exchange** in the returns list.
4. Repeat with a replacement that is **out of stock**.
5. **Expected:** Exchange is not allowed.
6. Try exchange on a product that has **no** size/colour options.
7. **Expected:** Exchange is not offered or is blocked.
8. With an open return on the same order/item, try exchange.
9. **Expected:** Exchange is blocked until the other case is finished.

---

## 9. Exchange — Admin path to replacement delivery

**Module Name:** Exchange  

**Type:** New Feature  

**What Was Wrong:**  
There was no end-to-end admin flow to approve an exchange, collect the old item, prepare the replacement, ship it with tracking, and close the case when delivered.

**Short Description:**  
Admin **Approves** the exchange (the replacement is reserved so it cannot be sold twice), books **return pickup**, receives and inspects the old item, accepts it with a disposition, settles any price difference if needed, then packs and ships the replacement. When the replacement is delivered, admin **Completes** the exchange. The customer receives tracking updates for the new parcel.

**Manual Testing Steps:**

1. Open a pending exchange and **Approve**.
2. **Expected:** Status is approved; the replacement quantity is reserved (no longer free to sell to someone else).
3. Book reverse pickup and move the case through received → inspected → accepted.
4. After price settlement is clear (see item 10), allocate / ready to ship and book the **outbound** delivery.
5. **Expected:** Tracking is available for the replacement; customer can follow delivery progress.
6. After delivery, choose **Complete**.
7. **Expected:** Exchange shows **Completed**.
8. If two staff try to approve when only one unit remains, only one approval should succeed.

---

## 10. Exchange — Price difference before shipping

**Module Name:** Exchange  

**Type:** New Feature  

**What Was Wrong:**  
When the replacement item cost more or less than the original, there was no place in admin to record collection, partial refund, or a waiver before the new item shipped.

**Short Description:**  
After the returned item is accepted, admin uses **Settle price difference** when needed:
- replacement **higher** → record payment collected (or waive),
- replacement **lower** → record the difference refunded,
- **same price** → no payment step required.

Outbound shipping is blocked until settlement is done (or marked not required / waived).

**Manual Testing Steps:**

1. Take an exchange where the replacement price is **higher** than the original.
2. After accept, open **Settle price difference**.
3. Record method and transaction reference, or mark **waived**.
4. **Expected:** You cannot book outbound until settlement is saved.
5. Repeat with a **lower** priced replacement and record the difference refund.
6. Repeat with the **same** price.
7. **Expected:** Settlement shows as not required; you can move to ship without collecting money.
8. Try saving settlement with a mismatched amount or missing reference when payment is required.
9. **Expected:** The form asks for the correct amount and details.

---

## 11. Exchange — Reject after inspection or convert to refund

**Module Name:** Exchange  

**Type:** New Feature  

**What Was Wrong:**  
Once the returned item reached the warehouse, staff could not reject a poor-quality return, or decide to refund money instead of sending a replacement, without improvising outside the system.

**Short Description:**  
After inspection, admin can **Reject** the exchange (customer gets an exchange-rejected email; the reserved replacement is released) or **Convert to refund** so the customer is refunded instead of receiving a new item. The normal “return refund only” action is not used on exchanges — staff use **Convert to refund**.

**Manual Testing Steps:**

1. Move an exchange through pickup to **Inspected**.
2. Choose **Reject** after inspection.
3. **Expected:** Exchange is rejected; customer gets the rejection email; reserved replacement stock is freed.
4. On another accepted exchange, choose **Convert to refund** and complete the refund.
5. **Expected:** Case ends as refunded; no replacement shipment is required.
6. Confirm the customer receives the correct refund communication (not an “exchange shipped” email).

---

## 12. Exchange — Failed pickup cancels the exchange

**Module Name:** Exchange  

**Type:** New Feature  

**What Was Wrong:**  
If the courier failed to collect the item for an exchange, there was no clean way to cancel the exchange and tell the customer.

**Short Description:**  
When reverse pickup fails, admin can **close** the exchange (same reasons as returns: not available, no product, changed mind). The exchange is cancelled, the reserved replacement is released, and the customer receives an **exchange cancelled** email. Admin can also **reschedule** instead of closing, same as returns.

**Manual Testing Steps:**

1. Approve an exchange and schedule pickup.
2. Mark pickup failed, then **Close** (cancel exchange).
3. **Expected:** Exchange is cancelled; customer gets the cancelled email; replacement is no longer reserved.
4. On another case, mark failed and choose **Reschedule**.
5. **Expected:** Exchange stays open; a new pickup can be booked.
6. Confirm a normal **order cancel** still never creates a courier pickup.

---

## 13. ExpressBees — Return pickup and exchange outbound

**Module Name:** ExpressBees  

**Type:** New Feature  

**What Was Wrong:**  
Return collections and exchange deliveries were arranged outside admin, so tracking numbers and courier status updates were easy to lose.

**Short Description:**  
From **Admin → Returns**, staff can **Book pickup** for approved returns and exchanges, and book **outbound delivery** for a ready replacement. Tracking numbers are saved on the case. Courier status updates (including failed pickup and delivered) flow back into admin so the next action is clear. If the courier service is temporarily unavailable, the case stays in a safe status and staff can retry. Order **cancel** never books a courier shipment.

**Manual Testing Steps:**

1. After approving a return, choose **Book pickup**.
2. **Expected:** Pickup / tracking details appear on the case; customer is notified that pickup is arranged.
3. Simulate or receive a **pickup failed** update.
4. **Expected:** Admin can reschedule or close; the case status stays sensible.
5. On a ready exchange, book **outbound** delivery.
6. **Expected:** Tracking is available for the replacement parcel.
7. When the courier reports **delivered**, complete the exchange.
8. With courier credentials temporarily wrong or offline, try booking again.
9. **Expected:** A clear error; previous good status is kept; retry works after fix.
10. Approve an order **cancel** and confirm no pickup or outbound is booked.

---

## 14. Orders — Ship without waiting on Zoho

**Module Name:** Orders — Shipping  

**Type:** New Feature  

**What Was Wrong:**  
Forward delivery could be stuck when Zoho or Ledgerly sync was slow or failed, even though shipping is managed from Mini's n More admin.

**Short Description:**  
From **Admin → Orders**, staff can book a **forward ExpressBees shipment** (tracking number and carrier details) even if Zoho or Ledgerly sync is delayed or failed. Background accounting sync continues separately and does not block the ship screen.

**Manual Testing Steps:**

1. Open an order that is ready to ship, where Zoho/Ledgerly sync is pending or failed.
2. Book the ExpressBees forward shipment from the order screen.
3. **Expected:** Tracking is created and the customer-facing order progress can move forward.
4. Confirm the order page shows the local tracking details.
5. Repeat while Zoho is unavailable.
6. **Expected:** Shipping still succeeds; you are not forced to fix Zoho first.

---

## 15. Emails — Cancel, return, and exchange updates

**Module Name:** Emails  

**Type:** New Feature  

**What Was Wrong:**  
Customers did not consistently receive Mini's n More emails when a cancel, return, or exchange moved to the next step.

**Short Description:**  
Customers now get emails for the main status changes, including:
- **Cancel** — request received, accepted, rejected  
- **Return** — requested, approved/rejected, pickup updates, refund  
- **Exchange** — requested, approved, rejected, cancelled, out for delivery / delivered, closed  

If email sending fails, the **order or case status still saves**; staff are not rolled back. Cancel emails must not go out for returns, and exchange emails must not be mixed with return templates.

**Manual Testing Steps:**

1. Run one cancel request → approve (or reject) and check the matching cancel emails only.
2. Run one return through approve → refund and check return emails (not exchange).
3. Reject one exchange after inspection and close one exchange after failed pickup.
4. **Expected:** Exchange rejected / exchange cancelled emails (not return templates).
5. In a safe UAT setup, cause email sending to fail, then approve a cancel.
6. **Expected:** The order still cancels; status is not undone because mail failed.
7. Spot-check that emails show the correct order number and, where relevant, tracking link.

---

## 16. Admin — Who can manage post-order work

**Module Name:** Admin — Access  

**Type:** New Feature  

**What Was Wrong:**  
It was unclear which staff logins could approve cancellations, work returns/exchanges, or book shipments.

**Short Description:**  
Under **Admin → RBAC / Users**, roles decide who can **view** versus **change** cancellations, returns, exchanges, and shipping. Full-access staff can approve, book pickup, refund, and ship. Limited roles see information only (action buttons hidden). Staff without permission cannot change a case. Customer logins cannot use admin screens or admin actions.

**Manual Testing Steps:**

1. Log in as a **full-access** admin and confirm cancel approve, return actions, and order ship all work.
2. Log in as a **limited** role.
3. **Expected:** Change buttons for cancel / returns / ship are hidden or unavailable.
4. Confirm a limited user cannot complete an approve or refund that their role forbids.
5. Confirm a normal **customer** account cannot open admin cancellation or returns pages.
6. Re-check one happy path from items 2–14 still works for a privileged user after roles are set.

---

## Notes for QA

- Detailed Pass/Fail checklist: [`docs/m2-qa-test-cases.md`](./docs/m2-qa-test-cases.md) and [`docs/m2-qa-test-cases.xlsx`](./docs/m2-qa-test-cases.xlsx).
- Return and exchange window: **3 days from delivery**.
- Mini's n More admin is the system of record; Zoho, Ledgerly, and ExpressBees run alongside and should not block these flows where designed.
