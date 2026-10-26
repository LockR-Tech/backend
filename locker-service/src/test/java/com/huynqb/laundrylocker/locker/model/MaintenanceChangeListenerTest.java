package com.huynqb.laundrylocker.locker.model;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceChangeListenerTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final MaintenanceChangeListener listener = new MaintenanceChangeListener(provider(rabbit));

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RabbitTemplate> provider(RabbitTemplate rabbit) {
        ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(rabbit);
        return provider;
    }

    private DomainEvent sent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.MAINTENANCE_CHANGED),
                event.capture());
        return (DomainEvent) event.getValue();
    }

    @Test
    void reportChangeCarriesReportAndLocker() {
        LockerReport report = new LockerReport();
        report.setId(34L);
        report.setLockerId(5L);

        listener.changed(report);

        DomainEvent event = sent();
        assertEquals("REPORT", event.payload().get("entity"));
        assertEquals(34L, event.payload().get("id"));
        assertEquals(5L, event.payload().get("lockerId"));
    }

    /// Ảnh/nhật ký/đánh giá thuộc phiếu ⇒ báo theo mã phiếu để màn chi tiết phiếu tải lại.
    @Test
    void repairLogIsReportedAsItsReport() {
        RepairLog log = new RepairLog();
        log.setReportId(34L);

        listener.changed(log);

        assertEquals(34L, sent().payload().get("id"));
    }

    @Test
    void unrelatedEntitiesAreIgnored() {
        listener.changed(new LockerBox());

        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
