package com.miniandmore.app.repository;

import com.miniandmore.app.domain.RazorpayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
