package com.miniandmore.app.repository;

import com.miniandmore.app.domain.ZohoItemWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZohoItemWebhookEventRepository extends JpaRepository<ZohoItemWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
