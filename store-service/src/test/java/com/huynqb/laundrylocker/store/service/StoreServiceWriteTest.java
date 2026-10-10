package com.huynqb.laundrylocker.store.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.store.client.LockerClient;
import com.huynqb.laundrylocker.store.client.LockerClient.LockerRef;
import com.huynqb.laundrylocker.store.client.OrderClient;
import com.huynqb.laundrylocker.store.dto.StoreRequest;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.model.StoreLocation;
import com.huynqb.laundrylocker.store.repository.StoreRepository;
import com.huynqb.laundrylocker.store.settings.TestStoreRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreServiceWriteTest {

    @Mock private StoreRepository repository;
    @Mock private OrderClient orderClient;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @Mock private LockerClient lockerClient;

    private StoreService service;
    private StoreLocation existing;

    @BeforeEach
    void setUp() {
        service = new StoreService(repository, orderClient, mediaStorage, TestStoreRules.defaults(), lockerClient);
        existing = new StoreLocation();
        existing.setId(3L);
        existing.setName("Chi nhánh cũ");
        existing.setContactPhone("0901000000");
        existing.setAddress("1 Đường A");
        existing.setLatitude(10.5);
        existing.setLongitude(106.5);
        existing.setDescription("Mô tả cũ");
        existing.setActive(false);
        existing.setStatus("INACTIVE");
        existing.setCreatedAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        existing.setUpdatedAt(LocalDateTime.of(2026, 9, 2, 8, 0));
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.save(any(StoreLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateKeepsFieldsTheRequestOmits() {
        StoreResponse result = service.update(3L, new StoreRequest("Chi nhánh mới", null, null, null, null, null, null, null, null));

        assertEquals("Chi nhánh mới", result.name());
        assertEquals("0901000000", result.contactPhone());
        assertEquals("1 Đường A", result.address());
        assertEquals(10.5, result.latitude());
        assertEquals(106.5, result.longitude());
        assertEquals("Mô tả cũ", result.description());
        assertEquals(false, result.active());
        assertEquals("INACTIVE", result.status());
    }

    @Test
    void updateOverwritesPresentFieldsAndEmptyTextClearsIt() {
        StoreResponse result = service.update(3L, new StoreRequest(
                "Chi nhánh mới", "", "2 Đường B", 10.7, 106.7, null, "", true, "ACTIVE"));

        assertNull(result.contactPhone());
        assertNull(result.description());
        assertEquals("2 Đường B", result.address());
        assertEquals(10.7, result.latitude());
        assertEquals(true, result.active());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void createWithoutActiveOrStatusDefaultsToActive() {
        StoreResponse result = service.create(new StoreRequest("Chi nhánh mới", "0902000000", null, null, null, null, null, null, null));

        assertEquals(true, result.active());
        assertEquals("ACTIVE", result.status());
        assertEquals("0902000000", result.contactPhone());
    }

    @Test
    void responseCarriesTimestamps() {
        StoreResponse result = service.get(3L);

        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0), result.createdAt());
        assertEquals(LocalDateTime.of(2026, 9, 2, 8, 0), result.updatedAt());
    }

    @Test
    void deleteIsRejectedWhileLockersStillBelongToTheStore() {
        when(lockerClient.lockersByStore(3L)).thenReturn(ApiResponse.ok(List.of(
                new LockerRef(11L, 3L, "CAB-A1", "Tủ A1"), new LockerRef(12L, 3L, null, "Tủ A2"))));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(3L));

        assertEquals("STORE_HAS_LOCKERS", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("2 tủ (CAB-A1, #12)"), ex.getMessage());
        verify(repository, never()).delete(any(StoreLocation.class));
    }

    @Test
    void deleteIgnoresLockersOfOtherStores() {
        when(lockerClient.lockersByStore(3L)).thenReturn(ApiResponse.ok(List.of(new LockerRef(11L, 4L, "CAB-B1", "Tủ B1"))));

        service.delete(3L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteSucceedsWhenTheStoreHasNoLockers() {
        when(lockerClient.lockersByStore(3L)).thenReturn(ApiResponse.ok(List.of()));

        service.delete(3L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteIsRefusedWhenLockersCannotBeChecked() {
        when(lockerClient.lockersByStore(3L)).thenThrow(new IllegalStateException("locker-service down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(3L));

        assertEquals("LOCKER_SERVICE_UNAVAILABLE", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(repository, never()).delete(any(StoreLocation.class));
    }

    @Test
    void deleteOfMissingStoreIsNotFoundWithoutCallingLockerService() {
        assertThrows(NotFoundException.class, () -> service.delete(9L));

        verify(lockerClient, never()).lockersByStore(anyLong());
    }
}
