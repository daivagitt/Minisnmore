package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    boolean existsByOrderIdAndEventType(Long orderId, String eventType);

    List<OrderEvent> findAllByUserLoginOrderByCreatedAtDesc(String userLogin, Pageable pageable);
}
