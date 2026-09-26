package com.miniandmore.app.repository;

import com.miniandmore.app.domain.XpressBeesReturnWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface XpressBeesReturnWebhookEventRepository extends JpaRepository<XpressBeesReturnWebhookEvent, Long> {
    boolean existsByEventId(String eventId);

    /**
     * True when a prior OFD webhook for this return already finished applying (local_status set).
     * Used so exchange OFD emails are sent once even when ExpressBees sends unique event ids.
     */
    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1 FROM xpressbees_return_webhook_event
                WHERE return_number = :returnNumber
                  AND local_status IS NOT NULL
                  AND (
                    LOWER(REPLACE(REPLACE(TRIM(COALESCE(provider_status, '')), '-', '_'), ' ', '_')) = 'out_for_delivery'
                    OR LOWER(TRIM(COALESCE(provider_status, ''))) = 'ofd'
                  )
            )
            """,
        nativeQuery = true
    )
    boolean existsCompletedOutForDeliveryEvent(@Param("returnNumber") String returnNumber);
}
