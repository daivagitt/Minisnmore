package com.miniandmore.app.domain.enumeration;

/**
 * Lifecycle for a customer return or exchange request.
 *
 * Return path: REQUESTED -&gt; APPROVED -&gt; PICKUP_REQUESTED -&gt; PICKED_UP -&gt; RECEIVED -&gt; INSPECTED
 *   -&gt; ACCEPTED -&gt; REFUNDED. Reject at REQUESTED or INSPECTED -&gt; REJECTED.
 * Exchange path: REQUESTED -&gt; APPROVED -&gt; PICKUP_REQUESTED -&gt; PICKED_UP -&gt; RECEIVED -&gt; INSPECTED
 *   -&gt; ACCEPTED -&gt; ITEM_ALLOCATED -&gt; READY_TO_SHIP -&gt; SHIPPED -&gt; DELIVERED -&gt; COMPLETED.
 *   Creating the outbound package from ITEM_ALLOCATED (or legacy REPACKING) moves to READY_TO_SHIP.
 *   Reject at REQUESTED/INSPECTED -&gt; REJECTED. Convert-to-refund at ACCEPTED -&gt; REFUNDED.
 */
public enum OrderReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    PICKUP_REQUESTED,
    PICKUP_SCHEDULED,
    PICKUP_FAILED,
    PICKED_UP,
    RECEIVED,
    INSPECTED,
    ACCEPTED,
    ITEM_ALLOCATED,
    REPACKING,
    READY_TO_SHIP,
    SHIPPED,
    DELIVERED,
    REFUNDED,
    COMPLETED,
    CANCELLED,
    FAILED,
}
