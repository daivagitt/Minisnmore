package com.miniandmore.app.repository;

import com.miniandmore.app.domain.ZohoOrderStatusWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZohoOrderStatusWebhookEventRepository extends JpaRepository<ZohoOrderStatusWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
