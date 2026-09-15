package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.payment.client.OrderLookupClient;
import com.huynqb.laundrylocker.payment.client.UserLookupClient;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// Ghép tên khách/mã đơn cho trang admin theo lô (một lời gọi Feign mỗi loại cho cả
/// trang). Service nguồn lỗi ⇒ map rỗng, field ghép để null, danh sách vẫn trả.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReferenceResolver {

    static final int CHUNK = 200;

    private final UserLookupClient userLookupClient;
    private final OrderLookupClient orderLookupClient;

    public Map<Long, UserSummary> users(Collection<Long> ids) {
        return batch("users", ids, userLookupClient::getUsers, UserSummary::id);
    }

    /// Bỏ qua orderId ≤ 0 (nạp ví).
    public Map<Long, OrderBrief> orders(Collection<Long> ids) {
        List<Long> real = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).toList();
        return batch("orders", real, orderLookupClient::getOrders, OrderBrief::id);
    }

    private <T> Map<Long, T> batch(
            String what, Collection<Long> ids, Function<Collection<Long>, ApiResponse<List<T>>> call, Function<T, Long> key) {
        List<Long> distinct = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, T> values = new LinkedHashMap<>();
        try {
            for (int i = 0; i < distinct.size(); i += CHUNK) {
                ApiResponse<List<T>> response =
                        call.apply(new ArrayList<>(distinct.subList(i, Math.min(distinct.size(), i + CHUNK))));
                if (response != null && response.data() != null) {
                    response.data().stream().filter(Objects::nonNull).forEach(v -> values.put(key.apply(v), v));
                }
            }
            return values;
        } catch (Exception ex) {
            log.warn("Admin lookup of {} failed, rendering without it: {}", what, ex.getMessage());
            return Map.of();
        }
    }
}
