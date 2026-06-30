package com.miniandmore.app.repository;

import com.miniandmore.app.domain.LedgerlyWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerlyWebhookEventRepository extends JpaRepository<LedgerlyWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
