.TL
Order Cancellation, Returns, Exchanges, and Tracking Status
.AU
Mini's n More - Functional and Technical Guide
.DA
28 September 2026
.PP
This document explains how order cancellation, returns, exchanges, refunds, and shipment tracking work in the current Mini's n More implementation. It distinguishes the main order status from customer-facing tracking and from cancellation or return case statuses.
.
.NH 1
Status model: three separate layers
.PP
The system maintains three related but independent status layers.
.IP "1." 4
Main order status: PENDING, CONFIRMED, HAND_WRAPPED, CANCELLED, or FULFILLED.
.IP "2." 4
Customer-facing tracking status: pending, confirmed, hand_wrapped, awaiting_pickup, shipped, out_for_delivery, delivery_failed, delivered, or cancelled.
.IP "3." 4
Case status: a cancellation request or a return/exchange request has its own lifecycle.
.PP
The distinction matters. For example, returning or exchanging a delivered order does not change the original order's delivery tracking from delivered. Instead, a separate return/exchange case records the reverse pickup and replacement journey.
.
.NH 1
Normal order tracking
.NH 2
Customer-facing progression
.DS L
pending
  -> confirmed
  -> hand_wrapped
  -> awaiting_pickup
  -> shipped
  -> out_for_delivery
  -> delivered
.DE
.PP
A delivery exception can produce this branch:
.DS L
out_for_delivery -> delivery_failed -> out_for_delivery -> delivered
.DE
.PP
The tracking_status response field is calculated when an order response is built. It is not maintained as a single independent database status.
.
.NH 2
How tracking_status is resolved
.IP "cancelled" 18
The main order is CANCELLED, or Ledgerly reports a cancelled or void status.
.IP "pending" 18
The order is PENDING and payment is not paid. COD fulfilment may still advance even while payment remains pending.
.IP "awaiting_pickup" 18
An ExpressBees AWB exists, but the carrier has not recorded a pickup or transit scan.
.IP "delivered" 18
The main order is FULFILLED, or a trusted external source reports delivery. Locally booked shipments use their local carrier status instead of being overwritten by Zoho.
.IP "out_for_delivery" 18
The local carrier reports an out-for-delivery milestone.
.IP "delivery_failed" 18
The local carrier reports an undelivered or failed-delivery milestone.
.IP "shipped" 18
The carrier reports pickup/transit, an applicable tracking number or shipment date exists, or Zoho/Ledgerly reports a shipped state.
.IP "hand_wrapped" 18
The main order is HAND_WRAPPED and no later fulfilment signal exists.
.IP "confirmed" 18
The order is CONFIRMED, or it is a paid transitional PENDING record.
.
.NH 2
ExpressBees booking versus physical shipment
.PP
Generating an ExpressBees AWB only creates a booking and label. It does not mean the parcel has physically left the warehouse.
.DS L
AWB created:
  shipment_carrier_status = booked
  tracking_status = awaiting_pickup

First physical pickup/transit scan:
  shipment_carrier_status = picked_up / in_transit / hub...
  tracking_status = shipped
.DE
.PP
When ExpressBees reports delivered, the system records the delivery timestamp, changes the main order to FULFILLED, and exposes tracking_status as delivered.
.PP
Carrier scans are not always received in chronological order. Once a parcel is out for delivery, a weaker hub or in-transit scan is ignored so the customer tracker does not regress to shipped. A delivery-failed scan may follow out-for-delivery, and a later reattempt may return it to out-for-delivery. Delivered is terminal.
.
.NH 2
Tracking update sources
.PP
Order tracking can be updated by:
.IP "-" 4
ExpressBees shipment webhooks.
.IP "-" 4
ExpressBees tracking API pulls.
.IP "-" 4
Zoho package, shipment, and sales-order webhooks.
.IP "-" 4
Scheduled Zoho tracking pulls, normally every 15 minutes.
.IP "-" 4
Scheduled Ledgerly tracking pulls.
.IP "-" 4
A stale-data refresh when an authenticated customer opens order details.
.PP
Authenticated order details attempt to refresh external tracking when it is older than five minutes. Public order tracking returns cached fields and deliberately does not block while calling Ledgerly, Zoho, or ExpressBees.
.
.NH 1
Order cancellation scenarios
.NH 2
Cancellation request lifecycle
.DS L
REQUESTED -> APPROVED
          -> REJECTED
          -> WITHDRAWN
