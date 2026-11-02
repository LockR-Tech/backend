package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// App báo ngôn ngữ đang chọn; thư/SMS gửi ngoài app (order-service) đọc lại qua `UserSummary.language`.
@ExtendWith(MockitoExtension.class)
class UserProfileServiceLanguageTest {

    @Mock private UserProfileRepository repository;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @InjectMocks private UserProfileService service;

    private UserProfile user() {
        UserProfile user = new UserProfile();
        user.setId(7L);
        user.setRoles("CUSTOMER");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void luuNgonNguVaTraQuaUserSummary() {
        UserProfile user = user();

        assertEquals("en", service.updateLanguage(7L, "en-US"));

        assertEquals("en", user.getLanguage());
        verify(repository).save(user);
        assertEquals("en", service.get(7L).language());
    }

    @Test
    void giaTriLaHoacTrong_laTiengViet() {
        UserProfile user = user();

        assertEquals("vi", service.updateLanguage(7L, "fr"));
        assertEquals("vi", user.getLanguage());
    }

    @Test
    void khongDoi_khongGhiLai() {
        UserProfile user = user();
        user.setLanguage("vi");

        service.updateLanguage(7L, "vi");

        verify(repository, never()).save(user);
    }
}
