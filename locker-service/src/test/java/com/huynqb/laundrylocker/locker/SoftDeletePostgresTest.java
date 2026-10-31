package com.huynqb.laundrylocker.locker;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.locker.dto.BatchDeleteLockersRequest;
import com.huynqb.laundrylocker.locker.model.DroneDevice;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.DroneDeviceRepository;
import com.huynqb.laundrylocker.locker.repository.DroneUnitRepository;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import com.huynqb.laundrylocker.locker.service.LockerService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Xoá mềm chạy thật trên Postgres (Flyway V32 + @SQLRestriction): bản ghi biến khỏi mọi truy vấn của
/// ứng dụng nhưng còn nguyên trong DB kèm lý do, và mã của bản ghi đã xoá được dùng lại.
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
class SoftDeletePostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("locker_db")
                    .withUsername("locker_user")
                    .withPassword("locker_pass");

    private static final Long ADMIN_ID = 7L;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LockerService lockerService;
    @Autowired
    private LockerUnitRepository lockerRepository;
    @Autowired
    private LockerBoxRepository boxRepository;
    @Autowired
    private DroneUnitRepository droneRepository;
    @Autowired
    private DroneDeviceRepository deviceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private LockerUnit locker(String code) {
        LockerUnit locker = new LockerUnit();
        locker.setCode(code);
        locker.setName("Tủ " + code);
        return lockerRepository.save(locker);
    }

    private LockerBox box(Long lockerId, int number, String status) {
        LockerBox box = new LockerBox();
        box.setLockerId(lockerId);
        box.setBoxNumber(number);
        box.setStatus(status);
        return boxRepository.save(box);
    }

    private DroneUnit drone(Long lockerId, String code) {
        DroneUnit drone = new DroneUnit();
        drone.setLockerId(lockerId);
        drone.setCode(code);
        return droneRepository.save(drone);
    }

    private Map<String, Object> rawRow(String table, Long id) {
        return jdbc.queryForMap("SELECT * FROM locker_schema." + table + " WHERE id = ?", id);
    }

    @Test
    void deletedLockerAndBoxesStayInDatabaseWithReasonAndCodeIsReusable() {
        String code = uniqueCode("SOFT");
        LockerUnit locker = locker(code);
        LockerBox box = box(locker.getId(), 1, "AVAILABLE");

        lockerService.deleteLocker(locker.getId(), "  Dời điểm đặt tủ  ", ADMIN_ID);

        assertFalse(lockerRepository.findById(locker.getId()).isPresent());
        assertFalse(boxRepository.findById(box.getId()).isPresent());
        assertThrows(NotFoundException.class, () -> lockerService.getLocker(locker.getId()));

        Map<String, Object> row = rawRow("lockers", locker.getId());
        assertEquals("Dời điểm đặt tủ", row.get("deleted_reason"));
        assertEquals(ADMIN_ID, ((Number) row.get("deleted_by")).longValue());
        assertNotNull(row.get("deleted_at"));
        assertEquals("Dời điểm đặt tủ", rawRow("locker_boxes", box.getId()).get("deleted_reason"));

        assertNotNull(locker(code).getId());
    }

    @Test
    void lockerDeleteNeedsReasonAndRefusesBoxesInUse() {
        LockerUnit locker = locker(uniqueCode("BUSY"));
        box(locker.getId(), 1, "OCCUPIED");

        BusinessException noReason = assertThrows(BusinessException.class,
                () -> lockerService.deleteLocker(locker.getId(), "   ", ADMIN_ID));
        assertEquals("DELETE_REASON_REQUIRED", noReason.getCode());

        BusinessException inUse = assertThrows(BusinessException.class,
                () -> lockerService.deleteLocker(locker.getId(), "Thử", ADMIN_ID));
        assertEquals("LOCKER_IN_USE", inUse.getCode());
        assertTrue(lockerRepository.findById(locker.getId()).isPresent());
    }

    @Test
    void batchDeleteWithoutSelectionDeletesNothing() {
        LockerUnit locker = locker(uniqueCode("KEEP"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lockerService.deleteLockersBatch(new BatchDeleteLockersRequest(List.of(), false, "Dọn"), ADMIN_ID));

        assertEquals("NO_LOCKERS_SELECTED", ex.getCode());
        assertTrue(lockerRepository.findById(locker.getId()).isPresent());
    }

    @Test
    void deletedBoxKeepsReasonAndNumberCanBeUsedAgain() {
        LockerUnit locker = locker(uniqueCode("BOX"));
        LockerBox box = box(locker.getId(), 5, "AVAILABLE");

        lockerService.deleteBox(box.getId(), "Cửa ô bị cong", ADMIN_ID);

        assertFalse(boxRepository.existsByLockerIdAndBoxNumber(locker.getId(), 5));
        assertEquals("Cửa ô bị cong", rawRow("locker_boxes", box.getId()).get("deleted_reason"));
        assertNotNull(box(locker.getId(), 5, "AVAILABLE").getId());
    }

    @Test
    void deletedDroneLeavesFleetReleasesDeviceAndFreesCodeAndLocker() {
        LockerUnit locker = locker(uniqueCode("PAD"));
        String code = uniqueCode("DRN");
        DroneUnit unit = drone(locker.getId(), code);
        DroneDevice device = new DroneDevice();
        device.setMacAddress("AA:BB:" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        device.setDroneId(unit.getId());
        device.setDroneCode(code);
        device = deviceRepository.save(device);

        lockerService.decommissionDrone(unit.getId(), "Thanh lý", ADMIN_ID);

        assertTrue(lockerService.listDroneUnits(null, locker.getId()).isEmpty());
        assertThrows(NotFoundException.class, () -> lockerService.getDroneUnit(unit.getId()));
        Map<String, Object> row = rawRow("drone_units", unit.getId());
        assertEquals("Thanh lý", row.get("deleted_reason"));
        assertEquals(false, row.get("active"));
        assertNull(deviceRepository.findById(device.getId()).orElseThrow().getDroneId());

        // Mã cũ dùng lại được; drone đã xoá không còn giữ trạm.
        DroneUnit replacement = drone(locker.getId(), code);
        assertTrue(droneRepository.findByCodeAndActiveTrue(code).isPresent());
        lockerService.decommissionDrone(replacement.getId(), "Thanh lý", ADMIN_ID);
        lockerService.deleteLocker(locker.getId(), "Bỏ trạm", ADMIN_ID);
        assertFalse(lockerRepository.findById(locker.getId()).isPresent());
    }
}
