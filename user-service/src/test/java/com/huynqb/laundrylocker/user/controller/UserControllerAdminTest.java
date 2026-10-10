package com.huynqb.laundrylocker.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.client.AuthClient;
import com.huynqb.laundrylocker.user.client.NotificationClient;
import com.huynqb.laundrylocker.user.dto.AdminUserView;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.service.UserProfileService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerAdminTest {

    @Mock private UserProfileService userProfileService;
    @Mock private AuthClient authClient;
    @Mock private NotificationClient notificationClient;

    private UserController controller;
    private final UserSummary current =
            new UserSummary(5L, "old@lockr.vn", "0901000005", "Khách A", "ACTIVE", Set.of("CUSTOMER"), null);

    @BeforeEach
    void setUp() {
        controller = new UserController(userProfileService, authClient, notificationClient, new ObjectMapper());
        when(userProfileService.get(5L)).thenReturn(current);
        when(authClient.updateStatus(anyLong(), anyMap())).thenReturn(ApiResponse.ok(Map.of()));
        when(authClient.updateIdentifiers(anyLong(), anyMap())).thenReturn(ApiResponse.ok(Map.of()));
    }

    @Test
    void statusIsRequired() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.adminStatus(5L, null, Map.of("enabled", false)));

        assertEquals("USER_STATUS_INVALID", ex.getCode());
        verifyNoInteractions(authClient);
        verify(userProfileService, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void suspendingPushesStatusToAuthBeforeProfile() {
        controller.adminStatus(5L, null, Map.of("status", "INACTIVE"));

        InOrder order = inOrder(authClient, userProfileService);
        order.verify(authClient).updateStatus(5L, Map.of("status", "INACTIVE"));
        order.verify(userProfileService).updateStatus(5L, "INACTIVE");
    }

    @Test
    void authOutageLeavesProfileUntouched() {
        when(authClient.updateStatus(anyLong(), anyMap())).thenThrow(new RuntimeException("connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.adminStatus(5L, "INACTIVE", null));

        assertEquals("AUTH_SYNC_FAILED", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(userProfileService, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void deleteDisablesLoginBeforeRemovingProfile() {
        controller.adminDelete(5L);

        InOrder order = inOrder(authClient, userProfileService);
        order.verify(authClient).updateStatus(5L, Map.of("status", "INACTIVE"));
        order.verify(userProfileService).delete(5L);
    }

    @Test
    void deleteAbortsWhenLoginCannotBeDisabled() {
        when(authClient.updateStatus(anyLong(), anyMap())).thenThrow(new RuntimeException("timeout"));

        assertThrows(BusinessException.class, () -> controller.adminDelete(5L));

        verify(userProfileService, never()).delete(anyLong());
    }

    @Test
    void adminUpdateSyncsChangedIdentifiersAndKeepsStatusWhenOmitted() {
        controller.adminUpdate(5L, new UserProfileRequest(
                " new@lockr.vn ", "0901000005", "Khách", "A", null, null, null, null));

        verify(userProfileService).assertUniqueForUpdate(5L, "new@lockr.vn", null);
        verify(authClient).updateIdentifiers(5L, Map.of("email", "new@lockr.vn"));
        verify(authClient, never()).updateStatus(anyLong(), anyMap());
        ArgumentCaptor<UserProfileRequest> saved = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(userProfileService).update(eq(5L), saved.capture());
        assertEquals("new@lockr.vn", saved.getValue().email());
        assertNull(saved.getValue().status());
    }

    @Test
    void adminUpdateSurfacesAuthConflict() {
        byte[] body = "{\"success\":false,\"code\":\"AUTH_EMAIL_TAKEN\",\"message\":\"Email đã có tài khoản đăng nhập\"}"
                .getBytes(StandardCharsets.UTF_8);
        Request request = Request.create(
                Request.HttpMethod.PUT, "/internal/auth/users/5/identifiers", Map.of(), null, StandardCharsets.UTF_8, null);
        when(authClient.updateIdentifiers(anyLong(), anyMap()))
                .thenThrow(new FeignException.Conflict("409", request, body, Map.of()));

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.adminUpdate(5L,
                new UserProfileRequest("taken@lockr.vn", null, null, null, null, null, null, null)));

        assertEquals("AUTH_EMAIL_TAKEN", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userProfileService, never()).update(anyLong(), any());
    }

    @Test
    void adminUpdateSyncsStatusOnlyWhenItChanges() {
        controller.adminUpdate(5L, new UserProfileRequest(null, null, null, null, null, null, "ACTIVE", null));
        controller.adminUpdate(5L, new UserProfileRequest(null, null, null, null, null, null, "inactive", null));

        verify(authClient).updateStatus(5L, Map.of("status", "INACTIVE"));
        verify(authClient, never()).updateIdentifiers(anyLong(), anyMap());
    }

    @Test
    void selfProfileUpdateCannotChangeRolesOrStatus() {
        controller.updateProfile(5L, new UserProfileRequest(
                null, null, "Tên", null, null, null, "ACTIVE", Set.of("ADMIN")));

        ArgumentCaptor<UserProfileRequest> saved = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(userProfileService).update(eq(5L), saved.capture());
        assertNull(saved.getValue().status());
        assertNull(saved.getValue().roles());
        assertEquals("Tên", saved.getValue().firstName());
    }

    @Test
    void adminDetailIncludesProviderAndTimestamps() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 8, 0);
        when(userProfileService.getAdminView(5L)).thenReturn(new AdminUserView(
                5L, "old@lockr.vn", "0901000005", "Khách A", "ACTIVE", Set.of("CUSTOMER"),
                created, created, null, null, null));
        Map<String, Object> account = new HashMap<>();
        account.put("userId", 5L);
        account.put("provider", "GOOGLE.COM");
        account.put("emailVerified", true);
        when(authClient.accountsByUsers(List.of(5L))).thenReturn(ApiResponse.ok(List.of(account)));

        AdminUserView view = controller.adminGet(5L).data();

        assertEquals("GOOGLE.COM", view.provider());
        assertEquals(created, view.createdAt());
    }

    @Test
    void adminDetailStillWorksWhenAuthLookupFails() {
        when(userProfileService.getAdminView(5L)).thenReturn(new AdminUserView(
                5L, null, null, "Khách A", "ACTIVE", Set.of("CUSTOMER"), null, null, null, null, null));
        when(authClient.accountsByUsers(any())).thenThrow(new RuntimeException("down"));

        assertNull(controller.adminGet(5L).data().provider());
    }
}
