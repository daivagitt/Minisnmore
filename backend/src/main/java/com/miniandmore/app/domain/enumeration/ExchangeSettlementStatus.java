package com.miniandmore.app.domain.enumeration;

/** Audit state for an exchange's snapshotted price difference. */
public enum ExchangeSettlementStatus {
    PENDING,
    NOT_REQUIRED,
    COLLECTED,
    REFUNDED,
    WAIVED,
}
