package com.miniandmore.app.domain.enumeration;

/**
 * Lifecycle for a customer return request.
 */
public enum OrderReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    RECEIVED,
    REFUNDED,
    FAILED,
    CANCELLED,
}
