package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.user.model.ActionLogRecord;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.ActionLogRepository;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/// Nhật ký thao tác (F3-G16): lưu đủ ai / lúc nào / làm gì, tên người chụp lúc ghi, mã KTV đổi thành tên.
class ActionLogServiceTest {

    private final ActionLogRepository repository = mock(ActionLogRepository.class);
    private final UserProfileRepository users = mock(UserProfileRepository.class);
    private final ActionLogService service = new ActionLogService(repository, users);

    @Test
    void storesActorNameAndTechnicianNames() {
        when(users.findById(1L)).thenReturn(Optional.of(user("Minh", "Trần")));
        when(users.findById(7L)).thenReturn(Optional.of(user("Bảo", "Phạm")));
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "LOCKER_TECHNICIAN_ASSIGNED");
        payload.put("targetType", "LOCKER");
        payload.put("targetId", 5L);
        payload.put("targetLabel", "HCM-01 · Tủ A");
        payload.put("lockerId", 5L);
        payload.put("toValue", "7");
        payload.put("actorUserId", 1L);
        payload.put("actorRoles", "ADMIN");

        service.store(new DomainEvent("e-1", DomainEventNames.ADMIN_ACTION_LOGGED, "locker-service",
                Instant.parse("2026-11-27T03:00:00Z"), payload));

        ArgumentCaptor<ActionLogRecord> saved = ArgumentCaptor.forClass(ActionLogRecord.class);
        verify(repository).save(saved.capture());
        ActionLogRecord record = saved.getValue();
        assertEquals("Minh Trần", record.getActorName());
        assertEquals("Bảo Phạm (#7)", record.getToValue());
        assertNull(record.getFromValue());
        assertEquals(5L, record.getLockerId());
        assertEquals(3, record.getOccurredAt().getHour(), "lưu giờ UTC như mọi cột thời gian khác");
        assertEquals(java.time.LocalDateTime.of(2026, 11, 26, 17, 0),
                ActionLogService.utcStartOf(java.time.LocalDate.of(2026, 11, 27)), "ngày 27/11 giờ VN bắt đầu 17:00 UTC hôm trước");
    }

    @Test
    void systemActionsAndDuplicatesAreHandled() {
        when(repository.existsByEventId("dup")).thenReturn(true);
        service.store(new DomainEvent("dup", DomainEventNames.ADMIN_ACTION_LOGGED, "locker-service", Instant.now(),
                Map.of("action", "LOCKER_MAINTENANCE_BY_TICKET")));
        verify(repository, never()).save(any());

        service.store(new DomainEvent("sys", DomainEventNames.ADMIN_ACTION_LOGGED, "locker-service", Instant.now(),
                Map.of("action", "LOCKER_MAINTENANCE_BY_TICKET", "fromValue", "ACTIVE", "toValue", "MAINTENANCE")));
        ArgumentCaptor<ActionLogRecord> saved = ArgumentCaptor.forClass(ActionLogRecord.class);
        verify(repository).save(saved.capture());
        assertNull(saved.getValue().getActorUserId());
        assertNull(saved.getValue().getActorName());
        assertEquals("MAINTENANCE", saved.getValue().getToValue());
    }

    private static UserProfile user(String first, String last) {
        UserProfile user = new UserProfile();
        user.setFirstName(first);
        user.setLastName(last);
        return user;
    }
}
