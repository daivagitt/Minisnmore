package com.miniandmore.app.domain.enumeration;

/** Lifecycle for a customer cancellation request pending admin review. */
public enum CancellationRequestStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
}
