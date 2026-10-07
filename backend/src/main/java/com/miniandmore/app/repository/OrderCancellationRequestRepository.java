package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderCancellationRequestRepository extends JpaRepository<OrderCancellationRequest, Long> {
    @EntityGraph(attributePaths = { "order" })
    Optional<OrderCancellationRequest> findFirstByRazorpayRefundIdOrderByIdDesc(String razorpayRefundId);

    @EntityGraph(attributePaths = { "order" })
    Optional<OrderCancellationRequest> findOneByRequestNumber(String requestNumber);

    @EntityGraph(attributePaths = { "order" })
    Optional<OrderCancellationRequest> findFirstByOrderIdAndStatusOrderByRequestedAtDesc(Long orderId, CancellationRequestStatus status);

    @EntityGraph(attributePaths = { "order" })
    List<OrderCancellationRequest> findAllByStatusInOrderByRequestedAtDesc(Collection<CancellationRequestStatus> statuses);

    boolean existsByOrderIdAndStatus(Long orderId, CancellationRequestStatus status);
}
