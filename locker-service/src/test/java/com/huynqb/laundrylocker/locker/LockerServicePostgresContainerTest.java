package com.huynqb.laundrylocker.locker;

import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerReportRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
class LockerServicePostgresContainerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("locker_db")
                    .withUsername("locker_user")
                    .withPassword("locker_pass");

    @Autowired
    private LockerUnitRepository lockerUnitRepository;
    @Autowired
    private LockerBoxRepository lockerBoxRepository;
    @Autowired
    private LockerReportRepository lockerReportRepository;

    @Test
    void flywayMigratesSchemaAndSeedsDemoCabinet() {
        LockerUnit demoCabinet =
                lockerUnitRepository.findAll().stream()
                        .filter(locker -> "CAB-DEMO-01".equals(locker.getCode()))
                        .findFirst()
                        .orElseThrow();

        assertEquals("ACTIVE", demoCabinet.getStatus());
        assertEquals(Boolean.TRUE, demoCabinet.getLandingPad());
        assertEquals("ARUCO-23", demoCabinet.getLandingMarkerId());

        List<LockerBox> cells =
                lockerBoxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(demoCabinet.getId());
        assertEquals(10, cells.size());
        assertEquals(3, countByCellType(cells, "DRONE"));
        assertEquals(6, countByCellType(cells, "STANDARD"));
        assertEquals(1, countByCellType(cells, "XL"));
        assertTrue(cells.stream().allMatch(cell -> "AVAILABLE".equals(cell.getStatus())));
    }

    @Test
    void maintenanceReportColumnsAreMapped() {
        LockerUnit demoCabinet =
                lockerUnitRepository.findAll().stream()
                        .filter(locker -> "CAB-DEMO-01".equals(locker.getCode()))
                        .findFirst()
                        .orElseThrow();
        LockerBox firstCell =
                lockerBoxRepository.findByLockerId(demoCabinet.getId()).stream().findFirst().orElseThrow();

        LockerReport report = new LockerReport();
        report.setLockerId(demoCabinet.getId());
        report.setBoxId(firstCell.getId());
        report.setUserId(1001L);
        report.setTitle("Door sensor issue");
        report.setDescription("Created by Testcontainers migration smoke test");
        report.setAssignedToUserId(2001L);
        report.setStatus("IN_PROGRESS");

        LockerReport saved = lockerReportRepository.saveAndFlush(report);

        assertNotNull(saved.getId());
        assertEquals(firstCell.getId(), saved.getBoxId());
        assertEquals(2001L, saved.getAssignedToUserId());
    }

    private long countByCellType(List<LockerBox> cells, String cellType) {
        return cells.stream().filter(cell -> cellType.equals(cell.getCellType())).count();
    }
}
