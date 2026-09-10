package com.miniandmore.app.domain.enumeration;

/** Warehouse disposition for a returned unit after inspection. */
public enum ReturnDisposition {
    RESTOCKABLE,
    QUARANTINE,
    REPAIR,
    DISCARD,
    RETURN_TO_VENDOR,
}
