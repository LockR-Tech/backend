package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.locker.model.LockerUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// F3-G16: tủ chỉ nhận đơn mới khi đang hoạt động VÀ có KTV phụ trách.
class LockerOrderPauseTest {

    @Test
    void activeLockerWithTechnicianTakesOrders() {
        LockerUnit locker = locker("ACTIVE", 7L);
        assertTrue(LockerService.acceptsNewOrders(locker));
        assertNull(LockerService.orderPauseReason(locker));
    }

    @Test
    void activeLockerWithoutTechnicianIsPaused() {
        LockerUnit locker = locker("ACTIVE", null);
        assertFalse(LockerService.acceptsNewOrders(locker));
        assertEquals("NO_TECHNICIAN", LockerService.orderPauseReason(locker));
    }

    @Test
    void maintenanceAndInstallationWinOverTheMissingTechnician() {
        assertEquals("MAINTENANCE", LockerService.orderPauseReason(locker("MAINTENANCE", null)));
        assertEquals("MAINTENANCE", LockerService.orderPauseReason(locker("INACTIVE", 7L)));
        assertEquals("INSTALLING", LockerService.orderPauseReason(locker("COMMISSIONING", null)));
    }

    private static LockerUnit locker(String status, Long technicianId) {
        LockerUnit locker = new LockerUnit();
        locker.setStatus(status);
        locker.setAssignedTechnicianId(technicianId);
        return locker;
    }
}