.DE
.
.NH 2
Customer submits a cancellation request
.PP
Submitting a cancellation request creates a separate REQUESTED case. It does not immediately cancel the order.
.DS L
cancellation_request.status = REQUESTED
order.status                = unchanged
tracking_status             = unchanged
.DE
.PP
While the request is pending, the customer cannot submit another pending request. Normal shipment booking is placed on hold. An administrator may override this hold only by supplying an audited reason. The customer can withdraw the request while it is still pending.
.
.NH 2
Administrator approves before dispatch
.PP
Approval performs the real cancellation:
.DS L
cancellation_request.status = APPROVED
order.status                = CANCELLED
tracking_status             = cancelled
.DE
.PP
Approval also saves the reason, restores reserved inventory, synchronizes the cancellation to Zoho and Ledgerly when applicable, writes lifecycle events, and sends the customer notification.
.PP
For a paid online order, the Razorpay refund is attempted before the order is marked cancelled. After success, payment_status becomes REFUNDED and the refund identifier is recorded.
.PP
For collected COD, the order can be cancelled before the manual refund is recorded:
.DS L
order.status                = CANCELLED
cancellation_request.status = APPROVED
payment_status              = PAID
cod_refund_pending          = true
.DE
.PP
An administrator must then record the refund method and transaction reference. At that point payment_status changes to REFUNDED.
.
.NH 2
Administrator rejects the request
.DS L
cancellation_request.status = REJECTED
order.status                = unchanged
tracking_status             = unchanged
.DE
.PP
The rejection reason is stored and communicated to the customer. Fulfilment can continue normally.
.
.NH 2
Customer withdraws the request
.DS L
cancellation_request.status = WITHDRAWN
order.status                = unchanged
tracking_status             = unchanged
.DE
.PP
With no REQUESTED cancellation remaining, the shipment hold is removed.
.
.NH 2
Cancellation after dispatch
.PP
An order cannot be cancelled after the system resolves tracking to shipped, out_for_delivery, delivery_failed, or delivered. The customer must wait for delivery and use the return workflow if eligible.
.
.NH 2
Shipment cancellation is not order cancellation
.PP
Cancelling a carrier booking is a separate operation. It cancels the ExpressBees booking when applicable and clears the AWB, carrier, tracking URL, shipment date, label, provider request, and logistics mode. It does not set the main order to CANCELLED.
.PP
After carrier-booking cancellation, calculated tracking normally returns to confirmed or hand_wrapped. The order can then be rebooked or separately cancelled through the cancellation-request workflow.
.
.NH 1
Return scenarios
.NH 2
Eligibility
.PP
The current implementation requires all of the following:
.IP "-" 4
Returns and refunds are enabled.
.IP "-" 4
The order is paid.
.IP "-" 4
The order is not cancelled.
.IP "-" 4
Delivery can be recognized by the system.
.IP "-" 4
The request is within the configured three-day return window.
.IP "-" 4
No other open return or exchange already exists for the order.
.
.NH 2
Successful return lifecycle
.DS L
REQUESTED
  -> APPROVED
  -> PICKUP_SCHEDULED
  -> PICKED_UP
  -> RECEIVED
  -> INSPECTED
  -> ACCEPTED
  -> REFUNDED
.DE
.PP
PICKUP_REQUESTED is also available where scheduling and booking are separate operational steps.
.
.NH 2
Request creation and amounts
.PP
The customer selects return lines and quantities. The system snapshots requested quantity, item gross amount, allocated order discount, and expected refundable amount. This supports partial-item and partial-quantity returns.
.PP
The original delivered order remains FULFILLED with tracking_status delivered. The new return case independently starts at REQUESTED.
.
.NH 2
Review decisions
.PP
An administrator can approve or reject the request while it is REQUESTED. A rejected request closes without changing the original order or payment. The customer may cancel their own case only while it remains REQUESTED, producing case status CANCELLED.
.
.NH 2
Reverse pickup tracking
.PP
Booking a pickup stores logistics mode, carrier name, reverse AWB, tracking URL, pickup window, and provider reference. The case becomes PICKUP_SCHEDULED.
.PP
ExpressBees reverse milestones map as follows:
.IP "pickup_scheduled / out_for_pickup" 28
PICKUP_SCHEDULED
.IP "picked_up" 28
PICKED_UP
.IP "received / warehouse_received" 28
RECEIVED
.IP "pickup_failed / customer_na / no_product" 28
PICKUP_FAILED
.PP
Manual and self-ship cases use equivalent administrator actions.
.
.NH 2
Pickup failure
.DS L
PICKUP_SCHEDULED -> PICKUP_FAILED
.DE
.PP
The administrator can reschedule, which returns the case to APPROVED before booking again, or close the failed case as CANCELLED. Closing a failed exchange pickup also releases any reserved replacement stock.
.
.NH 2
Inspection and partial acceptance
.PP
After warehouse receipt, the case advances from RECEIVED to INSPECTED. The administrator may accept or reject it. For multi-line returns, accepted quantity may be lower than requested quantity, and refund values are recalculated proportionally. Accepted restockable units are returned to inventory.
.
.NH 2
Refund outcomes
.IP "Online payment" 20
The refund is processed through Razorpay.
.IP "Collected COD" 20
The administrator must record the refund method and a payment reference.
.IP "Uncollected COD" 20
The case may finalize without sending a payment refund.
.IP "Full refund" 20
The original order payment_status becomes REFUNDED.
.IP "Partial refund" 20
The original order payment_status becomes PARTIALLY_REFUNDED.
.IP "Refund failure" 20
The case becomes FAILED and the administrator can retry.
.PP
After successful refund processing, the return case becomes REFUNDED.
.
.NH 1
Exchange scenarios
.NH 2
Successful exchange lifecycle
.DS L
REQUESTED
  -> APPROVED
  -> PICKUP_SCHEDULED
  -> PICKED_UP
  -> RECEIVED
  -> INSPECTED
  -> ACCEPTED
  -> ITEM_ALLOCATED
  -> REPACKING
  -> READY_TO_SHIP
  -> SHIPPED
  -> DELIVERED
  -> COMPLETED
