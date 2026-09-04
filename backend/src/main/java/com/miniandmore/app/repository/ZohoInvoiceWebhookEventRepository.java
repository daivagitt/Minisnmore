package com.miniandmore.app.repository;

import com.miniandmore.app.domain.ZohoInvoiceWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZohoInvoiceWebhookEventRepository extends JpaRepository<ZohoInvoiceWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
