package com.miniandmore.app.repository;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the CustomerOrder entity.
 */
@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {
    default Optional<CustomerOrder> findOneWithEagerRelationships(Long id) {
        return findById(id);
    }

    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.userLogin = ?#{authentication.name}")
    List<CustomerOrder> findByUserIsCurrentUser();

    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.userLogin = :login order by customerOrder.id desc")
    List<CustomerOrder> findAllByUserLoginOrderByIdDesc(@Param("login") String login);

    Page<CustomerOrder> findAllByPlacedAtGreaterThanEqualAndPlacedAtLessThan(
        Instant from,
        Instant to,
        Pageable pageable
    );

    @Query(
        "select customerOrder from CustomerOrder customerOrder where customerOrder.userLogin = :login and customerOrder.orderNumber = :orderNumber"
    )
    Optional<CustomerOrder> findOneByOrderNumberAndUserLogin(@Param("orderNumber") String orderNumber, @Param("login") String login);

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null) order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null)"
    )
    Page<CustomerOrder> findPaidOrdersForZohoRetry(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("statuses") List<ZohoSyncStatus> statuses,
        Pageable pageable
    );

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.zohoSalesOrderId is null order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.zohoSalesOrderId is null"
    )
    Page<CustomerOrder> findPaidOrdersMissingZohoSalesOrder(@Param("paymentStatus") PaymentStatus paymentStatus, Pageable pageable);

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "(customerOrder.ledgerlySyncStatus in :statuses or customerOrder.ledgerlySyncStatus is null) order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "(customerOrder.ledgerlySyncStatus in :statuses or customerOrder.ledgerlySyncStatus is null)"
    )
    Page<CustomerOrder> findPaidOrdersForLedgerlyRetry(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("statuses") List<LedgerlySyncStatus> statuses,
        Pageable pageable
    );

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.ledgerlySalesOrderId is null order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.ledgerlySalesOrderId is null"
    )
    Page<CustomerOrder> findPaidOrdersMissingLedgerlySalesOrder(@Param("paymentStatus") PaymentStatus paymentStatus, Pageable pageable);

    Optional<CustomerOrder> findOneByOrderNumber(String orderNumber);

    Optional<CustomerOrder> findOneByZohoSalesOrderId(String zohoSalesOrderId);

    Optional<CustomerOrder> findOneByZohoInvoiceId(String zohoInvoiceId);

    @Query(
        "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.paymentStatus = :paymentStatus order by customerOrder.id desc"
    )
    List<CustomerOrder> findCodOrdersByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.status in (com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED, " +
        "com.miniandmore.app.domain.enumeration.OrderStatus.HAND_WRAPPED) and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null) and " +
        "(customerOrder.zohoSalesOrderId is null or customerOrder.zohoInvoiceId is null or customerOrder.zohoInvoiceId = '') " +
        "order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.status in (com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED, " +
        "com.miniandmore.app.domain.enumeration.OrderStatus.HAND_WRAPPED) and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null) and " +
        "(customerOrder.zohoSalesOrderId is null or customerOrder.zohoInvoiceId is null or customerOrder.zohoInvoiceId = '')"
    )
    Page<CustomerOrder> findConfirmedCodOrdersForZohoRetry(
        @Param("statuses") List<ZohoSyncStatus> statuses,
        Pageable pageable
    );

    boolean existsByUserLoginAndPaymentStatus(String userLogin, PaymentStatus paymentStatus);

    /**
     * True when the customer already has an order that is not in {@code status}
     * (used for first-order coupon eligibility, excluding cancelled carts).
     * Login match is case-insensitive so email-style logins still count.
     */
    @Query(
        "select (count(customerOrder) > 0) from CustomerOrder customerOrder where lower(customerOrder.userLogin) = lower(:login) and customerOrder.status <> :status"
    )
    boolean existsByUserLoginAndStatusNot(@Param("login") String login, @Param("status") OrderStatus status);

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.inventoryReserved = true and customerOrder.inventoryReleasedAt is null and " +
        "customerOrder.inventoryReservedUntil <= :now order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.inventoryReserved = true and customerOrder.inventoryReleasedAt is null and customerOrder.inventoryReservedUntil <= :now"
    )
    Page<CustomerOrder> findExpiredInventoryReservations(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("now") Instant now,
        Pageable pageable
    );

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where " +
        "(customerOrder.paymentStatus = :paymentStatus or customerOrder.paymentMethod = 'cod') and " +
        "customerOrder.zohoSalesOrderId is not null and " +
        "(customerOrder.status in :statuses or " +
        "(customerOrder.status = :fulfilledStatus and " +
        "(customerOrder.zohoOrderStatus is null or lower(customerOrder.zohoOrderStatus) <> 'delivered'))) " +
        "order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where " +
        "(customerOrder.paymentStatus = :paymentStatus or customerOrder.paymentMethod = 'cod') and " +
        "customerOrder.zohoSalesOrderId is not null and " +
        "(customerOrder.status in :statuses or " +
        "(customerOrder.status = :fulfilledStatus and " +
        "(customerOrder.zohoOrderStatus is null or lower(customerOrder.zohoOrderStatus) <> 'delivered')))"
    )
    Page<CustomerOrder> findOrdersForZohoTrackingPull(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("statuses") List<OrderStatus> statuses,
        @Param("fulfilledStatus") OrderStatus fulfilledStatus,
        Pageable pageable
    );

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.ledgerlySalesOrderId is not null and customerOrder.status in :statuses order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.ledgerlySalesOrderId is not null and customerOrder.status in :statuses"
    )
    Page<CustomerOrder> findOrdersForLedgerlyTrackingPull(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("statuses") List<OrderStatus> statuses,
        Pageable pageable
    );
}
