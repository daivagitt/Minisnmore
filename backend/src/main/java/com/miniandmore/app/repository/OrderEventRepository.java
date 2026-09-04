package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    boolean existsByOrderIdAndEventType(Long orderId, String eventType);

    @Modifying
    @Query(
        value = """
        insert into order_event (id, user_login, order_id, order_number, event_type, title, body, created_at)
        values (nextval('sequence_generator'), :userLogin, :orderId, :orderNumber, :eventType, :title, :body, :createdAt)
        on conflict on constraint ux_order_event__order_id_event_type do nothing
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("userLogin") String userLogin,
        @Param("orderId") Long orderId,
        @Param("orderNumber") String orderNumber,
        @Param("eventType") String eventType,
        @Param("title") String title,
        @Param("body") String body,
        @Param("createdAt") Instant createdAt
    );

    List<OrderEvent> findAllByUserLoginOrderByCreatedAtDesc(String userLogin, Pageable pageable);
}
