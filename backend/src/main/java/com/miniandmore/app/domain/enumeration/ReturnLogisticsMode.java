package com.miniandmore.app.domain.enumeration;

/** How reverse pickup was arranged — ExpressBees is admin-only; manual paths never fabricate a provider id. */
public enum ReturnLogisticsMode {
    XPRESSBEES,
    MANUAL,
    SELF_SHIP,
}
