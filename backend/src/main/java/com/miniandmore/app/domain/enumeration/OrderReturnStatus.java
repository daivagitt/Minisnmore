package com.miniandmore.app.domain.enumeration;

/**
 * Lifecycle for a customer return or exchange request.
 *
 * Return path: REQUESTED -&gt; APPROVED -&gt; PICKUP_REQUESTED -&gt; PICKED_UP -&gt; RECEIVED -&gt; INSPECTED
 *   -&gt; ACCEPTED -&gt; REFUNDED. Reject at REQUESTED or INSPECTED -&gt; REJECTED.
 * Exchange path: REQUESTED -&gt; APPROVED -&gt; PICKUP_REQUESTED -&gt; PICKED_UP -&gt; RECEIVED -&gt; INSPECTED
 *   -&gt; ACCEPTED -&gt; ITEM_ALLOCATED -&gt; REPACKING -&gt; READY_TO_SHIP -&gt; SHIPPED -&gt; DELIVERED -&gt; COMPLETED.
 *   Reject at REQUESTED/INSPECTED -&gt; REJECTED. Convert-to-refund at ACCEPTED -&gt; REFUNDED.
 */
public enum OrderReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    PICKUP_REQUESTED,
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
