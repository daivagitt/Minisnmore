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

    List<CustomerOrder> findAllByPlacedAtGreaterThanEqualAndPlacedAtLessThanOrderByPlacedAtDesc(Instant from, Instant to);

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

    @Query(
        "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.paymentStatus = :paymentStatus order by customerOrder.id desc"
    )
    List<CustomerOrder> findCodOrdersByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    @Query(
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.status = com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null) and " +
        "(customerOrder.zohoSalesOrderId is null or customerOrder.zohoInvoiceId is null or customerOrder.zohoInvoiceId = '') " +
        "order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentMethod = 'cod' and " +
        "customerOrder.status = com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED and " +
        "(customerOrder.zohoSyncStatus in :statuses or customerOrder.zohoSyncStatus is null) and " +
        "(customerOrder.zohoSalesOrderId is null or customerOrder.zohoInvoiceId is null or customerOrder.zohoInvoiceId = '')"
    )
    Page<CustomerOrder> findConfirmedCodOrdersForZohoRetry(
        @Param("statuses") List<ZohoSyncStatus> statuses,
        Pageable pageable
    );

    boolean existsByUserLoginAndPaymentStatus(String userLogin, PaymentStatus paymentStatus);

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
        value = "select customerOrder from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.zohoSalesOrderId is not null and customerOrder.status in :statuses order by customerOrder.id asc",
        countQuery = "select count(customerOrder) from CustomerOrder customerOrder where customerOrder.paymentStatus = :paymentStatus and " +
        "customerOrder.zohoSalesOrderId is not null and customerOrder.status in :statuses"
    )
    Page<CustomerOrder> findOrdersForZohoTrackingPull(
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("statuses") List<OrderStatus> statuses,
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
