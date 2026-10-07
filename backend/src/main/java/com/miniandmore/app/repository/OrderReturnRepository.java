package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
    @EntityGraph(attributePaths = { "order", "orderItem" })
    Optional<OrderReturn> findFirstByRazorpayRefundIdOrderByIdDesc(String razorpayRefundId);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    Optional<OrderReturn> findOneByReturnNumber(String returnNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "order", "orderItem" })
    @Query("select r from OrderReturn r where r.returnNumber = :returnNumber")
    Optional<OrderReturn> findOneByReturnNumberForUpdate(@Param("returnNumber") String returnNumber);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    Optional<OrderReturn> findFirstBySettlementReferenceOrderByIdDesc(String settlementReference);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    Optional<OrderReturn> findFirstByReverseAwbOrderByRequestedAtDesc(String reverseAwb);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    Optional<OrderReturn> findFirstByOutboundAwbOrderByRequestedAtDesc(String outboundAwb);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    @Query("select r from OrderReturn r where r.userLogin = :login order by r.requestedAt desc")
    List<OrderReturn> findAllByUserLoginOrderByRequestedAtDesc(@Param("login") String login);

    @EntityGraph(attributePaths = { "order", "orderItem" })
    List<OrderReturn> findAllByStatusInOrderByRequestedAtDesc(Collection<OrderReturnStatus> statuses);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<OrderReturnStatus> statuses);

    boolean existsByOrderIdAndRequestTypeAndStatusIn(
        Long orderId,
        OrderRequestType requestType,
        Collection<OrderReturnStatus> statuses
    );

    boolean existsByOrderIdAndOrderItemIdAndStatusIn(Long orderId, Long orderItemId, Collection<OrderReturnStatus> statuses);

    List<OrderReturn> findAllByOrder_IdOrderByRequestedAtAsc(Long orderId);

    @Query("select r.requestType, count(r) from OrderReturn r group by r.requestType")
    List<Object[]> countGroupedByRequestType();

    @Query("select r.status, count(r) from OrderReturn r group by r.status")
    List<Object[]> countGroupedByStatus();

    @Query("select r.status, r.requestedAt from OrderReturn r where r.status not in :closed")
    List<Object[]> findStatusAndRequestedAtByStatusNotIn(@Param("closed") Collection<OrderReturnStatus> closed);

    long countByStatus(OrderReturnStatus status);

    @Modifying
    @Query(
        "update OrderReturn r set r.zohoCreditNoteId = :creditNoteId where r.returnNumber = :returnNumber and (r.zohoCreditNoteId is null or r.zohoCreditNoteId = '')"
    )
    int persistZohoCreditNoteIdIfAbsent(@Param("returnNumber") String returnNumber, @Param("creditNoteId") String creditNoteId);
}
