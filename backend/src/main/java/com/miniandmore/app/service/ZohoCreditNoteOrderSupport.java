package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import java.util.Optional;

/** Order/contact resolution for Zoho credit notes (implemented by {@link ZohoSyncService}). */
public interface ZohoCreditNoteOrderSupport {
    String resolveZohoContact(CustomerOrder order);

    Optional<String> resolveZohoItemId(OrderItem item);
}
