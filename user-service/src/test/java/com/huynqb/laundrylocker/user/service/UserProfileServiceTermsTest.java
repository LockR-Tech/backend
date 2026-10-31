package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.user.dto.UserProfileView;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Khách bấm "Đồng ý" Điều khoản dịch vụ lần đầu đăng nhập: lưu true + mốc + phiên bản; hồ sơ trả về cờ đó.
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTermsTest {

    @Mock private UserProfileRepository repository;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @InjectMocks private UserProfileService service;

    private UserProfile customer() {
        UserProfile user = new UserProfile();
        user.setId(7L);
        user.setFirstName("Nguyễn");
        user.setLastName("An");
        user.setRoles("CUSTOMER");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void taiKhoanMoi_chuaDongYDieuKhoan() {
        customer();

        UserProfileView view = service.getProfileView(7L, true);

        assertFalse(view.termsAccepted());
        assertEquals(null, view.termsAcceptedAt());
    }

    @Test
    void dongY_luuTrueMocVaPhienBan() {
        UserProfile user = customer();

        UserProfileView view = service.acceptTerms(7L, " 2026-10-31 ", true);

        assertTrue(user.isTermsAccepted());
        assertNotNull(user.getTermsAcceptedAt());
        assertEquals("2026-10-31", user.getTermsVersion());
        verify(repository).save(user);
        assertTrue(view.termsAccepted());
        assertEquals("2026-10-31", view.termsVersion());
    }

    @Test
    void dongYLaiCungPhienBan_khongDoiMocCu() {
        UserProfile user = customer();
        LocalDateTime first = LocalDateTime.of(2026, 10, 1, 9, 0);
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(first);
        user.setTermsVersion("2026-10-31");

        service.acceptTerms(7L, "2026-10-31", true);

        assertEquals(first, user.getTermsAcceptedAt());
        verify(repository, never()).save(any());
    }

    @Test
    void dongYPhienBanMoi_capNhatMoc() {
        UserProfile user = customer();
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        user.setTermsVersion("2026-10-01");

        service.acceptTerms(7L, "2026-12-01", true);

        assertEquals("2026-12-01", user.getTermsVersion());
        assertTrue(user.getTermsAcceptedAt().isAfter(LocalDateTime.of(2026, 10, 1, 9, 0)));
    }

    @Test
    void phienBanQuaDai_bao400() {
        customer();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.acceptTerms(7L, "x".repeat(31), true));
        assertEquals("TERMS_VERSION_INVALID", ex.getCode());
    }
}
