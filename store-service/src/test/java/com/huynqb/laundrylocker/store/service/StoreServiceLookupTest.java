package com.huynqb.laundrylocker.store.service;

import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.store.client.OrderClient;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.model.StoreLocation;
import com.huynqb.laundrylocker.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceLookupTest {

    @Mock private StoreRepository repository;
    @Mock private OrderClient orderClient;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @InjectMocks private StoreService service;

    @Test
    void getManyLoadsOnlyRequestedStores() {
        when(repository.findAllById(List.of(3L, 4L))).thenReturn(List.of(store(3L, "Chi nhánh Quận 1")));

        List<StoreResponse> result = service.getMany(Arrays.asList(3L, 4L, 3L, null));

        assertEquals(1, result.size());
        assertEquals("Chi nhánh Quận 1", result.get(0).name());
        verify(repository, never()).findAll();
    }

    @Test
    void getManyWithoutIdsReturnsAllStores() {
        when(repository.findAll()).thenReturn(List.of(store(3L, "A"), store(4L, "B")));

        assertEquals(2, service.getMany(null).size());
    }

    private StoreLocation store(Long id, String name) {
        StoreLocation store = new StoreLocation();
        store.setId(id);
        store.setName(name);
        store.setAddress("1 Lê Lợi");
        return store;
    }
}
