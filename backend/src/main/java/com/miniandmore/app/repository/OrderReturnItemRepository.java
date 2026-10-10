package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderReturnItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, Long> {
    @EntityGraph(attributePaths = { "orderItem" })
    List<OrderReturnItem> findAllByOrderReturnIdOrderById(Long orderReturnId);
}
