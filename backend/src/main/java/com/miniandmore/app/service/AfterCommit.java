package com.miniandmore.app.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work after the current transaction commits so transactional order/return
 * updates are never rolled back after a side effect such as email has already gone out.
 */
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
            );
            return;
        }
        action.run();
    }
}
