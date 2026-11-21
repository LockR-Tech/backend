package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import com.huynqb.laundrylocker.user.model.UserOnboardingState;
import com.huynqb.laundrylocker.user.repository.UserOnboardingStateRepository;
import com.huynqb.laundrylocker.user.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Ba endpoint trạng thái hướng dẫn lần đầu qua HTTP (F3-G13): chỉ thao tác trên chính người đăng nhập
/// (`X-User-Id`), không gọi thay được cho người khác.
class OnboardingControllerTest {

    private final Map<Long, UserOnboardingState> db = new HashMap<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserOnboardingStateRepository repository = mock(UserOnboardingStateRepository.class);
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            UserOnboardingState s = inv.getArgument(0);
            db.put(s.getUserId(), s);
            return s;
        });
        mvc = MockMvcBuilders.standaloneSetup(new OnboardingController(new OnboardingService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReturnsDefaultsForNewUser() throws Exception {
        mvc.perform(get("/api/user/onboarding").header("X-User-Id", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoShow").value(true))
                .andExpect(jsonPath("$.data.seenGroups").isEmpty());
    }

    @Test
    void skipForeverIsSavedForTheCallerOnly() throws Exception {
        mvc.perform(put("/api/user/onboarding/auto-show").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"autoShow\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoShow").value(false));
        mvc.perform(get("/api/user/onboarding").header("X-User-Id", "11"))
                .andExpect(jsonPath("$.data.autoShow").value(false));
        mvc.perform(get("/api/user/onboarding").header("X-User-Id", "12"))
                .andExpect(jsonPath("$.data.autoShow").value(true));
    }

    @Test
    void seenIsRecordedAndReturned() throws Exception {
        mvc.perform(post("/api/user/onboarding/seen").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"admin-orders\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seenGroups.admin-orders").value(2));
        assertEquals(2, db.get(11L).getSeenGroups().get("admin-orders"));
    }

    @Test
    void callerCannotWriteSomeoneElsesStateThroughTheBody() throws Exception {
        // Kẻ gọi (11) cố chèn id người khác (12) vào body: bị bỏ qua, chỉ dòng của 11 thay đổi.
        mvc.perform(post("/api/user/onboarding/seen").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":12,\"group\":\"admin-orders\",\"version\":5}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/user/onboarding/auto-show").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":12,\"autoShow\":false}"))
                .andExpect(status().isOk());
        assertNull(db.get(12L));
        assertFalse(db.get(11L).isAutoShow());
        mvc.perform(get("/api/user/onboarding").header("X-User-Id", "12"))
                .andExpect(jsonPath("$.data.autoShow").value(true))
                .andExpect(jsonPath("$.data.seenGroups").isEmpty());
    }

    @Test
    void requestsWithoutIdentityAreRejected() throws Exception {
        mvc.perform(get("/api/user/onboarding"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEADER_REQUIRED"));
        mvc.perform(post("/api/user/onboarding/seen")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"admin-orders\",\"version\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/user/onboarding/auto-show")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"autoShow\":false}"))
                .andExpect(status().isBadRequest());
        assertTrue(db.isEmpty());
    }

    @Test
    void invalidGroupAndVersionAreRejected() throws Exception {
        mvc.perform(post("/api/user/onboarding/seen").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"Admin_Orders\",\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONBOARDING_GROUP_INVALID"));
        mvc.perform(post("/api/user/onboarding/seen").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"admin-orders\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONBOARDING_VERSION_INVALID"));
        mvc.perform(put("/api/user/onboarding/auto-show").header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONBOARDING_AUTO_SHOW_REQUIRED"));
        assertTrue(db.isEmpty());
    }
}
