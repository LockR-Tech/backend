package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceLookupTest {

    @Mock private UserProfileRepository repository;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @InjectMocks private UserProfileService service;

    @Test
    void getManyDeduplicatesIdsAndMapsFullName() {
        UserProfile user = new UserProfile();
        user.setId(7L);
        user.setFirstName("Nguyễn");
        user.setLastName("An");
        user.setPhoneNumber("0901234567");
        user.setEmail("an@example.com");
        user.setRoles("CUSTOMER");
        when(repository.findAllById(List.of(7L, 9L))).thenReturn(List.of(user));

        List<UserSummary> result = service.getMany(Arrays.asList(7L, null, 9L, 7L));

        verify(repository).findAllById(List.of(7L, 9L));
        assertEquals(1, result.size());
        assertEquals("Nguyễn An", result.get(0).fullName());
        assertEquals("0901234567", result.get(0).phoneNumber());
    }

    @Test
    void getManyWithoutIdsSkipsDatabase() {
        assertTrue(service.getMany(List.of()).isEmpty());
        assertTrue(service.getMany(null).isEmpty());
        verifyNoInteractions(repository);
    }
}
