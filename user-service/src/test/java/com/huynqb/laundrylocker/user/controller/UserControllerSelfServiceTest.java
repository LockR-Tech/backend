package com.huynqb.laundrylocker.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.client.AuthClient;
import com.huynqb.laundrylocker.user.client.NotificationClient;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.dto.UserProfileView;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Hồ sơ của chính người dùng: đổi email/số điện thoại đồng bộ tài khoản đăng nhập, họ tên, `hasPassword`, đổi mật khẩu.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerSelfServiceTest {

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
        when(userProfileService.update(anyLong(), any())).thenReturn(current);
        when(authClient.updateIdentifiers(anyLong(), anyMap())).thenReturn(ApiResponse.ok(Map.of()));
    }

    @Test
    void phoneChangeIsCheckedThenSyncedToLoginBeforeProfileIsSaved() {
        controller.updateProfile(5L, request(null, " 0909888777 ", null, null, null));

        InOrder order = inOrder(userProfileService, authClient);
        order.verify(userProfileService).assertUniqueForUpdate(5L, null, "0909888777");
        order.verify(authClient).updateIdentifiers(5L, Map.of("phoneNumber", "0909888777"));
        ArgumentCaptor<UserProfileRequest> saved = ArgumentCaptor.forClass(UserProfileRequest.class);
        order.verify(userProfileService).update(eq(5L), saved.capture());
        assertEquals("0909888777", saved.getValue().phoneNumber());
    }

    @Test
    void duplicatePhoneStopsBeforeTouchingLoginAccount() {
        doThrow(new BusinessException("USER_PHONE_TAKEN", "taken", HttpStatus.CONFLICT))
                .when(userProfileService).assertUniqueForUpdate(5L, null, "0909888777");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateProfile(5L, request(null, "0909888777", null, null, null)));

        assertEquals("USER_PHONE_TAKEN", error.getCode());
        verify(authClient, never()).updateIdentifiers(anyLong(), anyMap());
        verify(userProfileService, never()).update(anyLong(), any());
    }

    @Test
    void loginAccountConflictKeepsProfileUnchanged() {
        byte[] body = "{\"success\":false,\"code\":\"AUTH_EMAIL_TAKEN\",\"message\":\"Email đã có tài khoản đăng nhập\"}"
                .getBytes(StandardCharsets.UTF_8);
        when(authClient.updateIdentifiers(anyLong(), anyMap()))
                .thenThrow(new FeignException.Conflict("409", feignRequest(), body, Map.of()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateProfile(5L, request("taken@lockr.vn", null, null, null, null)));

        assertEquals("AUTH_EMAIL_TAKEN", error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(userProfileService, never()).update(anyLong(), any());
    }

    @Test
    void unchangedOrBlankIdentifiersDoNotTouchLoginAccount() {
        controller.updateProfile(5L, request("OLD@lockr.vn", "", "Tên", "Mới", null));

        verify(authClient, never()).updateIdentifiers(anyLong(), anyMap());
        ArgumentCaptor<UserProfileRequest> saved = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(userProfileService).update(eq(5L), saved.capture());
        assertEquals(null, saved.getValue().phoneNumber());
        assertEquals("Tên", saved.getValue().firstName());
        assertEquals("Mới", saved.getValue().lastName());
    }

    @Test
    void legacyFullNameIsSplitAtLastSpace() {
        controller.updateProfile(5L, request(null, null, null, null, "  Nguyễn   Văn An "));

        ArgumentCaptor<UserProfileRequest> saved = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(userProfileService).update(eq(5L), saved.capture());
        assertEquals("Nguyễn Văn", saved.getValue().firstName());
        assertEquals("An", saved.getValue().lastName());
    }

    @Test
    void nameResolutionRules() {
        assertArrayEquals(new String[] {"An", ""},
                UserController.resolveNames(request(null, null, null, null, "An")));
        assertArrayEquals(new String[] {"Bình", null},
                UserController.resolveNames(request(null, null, "Bình", null, "Nguyễn Văn An")));
        assertArrayEquals(new String[] {null, null},
                UserController.resolveNames(request(null, null, null, null, " ")));
    }

    @Test
    void jsonBodyAcceptsBothNameShapes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        UserProfileRequest legacy = mapper.readValue("{\"fullName\":\"Nguyễn An\",\"phoneNumber\":\"0909\"}",
                UserProfileRequest.class);
        UserProfileRequest split = mapper.readValue("{\"firstName\":\"An\",\"lastName\":\"Nguyễn\",\"extra\":1}",
                UserProfileRequest.class);

        assertEquals("Nguyễn An", legacy.fullName());
        assertEquals("0909", legacy.phoneNumber());
        assertEquals("An", split.firstName());
        assertEquals("Nguyễn", split.lastName());
    }

    @Test
    void profileReportsWhetherAccountHasOwnPassword() {
        Map<String, Object> account = new HashMap<>();
        account.put("userId", 5);
        account.put("hasPassword", false);
        when(authClient.accountsByUsers(List.of(5L))).thenReturn(ApiResponse.ok(List.of(account)));
        when(userProfileService.getProfileView(eq(5L), anyBoolean())).thenAnswer(inv -> view(inv.getArgument(1)));

        assertEquals(false, controller.profile(5L).data().hasPassword());
        verify(userProfileService).getProfileView(5L, false);
    }

    @Test
    void profileStillLoadsWhenLoginLookupFails() {
        when(authClient.accountsByUsers(any())).thenThrow(new RuntimeException("down"));
        when(userProfileService.getProfileView(eq(5L), anyBoolean())).thenAnswer(inv -> view(inv.getArgument(1)));

        assertEquals(true, controller.profile(5L).data().hasPassword());
    }

    @Test
    void passwordChangeReturnsFreshTokensForThisDevice() {
        Map<String, Object> request = Map.of("currentPassword", "old", "newPassword", "new");
        when(authClient.changePassword(5L, request))
                .thenReturn(ApiResponse.ok(Map.of("accessToken", "new-access", "refreshToken", "new-refresh")));

        ApiResponse<Map<String, Object>> response = controller.changePassword(5L, request);

        assertEquals("PASSWORD_CHANGED", response.code());
        assertEquals("new-access", response.data().get("accessToken"));
        assertEquals("new-refresh", response.data().get("refreshToken"));
    }

    @Test
    void passwordChangeSurfacesAuthErrorCode() {
        byte[] body = "{\"success\":false,\"code\":\"AUTH_CURRENT_PASSWORD_REQUIRED\",\"message\":\"Vui lòng nhập mật khẩu hiện tại\"}"
                .getBytes(StandardCharsets.UTF_8);
        when(authClient.changePassword(anyLong(), anyMap()))
                .thenThrow(new FeignException.BadRequest("400", feignRequest(), body, Map.of()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.changePassword(5L, Map.of("newPassword", "new")));

        assertEquals("AUTH_CURRENT_PASSWORD_REQUIRED", error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private static UserProfileRequest request(
            String email, String phoneNumber, String firstName, String lastName, String fullName) {
        return new UserProfileRequest(email, phoneNumber, firstName, lastName, null, null, null, null, fullName);
    }

    private static UserProfileView view(boolean hasPassword) {
        return new UserProfileView(5L, "old@lockr.vn", "0901000005", "Khách A", "Khách", "A", null,
                "ACTIVE", Set.of("CUSTOMER"), null, hasPassword);
    }

    private static Request feignRequest() {
        return Request.create(Request.HttpMethod.PUT, "/internal/auth/users/5", Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
