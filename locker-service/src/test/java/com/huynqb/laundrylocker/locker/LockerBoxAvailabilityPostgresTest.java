package com.huynqb.laundrylocker.locker;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// Ô đổi trạng thái qua JPA thật (Postgres) → báo "tủ X đổi số ô trống" sau commit, mỗi tủ một lần
/// cho một giao dịch; cập nhật không đổi trạng thái (cảm biến cửa) thì không báo.
@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "management.health.rabbit.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.rabbitmq.listener.direct.auto-startup=false"
        })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LockerBoxAvailabilityPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("locker_db")
                    .withUsername("locker_user")
                    .withPassword("locker_pass");

    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LockerUnitRepository lockerUnitRepository;
    @Autowired
    private LockerBoxRepository lockerBoxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private LockerUnit demo;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        demo = lockerUnitRepository.findAll().stream()
                .filter(locker -> "CAB-DEMO-01".equals(locker.getCode()))
                .findFirst()
                .orElseThrow();
        clearInvocations(rabbitTemplate);
    }

    @Test
    void statusChangePublishesOncePerLockerAfterCommit() {
        List<Long> boxIds = drones().stream().limit(2).map(LockerBox::getId).toList();
        boolean[] publishedBeforeCommit = {false};
        doAnswer(inv -> null).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        tx.executeWithoutResult(status -> {
            for (Long id : boxIds) {
                LockerBox box = lockerBoxRepository.findById(id).orElseThrow();
                box.setStatus("RESERVED");
            }
            lockerBoxRepository.flush();
            try {
                verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
            } catch (AssertionError e) {
                publishedBeforeCommit[0] = true;
            }
        });

        assertEquals(false, publishedBeforeCommit[0], "phải chờ commit mới báo");
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_AVAILABILITY_CHANGED), event.capture());
        assertEquals(demo.getId(), ((Number) ((DomainEvent) event.getValue()).payload().get("lockerId")).longValue());

        tx.executeWithoutResult(status -> boxIds.forEach(id ->
                lockerBoxRepository.findById(id).orElseThrow().setStatus("AVAILABLE")));
    }

    @Test
    void updateWithoutStatusChangeDoesNotPublish() {
        Long boxId = drones().getFirst().getId();

        tx.executeWithoutResult(status -> {
            LockerBox box = lockerBoxRepository.findById(boxId).orElseThrow();
            box.setFaultReason("chỉ đổi ghi chú");
        });

        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), eq(DomainEventNames.LOCKER_AVAILABILITY_CHANGED), any(Object.class));
    }

    @Test
    void rolledBackChangeDoesNotPublish() {
        Long boxId = drones().getFirst().getId();

        tx.executeWithoutResult(status -> {
            lockerBoxRepository.findById(boxId).orElseThrow().setStatus("OCCUPIED");
            lockerBoxRepository.flush();
            status.setRollbackOnly();
        });

        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), eq(DomainEventNames.LOCKER_AVAILABILITY_CHANGED), any(Object.class));
    }

    private List<LockerBox> drones() {
        return lockerBoxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(demo.getId()).stream()
                .filter(box -> "DRONE".equals(box.getCellType()))
                .toList();
    }
}
