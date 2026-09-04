package com.miniandmore.app.service;

import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.repository.CustomerOrderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.zoho", name = "auto-sync-enabled", havingValue = "true")
public class ZohoSyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoSyncScheduler.class);
    private static final int ORDER_RETRY_BATCH = 50;
    private static final int TRACKING_PULL_BATCH = 3;

    private final CustomerOrderRepository customerOrderRepository;
    private final ApplicationProperties applicationProperties;
    private final ZohoSyncService zohoSyncService;

    public ZohoSyncScheduler(
        CustomerOrderRepository customerOrderRepository,
        ApplicationProperties applicationProperties,
        ZohoSyncService zohoSyncService
    ) {
        this.customerOrderRepository = customerOrderRepository;
        this.applicationProperties = applicationProperties;
        this.zohoSyncService = zohoSyncService;
    }

    @Scheduled(fixedDelayString = "${application.zoho.order-retry.fixed-delay-ms:600000}")
    public void retryFailedOrderSync() {
        customerOrderRepository
            .findPaidOrdersForZohoRetry(
                PaymentStatus.PAID,
                List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING),
                PageRequest.of(0, ORDER_RETRY_BATCH)
            )
            .forEach(order -> {
                try {
                    zohoSyncService.syncOrder(order.getId());
                } catch (Exception e) {
                    LOG.debug("Scheduled Zoho order retry failed for {}: {}", order.getId(), e.getMessage());
                }
            });
        customerOrderRepository
            .findConfirmedCodOrdersForZohoRetry(
                List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING),
                PageRequest.of(0, ORDER_RETRY_BATCH)
            )
            .forEach(order -> {
                try {
                    zohoSyncService.syncOrder(order.getId());
                } catch (Exception e) {
                    LOG.debug("Scheduled Zoho COD order retry failed for {}: {}", order.getId(), e.getMessage());
                }
            });
    }

    @Scheduled(fixedDelayString = "${application.zoho.order-tracking.fixed-delay-ms:900000}")
    public void pullOpenOrderTracking() {
        if (!applicationProperties.getZoho().getOrderTracking().isEnabled()) {
            return;
        }
        customerOrderRepository
            .findOrdersForZohoTrackingPull(
                PaymentStatus.PAID,
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.HAND_WRAPPED),
                OrderStatus.FULFILLED,
                PageRequest.of(0, TRACKING_PULL_BATCH)
            )
            .forEach(order -> {
                try {
                    zohoSyncService.pullOrderTracking(order.getId());
                } catch (Exception e) {
                    LOG.debug("Scheduled Zoho tracking pull failed for {}: {}", order.getId(), e.getMessage());
                }
            });
    }
}
