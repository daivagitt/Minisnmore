package com.miniandmore.app.repository;

import com.miniandmore.app.domain.ZohoStockWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZohoStockWebhookEventRepository extends JpaRepository<ZohoStockWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
