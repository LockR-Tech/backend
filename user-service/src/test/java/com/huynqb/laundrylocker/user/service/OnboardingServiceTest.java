package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.dto.OnboardingStateView;
import com.huynqb.laundrylocker.user.model.UserOnboardingState;
import com.huynqb.laundrylocker.user.repository.UserOnboardingStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Luật lưu trạng thái hướng dẫn lần đầu (F3-G13): mặc định, bỏ qua vĩnh viễn, giữ phiên bản lớn nhất,
/// kiểm mã nhóm/phiên bản.
class OnboardingServiceTest {

    private final Map<Long, UserOnboardingState> db = new HashMap<>();
    private OnboardingService service;

    @BeforeEach
    void setUp() {
        UserOnboardingStateRepository repository = mock(UserOnboardingStateRepository.class);
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            UserOnboardingState s = inv.getArgument(0);
            db.put(s.getUserId(), s);
            return s;
        });
        service = new OnboardingService(repository);
    }

    @Test
    void userWithoutRowGetsDefaults() {
        OnboardingStateView view = service.get(7L);
        assertTrue(view.autoShow());
        assertTrue(view.seenGroups().isEmpty());
        assertNull(view.updatedAt());
    }

    @Test
    void skipForeverIsStoredAndCanBeTurnedBackOn() {
        assertFalse(service.setAutoShow(7L, false).autoShow());
        assertFalse(service.get(7L).autoShow());
        assertTrue(service.setAutoShow(7L, true).autoShow());
    }

    @Test
    void autoShowIsRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setAutoShow(7L, null));
        assertEquals("ONBOARDING_AUTO_SHOW_REQUIRED", ex.getCode());
    }

    @Test
    void seenKeepsTheHighestVersionPerGroup() {
        service.markSeen(7L, "admin-orders", 2);
        service.markSeen(7L, "admin-orders", 1);          // máy cũ gửi phiên bản thấp hơn
        OnboardingStateView view = service.markSeen(7L, "customer-home", 1);
        assertEquals(Map.of("admin-orders", 2, "customer-home", 1), view.seenGroups());
        assertEquals(3, service.markSeen(7L, "admin-orders", 3).seenGroups().get("admin-orders"));
    }

    @Test
    void seenDoesNotTouchAutoShow() {
        service.setAutoShow(7L, false);
        assertFalse(service.markSeen(7L, "admin-orders", 1).autoShow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Admin-Orders", "admin_orders", "admin orders", "-admin", "admin/orders",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void badGroupCodesAreRejected(String group) {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.markSeen(7L, group, 1));
        assertEquals("ONBOARDING_GROUP_INVALID", ex.getCode());
    }

    @Test
    void nullGroupIsRejected() {
        assertEquals("ONBOARDING_GROUP_INVALID",
                assertThrows(BusinessException.class, () -> service.markSeen(7L, null, 1)).getCode());
    }

    @Test
    void badVersionsAreRejected() {
        for (Integer v : new Integer[]{null, 0, -1, OnboardingService.MAX_VERSION + 1}) {
            assertEquals("ONBOARDING_VERSION_INVALID",
                    assertThrows(BusinessException.class, () -> service.markSeen(7L, "admin-orders", v)).getCode());
        }
    }

    @Test
    void missingUserIsUnauthorized() {
        assertEquals("AUTH_REQUIRED", assertThrows(BusinessException.class, () -> service.get(null)).getCode());
        assertEquals("AUTH_REQUIRED",
                assertThrows(BusinessException.class, () -> service.markSeen(null, "a", 1)).getCode());
    }

    @Test
    void groupCountIsCapped() {
        for (int i = 0; i < OnboardingService.MAX_GROUPS; i++) {
            service.markSeen(7L, "g-" + i, 1);
        }
        service.markSeen(7L, "g-1", 2);                    // nhóm đã có vẫn cập nhật được
        assertEquals("ONBOARDING_TOO_MANY_GROUPS",
                assertThrows(BusinessException.class, () -> service.markSeen(7L, "g-new", 1)).getCode());
    }
}
