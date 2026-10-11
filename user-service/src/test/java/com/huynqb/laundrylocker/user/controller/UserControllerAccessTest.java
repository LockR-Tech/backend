package com.huynqb.laundrylocker.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.client.AuthClient;
import com.huynqb.laundrylocker.user.client.NotificationClient;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// /api/users/** (không phải /api/admin/**): chỉ chính chủ hoặc ADMIN.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerAccessTest {

    @Mock private UserProfileService userProfileService;
    @Mock private AuthClient authClient;
    @Mock private NotificationClient notificationClient;

    private UserController controller;
    private final UserSummary user5 =
            new UserSummary(5L, "a@lockr.vn", "0901000005", "Khách A", "ACTIVE", Set.of("CUSTOMER"), null);
    private final UserProfileRequest request =
            new UserProfileRequest(null, null, "Tên", "Mới", null, null, "ACTIVE", Set.of("ADMIN"));

    @BeforeEach
    void setUp() {
        controller = new UserController(userProfileService, authClient, notificationClient, new ObjectMapper());
        when(userProfileService.get(5L)).thenReturn(user5);
        when(userProfileService.list()).thenReturn(List.of(user5));
        when(userProfileService.update(anyLong(), any())).thenReturn(user5);
        when(userProfileService.create(any())).thenReturn(user5);
    }

    @Test
    void customerReadsOwnProfileById() {
        assertEquals(5L, controller.get(5L, 5L, "CUSTOMER").data().id());
        assertEquals(5L, controller.getLegacy(5L, 5L, "CUSTOMER").data().id());
    }

    @Test
    void customerCannotReadAnotherUser() {
        assertForbidden(() -> controller.get(5L, 9L, "CUSTOMER"));
        assertForbidden(() -> controller.getLegacy(5L, 9L, "LOCKER_TECHNICIAN"));
        assertForbidden(() -> controller.get(5L, null, null));
        verify(userProfileService, never()).get(anyLong());
    }

    @Test
    void adminReadsAnyUser() {
        assertEquals(5L, controller.get(5L, 1L, "ADMIN").data().id());
    }

    @Test
    void onlyAdminListsOrCreatesUsers() {
        assertForbidden(() -> controller.list("CUSTOMER"));
        assertForbidden(() -> controller.listLegacy("DRONE_TECHNICIAN"));
        assertForbidden(() -> controller.create(request, "CUSTOMER"));
        assertForbidden(() -> controller.createLegacy(request, null));
        verify(userProfileService, never()).list();
        verify(userProfileService, never()).create(any());

        assertEquals(1, controller.list("ADMIN").data().size());
        assertEquals(5L, controller.create(request, "ADMIN").data().id());
    }

    @Test
    void customerCannotUpdateAnotherUser() {
        assertForbidden(() -> controller.update(5L, request, 9L, "CUSTOMER"));
        verify(userProfileService, never()).update(anyLong(), any());
    }

    @Test
    void selfUpdateStillDropsStatusAndRoles() {
        controller.update(5L, request, 5L, "CUSTOMER");

        ArgumentCaptor<UserProfileRequest> sent = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(userProfileService).update(eq(5L), sent.capture());
        assertEquals("Tên", sent.getValue().firstName());
        assertNull(sent.getValue().status());
        assertNull(sent.getValue().roles());
    }

    private static void assertForbidden(Runnable call) {
        BusinessException ex = assertThrows(BusinessException.class, call::run);
        assertEquals("USER_FORBIDDEN", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
