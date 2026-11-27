package com.huynqb.laundrylocker.locker.model;

import com.huynqb.laundrylocker.common.audit.ActionLog;
import com.huynqb.laundrylocker.common.audit.RequestActor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Nhật ký thao tác (F3-G16): ghi đúng hành động và từ → thành cho tủ, ô, drone; không ghi việc tự động theo đơn /
/// chuyến bay trừ khi admin / KTV bấm.
class ActionAuditListenerTest {

    private static final RequestActor ADMIN = new RequestActor(1L, "ADMIN");

    @Test
    void lockerMaintenanceActivationAndTechnicianAreLogged() {
        LockerUnit locker = locker();
        Map<String, String> before = locker.auditFields();
        locker.setStatus("MAINTENANCE");
        locker.setMaintenanceSource("ADMIN");
        locker.setAssignedTechnicianId(7L);

        List<ActionLog.Entry> entries = ActionAuditListener.lockerChanges(locker, before, locker.auditFields());

        assertEquals(List.of("LOCKER_MAINTENANCE_ON", "LOCKER_TECHNICIAN_ASSIGNED"),
                entries.stream().map(ActionLog.Entry::action).toList());
        assertEquals("ACTIVE", entries.get(0).fromValue());
        assertEquals("MAINTENANCE", entries.get(0).toValue());
        assertEquals(5L, entries.get(0).lockerId());
        assertEquals("HCM-01 · Tủ A", entries.get(0).targetLabel());
        assertNull(entries.get(1).fromValue());
        assertEquals("7", entries.get(1).toValue());

        before = locker.auditFields();
        locker.setStatus("ACTIVE");
        locker.setAssignedTechnicianId(null);
        entries = ActionAuditListener.lockerChanges(locker, before, locker.auditFields());
        assertEquals(List.of("LOCKER_ACTIVATED", "LOCKER_TECHNICIAN_UNASSIGNED"),
                entries.stream().map(ActionLog.Entry::action).toList());
    }

    @Test
    void ticketBlockAndSoftDeleteKeepTheirOwnActions() {
        LockerUnit locker = locker();
        Map<String, String> before = locker.auditFields();
        locker.setStatus("MAINTENANCE");
        locker.setMaintenanceSource("TICKET");
        assertEquals("LOCKER_MAINTENANCE_BY_TICKET",
                ActionAuditListener.lockerChanges(locker, before, locker.auditFields()).getFirst().action());

        before = locker.auditFields();
        locker.setDeletedAt(LocalDateTime.now());
        locker.setDeletedReason("Gỡ tủ");
        List<ActionLog.Entry> deleted = ActionAuditListener.lockerChanges(locker, before, locker.auditFields());
        assertEquals(1, deleted.size());
        assertEquals("LOCKER_DELETED", deleted.getFirst().action());
        assertEquals("Gỡ tủ", deleted.getFirst().note());
    }

    @Test
    void boxOrderFlowIsQuietButFaultsAndStaffActionsAreLogged() {
        LockerBox box = box("AVAILABLE");
        Map<String, String> before = box.auditFields();
        box.setStatus("RESERVED");
        assertTrue(ActionAuditListener.boxChanges(box, before, box.auditFields()).isEmpty(), "đặt đơn giữ ô: không ghi");

        AtomicReference<List<ActionLog.Entry>> staff = new AtomicReference<>();
        Map<String, String> reserved = box.auditFields();
        box.setStatus("AVAILABLE");
        RequestActor.runAs(ADMIN, () -> staff.set(ActionAuditListener.boxChanges(box, reserved, box.auditFields())));
        assertEquals("BOX_STATUS_CHANGED", staff.get().getFirst().action(), "admin nhả ô tay: có ghi");

        before = box.auditFields();
        box.setStatus("FAULT");
        box.setFaultReason("Kẹt khoá");
        List<ActionLog.Entry> fault = ActionAuditListener.boxChanges(box, before, box.auditFields());
        assertEquals("BOX_FAULT", fault.getFirst().action());
        assertEquals("Kẹt khoá", fault.getFirst().note());
        assertEquals(9L, fault.getFirst().lockerId());
        assertEquals("#3", fault.getFirst().targetLabel());

        before = box.auditFields();
        box.setStatus("AVAILABLE");
        assertEquals("BOX_BACK_IN_SERVICE",
                ActionAuditListener.boxChanges(box, before, box.auditFields()).getFirst().action());
    }

    @Test
    void droneFlightFlowIsQuietButMaintenanceAndAssignmentAreLogged() {
        DroneUnit drone = new DroneUnit();
        drone.setId(4L);
        drone.setCode("DRN-004");
        drone.setLockerId(2L);
        drone.setStatus(DroneStatus.IDLE);
        Map<String, String> before = drone.auditFields();
        drone.setStatus(DroneStatus.IN_FLIGHT);
        assertTrue(ActionAuditListener.droneChanges(drone, before, drone.auditFields()).isEmpty());

        before = drone.auditFields();
        drone.setStatus(DroneStatus.MAINTENANCE);
        drone.setAssignedTechnicianId(11L);
        drone.setLockerId(3L);
        List<ActionLog.Entry> entries = ActionAuditListener.droneChanges(drone, before, drone.auditFields());
        assertEquals(List.of("DRONE_STATUS_CHANGED", "DRONE_TECHNICIAN_ASSIGNED", "DRONE_MOVED"),
                entries.stream().map(ActionLog.Entry::action).toList());
        assertEquals("IN_FLIGHT", entries.getFirst().fromValue());
        assertEquals("2", entries.get(2).fromValue());
        assertEquals("3", entries.get(2).toValue());
    }

    private static LockerUnit locker() {
        LockerUnit locker = new LockerUnit();
        locker.setId(5L);
        locker.setCode("HCM-01");
        locker.setName("Tủ A");
        locker.setStatus("ACTIVE");
        return locker;
    }

    private static LockerBox box(String status) {
        LockerBox box = new LockerBox();
        box.setId(31L);
        box.setLockerId(9L);
        box.setBoxNumber(3);
        box.setStatus(status);
        return box;
    }
}