.DE
.
.NH 2
Request validation
.PP
The customer selects the original order item, replacement size, replacement colour, and reason. The system verifies that the product supports variants, the selected replacement is sellable, no full return is already open for the order, and no exchange is already open for the same item.
.
.NH 2
Approval, reservation, and price difference
.PP
Approving an exchange reserves replacement stock immediately. The system calculates the original item's net amount after discount, the replacement amount, and the price difference.
.IP "Replacement costs more" 24
The exact difference must be COLLECTED.
.IP "Replacement costs less" 24
The exact difference must be REFUNDED. For prepaid Razorpay orders this is issued through Razorpay. If the gateway is unavailable or the refund was already issued outside the app, an administrator may record a manual method and transaction reference instead.
.IP "Same price" 24
Settlement is NOT_REQUIRED.
.IP "Operational exception" 24
An administrator may explicitly mark the difference WAIVED.
.PP
Replacement allocation and outbound shipment booking are blocked while settlement remains PENDING.
.
.NH 2
After inspection
.PP
An accepted exchange advances through ITEM_ALLOCATED, REPACKING, and READY_TO_SHIP. If the original item is restockable, its inventory is restored.
.PP
If the replacement can no longer be supplied, an accepted exchange may be converted into a refund. A failed refund conversion may be retried from FAILED.
.
.NH 2
Replacement shipment tracking
.PP
The replacement shipment stores its own outbound logistics mode, carrier, AWB, tracking URL, label URL, and provider request identifier. Booking a manual or ExpressBees outbound shipment changes READY_TO_SHIP to SHIPPED.
.PP
ExpressBees replacement milestones map as follows:
.IP "shipped / in_transit" 24
SHIPPED
.IP "out_for_delivery" 24
The case remains SHIPPED and an out-for-delivery notification is sent.
.IP "delivered" 24
The case advances through DELIVERED to COMPLETED.
.PP
The exchange enum has no distinct OUT_FOR_DELIVERY state. That intermediate milestone is represented through notification while the stored case remains SHIPPED.
.
.NH 1
Customer and administrator visibility
.NH 2
Administrator view
.PP
Administrators can see and update the complete case status, reverse AWB and tracking URL, pickup failure reason, replacement AWB and tracking URL, inspection decisions, settlement details, refund identifiers, and manual COD refund details.
.
.NH 2
Customer view
.PP
Customers see the return/exchange case status, rejection reason, replacement choice, and replacement outbound tracking link and AWB. They may cancel a case only while its status is REQUESTED.
.PP
In the current implementation, reverse-pickup AWB and reverse tracking URL are stored and visible to administrators but are not included in the customer return response. The customer account currently renders the replacement shipment tracking link only.
.
.NH 1
Implementation observations
.IP "1." 4
The return-window calculation currently gives shipment_date first priority as its delivery anchor. If shipment_date represents dispatch rather than delivery, the three-day return period can begin too early. A dedicated shipment_delivered_at timestamp or explicit delivered event should take priority for strict "three days from delivery" behavior.
.IP "2." 4
The original order's tracking remains delivered throughout a return or exchange. Customer interfaces should always display the separate return/exchange status alongside it to avoid confusion.
.IP "3." 4
For better customer visibility, the return response could expose reverse_awb and tracking_url, just as it already exposes outbound_awb and outbound_tracking_url for replacement shipments.
.
.NH 1
Quick operational reference
.IP "Cancellation" 18
Use only before dispatch. REQUESTED places fulfilment on hold; APPROVED cancels the order; REJECTED or WITHDRAWN leaves the order active.
.IP "Return" 18
Delivered paid order -> reverse pickup -> receive -> inspect -> accept -> refund.
.IP "Exchange" 18
Delivered paid order -> reserve replacement -> reverse pickup -> inspect -> settle difference -> allocate -> ship replacement -> complete.
.IP "Forward tracking" 18
Update automatically from carrier/Zoho/Ledgerly events and pulls. Do not equate AWB creation with physical shipment for ExpressBees.
.IP "Reverse tracking" 18
Update the return/exchange case status, not the original order's tracking_status.
