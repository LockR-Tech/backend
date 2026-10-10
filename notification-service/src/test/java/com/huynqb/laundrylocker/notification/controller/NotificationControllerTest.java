package com.huynqb.laundrylocker.notification.controller;

import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.notification.dto.AdminBroadcastRequest;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.service.DeliveryNotificationService;
import com.huynqb.laundrylocker.notification.service.DronePositionService;
import com.huynqb.laundrylocker.notification.service.GuestNotificationService;
import com.huynqb.laundrylocker.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Lớp HTTP: thao tác của người dùng gắn với X-User-Id, thao tác admin theo id, broadcast nhận `userIds`.
class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(
                        notificationService,
                        mock(DeliveryNotificationService.class),
                        mock(DronePositionService.class),
                        mock(GuestNotificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void userMarkReadAndDeleteArePassedTheCallerId() throws Exception {
        when(notificationService.markRead(5L, 7L)).thenReturn(response(5L, 7L, "READ"));

        mvc.perform(patch("/api/notifications/5/read").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));
        mvc.perform(delete("/api/notifications/5").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_DELETED"));

        verify(notificationService).delete(5L, 7L);
        verify(notificationService, never()).delete(5L);
    }

    @Test
    void perUserListReturnsDataOnlyForTheCallerOrAnAdmin() throws Exception {
        when(notificationService.getByUser(7L)).thenReturn(List.of(response(5L, 7L, "UNREAD")));

        mvc.perform(get("/api/notifications/user/7").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5));
        mvc.perform(get("/api/notifications/user/7").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5));
        mvc.perform(get("/api/notifications/user/7").header("X-User-Id", "8").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/notifications/user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(notificationService, org.mockito.Mockito.times(2)).getByUser(7L);
    }

    @Test
    void userDeleteOfAnotherUsersNotificationIsNotFound() throws Exception {
        doThrow(new NotFoundException("Notification", 5L)).when(notificationService).delete(5L, 8L);

        mvc.perform(delete("/api/notifications/5").header("X-User-Id", "8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void adminDeleteReadAndResendWorkById() throws Exception {
        when(notificationService.markRead(5L)).thenReturn(response(5L, 7L, "READ"));
        when(notificationService.resend(5L)).thenReturn(response(5L, 7L, "UNREAD"));
        doThrow(new NotFoundException("Notification", 9L)).when(notificationService).delete(9L);

        mvc.perform(patch("/api/admin/notifications/5/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));
        mvc.perform(post("/api/admin/notifications/5/resend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_RESENT"))
                .andExpect(jsonPath("$.data.userId").value(7));
        mvc.perform(delete("/api/admin/notifications/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_DELETED"));
        mvc.perform(delete("/api/admin/notifications/9"))
                .andExpect(status().isNotFound());

        verify(notificationService).delete(5L);
    }

    @Test
    void broadcastAcceptsOptionalUserIds() throws Exception {
        when(notificationService.adminBroadcast(any())).thenReturn(List.of(response(1L, 3L, "UNREAD")));

        mvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bảo trì\",\"message\":\"Tủ tạm dừng\",\"userIds\":[3,4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(3));
        mvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bảo trì\",\"message\":\"Tủ tạm dừng\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminBroadcastRequest> captor = ArgumentCaptor.forClass(AdminBroadcastRequest.class);
        verify(notificationService, org.mockito.Mockito.times(2)).adminBroadcast(captor.capture());
        assertThat(captor.getAllValues().get(0).userIds()).containsExactly(3L, 4L);
        assertThat(captor.getAllValues().get(1).userIds()).isNull();
    }

    @Test
    void broadcastWithoutTitleIsRejected() throws Exception {
        mvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tủ tạm dừng\",\"userIds\":[3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(notificationService, never()).adminBroadcast(any());
    }

    private static NotificationResponse response(Long id, Long userId, String status) {
        return new NotificationResponse(
                id, userId, "Tiêu đề", "Nội dung", "SYSTEM", "READ".equals(status), null, null, status, null, null);
    }
}
