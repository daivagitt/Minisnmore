package com.miniandmore.app.service;

import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.ledgerly", name = "auto-sync-enabled", havingValue = "true")
public class LedgerlySyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlySyncScheduler.class);

    private final CustomerOrderRepository customerOrderRepository;
    private final LedgerlySyncService ledgerlySyncService;

    public LedgerlySyncScheduler(CustomerOrderRepository customerOrderRepository, LedgerlySyncService ledgerlySyncService) {
        this.customerOrderRepository = customerOrderRepository;
        this.ledgerlySyncService = ledgerlySyncService;
    }

    @Scheduled(fixedDelayString = "${application.ledgerly.schedulers.retry-orders-ms:300000}")
    public void retryFailedOrderSync() {
        List<Long> orderIds = customerOrderRepository
            .findAll()
            .stream()
            .filter(o -> o.getLedgerlySyncStatus() == LedgerlySyncStatus.FAILED || o.getLedgerlySyncStatus() == LedgerlySyncStatus.PENDING)
            .map(o -> o.getId())
            .toList();
        for (Long id : orderIds) {
            try {
                ledgerlySyncService.syncOrder(id);
            } catch (Exception e) {
                LOG.debug("Scheduled order retry failed for {}: {}", id, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${application.ledgerly.schedulers.pull-tracking-ms:3600000}")
    public void pullOpenOrderTracking() {
        customerOrderRepository
            .findAll()
            .forEach(o -> {
                try {
                    ledgerlySyncService.pullOrderTracking(o.getId());
                } catch (Exception e) {
                    LOG.debug("Scheduled tracking pull failed for {}: {}", o.getId(), e.getMessage());
                }
            });
    }
}
