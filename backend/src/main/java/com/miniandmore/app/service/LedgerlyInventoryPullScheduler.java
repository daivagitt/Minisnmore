package com.miniandmore.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.ledgerly.schedulers", name = "pull-inventory-ms")
public class LedgerlyInventoryPullScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlyInventoryPullScheduler.class);

    private final LedgerlySyncService ledgerlySyncService;

    public LedgerlyInventoryPullScheduler(LedgerlySyncService ledgerlySyncService) {
        this.ledgerlySyncService = ledgerlySyncService;
    }

    @Scheduled(fixedDelayString = "${application.ledgerly.schedulers.pull-inventory-ms}")
    public void pullInventory() {
        try {
            ledgerlySyncService.pullInventory();
        } catch (Exception e) {
            LOG.debug("Scheduled inventory pull failed: {}", e.getMessage());
        }
    }
}
