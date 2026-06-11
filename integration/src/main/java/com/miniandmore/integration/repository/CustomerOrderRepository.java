package com.miniandmore.integration.repository;

import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
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

    Optional<CustomerOrder> findOneByOrderNumber(String orderNumber);

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
}
