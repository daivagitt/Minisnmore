package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void run_executesImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        AfterCommit.run(() -> ran.set(true));
        assertThat(ran).isTrue();
    }

    @Test
    void run_defersUntilAfterCommitWhenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean ran = new AtomicBoolean(false);
        AfterCommit.run(() -> ran.set(true));
        assertThat(ran).isFalse();
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertThat(ran).isTrue();
    }
}
