package com.huynqb.laundrylocker.common.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MaintenanceChangeEventsTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransactionPublishesRightAway() {
        MaintenanceChangeEvents.changed(rabbit, "locker-service", MaintenanceChangeEvents.REPORT, 7L, 3L);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.MAINTENANCE_CHANGED),
                event.capture());
        DomainEvent sent = (DomainEvent) event.getValue();
        assertEquals("REPORT", sent.payload().get("entity"));
        assertEquals(7L, sent.payload().get("id"));
        assertEquals(3L, sent.payload().get("lockerId"));
    }

    @Test
    void insideTransactionSendsOncePerEntityAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        MaintenanceChangeEvents.changed(rabbit, "locker-service", MaintenanceChangeEvents.REPORT, 7L, 3L);
        MaintenanceChangeEvents.changed(rabbit, "locker-service", MaintenanceChangeEvents.REPORT, 7L, null);
        MaintenanceChangeEvents.changed(rabbit, "locker-service", MaintenanceChangeEvents.SCHEDULE, 7L, 3L);
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(TransactionSynchronization::afterCommit);
        syncs.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(rabbit, times(2)).convertAndSend(eq(DomainEventNames.EXCHANGE),
                eq(DomainEventNames.MAINTENANCE_CHANGED), any(Object.class));
    }

    @Test
    void rolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        MaintenanceChangeEvents.changed(rabbit, "locker-service", MaintenanceChangeEvents.DRONE, 2L, null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
