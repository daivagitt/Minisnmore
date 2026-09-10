package com.miniandmore.app.repository;

import com.miniandmore.app.domain.XpressBeesReturnWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface XpressBeesReturnWebhookEventRepository extends JpaRepository<XpressBeesReturnWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
