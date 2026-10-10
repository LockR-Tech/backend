package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.user.dto.AdminUserView;
import com.huynqb.laundrylocker.user.dto.UserGrowthPoint;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceAdminTest {

    @Mock private UserProfileRepository repository;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @InjectMocks private UserProfileService service;

    @Test
    void updateWithoutStatusKeepsSuspension() {
        UserProfile user = user(5L, "INACTIVE");
        when(repository.findById(5L)).thenReturn(Optional.of(user));
        when(repository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(5L, new UserProfileRequest(null, null, "Tên mới", null, null, null, null, null));

        assertEquals("INACTIVE", user.getStatus());
        assertEquals("Tên mới", user.getFirstName());
    }

    @Test
    void newProfileWithoutStatusDefaultsToActive() {
        when(repository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new UserProfileRequest("a@b.vn", "0901", "A", null, null, null, null, null));

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(saved.capture());
        assertEquals("ACTIVE", saved.getValue().getStatus());
    }

    @Test
    void updateStatusRejectsMissingOrUnknownValue() {
        BusinessException missing = assertThrows(BusinessException.class, () -> service.updateStatus(5L, null));
        BusinessException literalNull = assertThrows(BusinessException.class, () -> service.updateStatus(5L, "null"));

        assertEquals("USER_STATUS_INVALID", missing.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatus());
        assertEquals("USER_STATUS_INVALID", literalNull.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatusNormalizesCase() {
        UserProfile user = user(5L, "ACTIVE");
        when(repository.findById(5L)).thenReturn(Optional.of(user));
        when(repository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(5L, " inactive ");

        assertEquals("INACTIVE", user.getStatus());
    }

    @Test
    void uniquenessCheckForUpdateIgnoresSameUser() {
        when(repository.findFirstByEmailIgnoreCase("mine@b.vn")).thenReturn(Optional.of(user(5L, "ACTIVE")));
        when(repository.findFirstByPhoneNumber("0909")).thenReturn(Optional.of(user(6L, "ACTIVE")));

        service.assertUniqueForUpdate(5L, "mine@b.vn", null);
        BusinessException taken = assertThrows(BusinessException.class,
                () -> service.assertUniqueForUpdate(5L, null, "0909"));

        assertEquals("USER_PHONE_TAKEN", taken.getCode());
        assertEquals(HttpStatus.CONFLICT, taken.getStatus());
    }

    @Test
    void adminViewCarriesTimestamps() {
        UserProfile user = user(5L, "ACTIVE");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        user.setUpdatedAt(LocalDateTime.of(2026, 2, 3, 4, 5));
        when(repository.findById(5L)).thenReturn(Optional.of(user));

        AdminUserView view = service.getAdminView(5L);

        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), view.createdAt());
        assertEquals(LocalDateTime.of(2026, 2, 3, 4, 5), view.updatedAt());
    }

    @Test
    void growthZeroFillsMonthsAndBucketsByVietnamTime() {
        // 2026-10-10 05:00 UTC; lưu trữ UTC như container production.
        service.setTime(new BusinessTime(
                ZoneId.of("UTC"), BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-10-10T05:00:00Z"), ZoneId.of("UTC"))));
        when(repository.findCreatedAtSince(LocalDateTime.of(2026, 7, 31, 17, 0))).thenReturn(List.of(
                LocalDateTime.of(2026, 8, 1, 1, 0),
                // 31/08 18:00 UTC = 01/09 01:00 giờ Việt Nam ⇒ tính vào tháng 9.
                LocalDateTime.of(2026, 8, 31, 18, 0),
                LocalDateTime.of(2026, 10, 9, 10, 0)));

        List<UserGrowthPoint> points = service.growth(3);

        assertEquals(List.of(
                new UserGrowthPoint("2026-08", 1),
                new UserGrowthPoint("2026-09", 1),
                new UserGrowthPoint("2026-10", 1)), points);
    }

    @Test
    void growthDefaultsToTwelveMonthsAndCapsAtThirtySix() {
        service.setTime(new BusinessTime(
                ZoneId.of("UTC"), BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-10-10T05:00:00Z"), ZoneId.of("UTC"))));
        when(repository.findCreatedAtSince(any())).thenReturn(List.of());

        assertEquals(12, service.growth(null).size());
        assertEquals("2025-11", service.growth(null).get(0).month());
        assertEquals(36, service.growth(500).size());
        assertEquals(1, service.growth(0).size());
    }

    private UserProfile user(Long id, String status) {
        UserProfile user = new UserProfile();
        user.setId(id);
        user.setStatus(status);
        user.setRoles("CUSTOMER");
        return user;
    }
}
