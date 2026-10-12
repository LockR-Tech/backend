package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.OrderRating;
import com.huynqb.laundrylocker.order.repository.OrderRatingRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Đánh giá hiển thị ở trang cửa hàng (store-service gọi `/internal/orders/stores/{id}/ratings`).
/// Đơn gửi hàng/thuê tủ/drone không ghi storeId nên cửa hàng được suy từ tủ của đơn
/// (order.lockerId → locker.storeId).
@Service
@RequiredArgsConstructor
public class StoreRatingQueryService {

    /// Không id tủ nào trùng — tránh `in ()` khi cửa hàng chưa có tủ hoặc tra cứu tủ lỗi.
    private static final Set<Long> NO_LOCKER = Set.of(-1L);

    private final OrderRatingRepository ratingRepository;
    private final AdminReferenceResolver references;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> storeRatings(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        Set<Long> lockerIds = references.allLockers().values().values().stream()
                .filter(locker -> storeId.equals(locker.storeId()))
                .map(LockerInfo::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<OrderRating> ratings =
                ratingRepository.findForStore(storeId, lockerIds.isEmpty() ? NO_LOCKER : lockerIds);
        if (ratings.isEmpty()) {
            return List.of();
        }
        Lookup<UserSummary> users = references.users(ratings.stream().map(OrderRating::getUserId).toList());
        return ratings.stream().map(rating -> toView(rating, users.get(rating.getUserId()))).toList();
    }

    private static Map<String, Object> toView(OrderRating rating, UserSummary user) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", rating.getId());
        result.put("orderId", rating.getOrderId());
        result.put("userId", rating.getUserId());
        result.put("userName", user == null || !StringUtils.hasText(user.fullName()) ? null : user.fullName());
        result.put("rating", rating.getRating());
        result.put("comment", rating.getComment());
        result.put("createdAt", rating.getCreatedAt());
        return result;
    }
}
