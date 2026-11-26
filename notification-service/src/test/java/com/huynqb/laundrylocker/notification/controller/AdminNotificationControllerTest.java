package com.huynqb.laundrylocker.notification.controller;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationBulkDeleteRequest;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationStats;
import com.huynqb.laundrylocker.notification.service.AdminNotificationQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Lớp HTTP trang Thông báo admin: tham số lọc, thẻ số liệu, xoá hàng loạt kèm người xoá.
class AdminNotificationControllerTest {

    private final AdminNotificationQueryService service = mock(AdminNotificationQueryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminNotificationController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void searchPassesFiltersAndRecipientIds() throws Exception {
        when(service.search(2, 20, "UNREAD", "ORDER_CREATED", "an", List.of(8L, 9L)))
                .thenReturn(new PageResponse<>(List.of(), 2, 20, 41, 3));

        mvc.perform(get("/api/admin/notifications/search")
                        .param("page", "2").param("size", "20").param("status", "UNREAD")
                        .param("type", "ORDER_CREATED").param("search", "an").param("recipientIds", "8,9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(41))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void statsAreReturnedAsIs() throws Exception {
        when(service.stats()).thenReturn(new AdminNotificationStats(
                3, 1, 2, 60L, List.of(new AdminNotificationStats.TypeCount("ORDER_CREATED", 3))));

        mvc.perform(get("/api/admin/notifications/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(1))
                .andExpect(jsonPath("$.data.averageReadMinutes").value(60))
                .andExpect(jsonPath("$.data.types[0].type").value("ORDER_CREATED"));
    }

    @Test
    void bulkDeleteSendsBodyAndActor() throws Exception {
        when(service.bulkDelete(org.mockito.ArgumentMatchers.any(), eq(1L))).thenReturn(List.of(4L, 5L));

        mvc.perform(post("/api/admin/notifications/bulk-delete").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"all\":true,\"status\":\"READ\",\"recipientIds\":[8],\"reason\":\"dọn dẹp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATIONS_DELETED"))
                .andExpect(jsonPath("$.data[1]").value(5));

        ArgumentCaptor<AdminNotificationBulkDeleteRequest> body = ArgumentCaptor.forClass(AdminNotificationBulkDeleteRequest.class);
        verify(service).bulkDelete(body.capture(), eq(1L));
        assertTrue(body.getValue().all());
        assertEquals("READ", body.getValue().status());
        assertEquals(List.of(8L), body.getValue().recipientIds());
        assertEquals("dọn dẹp", body.getValue().reason());
    }
}
