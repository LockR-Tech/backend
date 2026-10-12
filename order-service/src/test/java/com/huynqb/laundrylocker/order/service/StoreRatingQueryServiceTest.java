package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.OrderRating;
import com.huynqb.laundrylocker.order.repository.OrderRatingRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreRatingQueryServiceTest {

    @Mock private OrderRatingRepository ratingRepository;
    @Mock private AdminReferenceResolver references;

    private StoreRatingQueryService service;

    @BeforeEach
    void setUp() {
        service = new StoreRatingQueryService(ratingRepository, references);
    }

    @Test
    void ratingsOfStoreLockersCarryReviewerName() {
        when(references.allLockers()).thenReturn(Lookup.of(Map.of(
                7L, locker(7L, 3L),
                8L, locker(8L, 4L),
                9L, locker(9L, null))));
        when(ratingRepository.findForStore(3L, Set.of(7L))).thenReturn(List.of(rating(1L, 44L, 5), rating(2L, 45L, 4)));
        when(references.users(any())).thenReturn(Lookup.of(Map.of(
                44L, new UserSummary(44L, "an@lockr.vn", null, "Nguyễn An", "ACTIVE"))));

        List<Map<String, Object>> result = service.storeRatings(3L);

        assertEquals(2, result.size());
        assertEquals("Nguyễn An", result.get(0).get("userName"));
        assertEquals(5, result.get(0).get("rating"));
        assertNull(result.get(1).get("userName"));
    }

    @Test
    void lockerLookupFailureStillReturnsRatingsRecordedWithStoreId() {
        when(references.allLockers()).thenReturn(new Lookup<>(Map.of(), false));
        when(ratingRepository.findForStore(eq(3L), any())).thenReturn(List.of());

        assertTrue(service.storeRatings(3L).isEmpty());
        verify(ratingRepository).findForStore(3L, Set.of(-1L));
    }

    private static LockerInfo locker(Long id, Long storeId) {
        return new LockerInfo(id, storeId, "CAB-" + id, "Tủ " + id, "ACTIVE", null, null, null, false, null, 8, 4);
    }

    private static OrderRating rating(Long id, Long userId, int stars) {
        OrderRating rating = new OrderRating();
        rating.setId(id);
        rating.setOrderId(100L + id);
        rating.setUserId(userId);
        rating.setRating(stars);
        return rating;
    }
}
