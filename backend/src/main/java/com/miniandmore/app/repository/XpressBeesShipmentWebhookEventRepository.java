package com.miniandmore.app.repository;

import com.miniandmore.app.domain.XpressBeesShipmentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface XpressBeesShipmentWebhookEventRepository extends JpaRepository<XpressBeesShipmentWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
