package com.huynqb.laundrylocker.store.service;

import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.store.client.OrderClient;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.model.StoreLocation;
import com.huynqb.laundrylocker.store.repository.StoreRepository;
import com.huynqb.laundrylocker.store.settings.TestStoreRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceNearbyTest {

    @Mock
    StoreRepository repository;
    @Mock
    OrderClient orderClient;
    @Mock
    CloudinaryMediaStorage mediaStorage;

    @BeforeEach
    void setUp() {
        // Cửa hàng gần (~1,1 km) và cửa hàng xa (~22 km) so với điểm tìm (10.77, 106.70).
        when(repository.findByStatusAndActiveTrue("ACTIVE"))
                .thenReturn(List.of(store(1L, 10.78, 106.70), store(2L, 10.97, 106.70)));
    }

    @Test
    void defaultRadiusIsTenKilometres() {
        StoreService service = new StoreService(repository, orderClient, mediaStorage, TestStoreRules.defaults());

        assertEquals(List.of(1L), ids(service.nearby(10.77, 106.70, null)));
    }

    @Test
    void defaultRadiusFollowsAdminSettingButExplicitRadiusWins() {
        StoreService service = new StoreService(
                repository, orderClient, mediaStorage,
                TestStoreRules.of(Map.of("app.store.nearby-default-radius-km", "25.5")));

        assertEquals(List.of(1L, 2L), ids(service.nearby(10.77, 106.70, null)));
        assertEquals(List.of(1L), ids(service.nearby(10.77, 106.70, 5.0)));
    }

    private static List<Long> ids(List<StoreResponse> stores) {
        return stores.stream().map(StoreResponse::id).toList();
    }

    private static StoreLocation store(Long id, double latitude, double longitude) {
        StoreLocation store = new StoreLocation();
        store.setId(id);
        store.setName("Store " + id);
        store.setLatitude(latitude);
        store.setLongitude(longitude);
        return store;
    }
}
