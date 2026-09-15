package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.PaymentReportClient;
import com.huynqb.laundrylocker.order.client.StoreLookupClient;
import com.huynqb.laundrylocker.order.client.UserLookupClient;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
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

/**
 * Ghép dữ liệu từ service khác cho trang admin theo lô: mỗi loại tra cứu là một
 * lời gọi Feign cho cả trang (chia nhỏ theo {@link #CHUNK} id), không gọi từng dòng.
 * Lỗi tra cứu không làm hỏng danh sách — trả {@link Lookup#unavailable()} và các
 * field tên để null.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminReferenceResolver {

    static final int CHUNK = 200;

    private final UserLookupClient userLookupClient;
    private final LockerLookupClient lockerLookupClient;
    private final StoreLookupClient storeLookupClient;
    private final PaymentReportClient paymentReportClient;

    public Lookup<UserSummary> users(Collection<Long> ids) {
        return batch("users", ids, chunk -> userLookupClient.getUsers(chunk), UserSummary::id);
    }

    public Lookup<LockerInfo> lockers(Collection<Long> ids) {
        return batch("lockers", ids, chunk -> lockerLookupClient.getLockers(chunk), LockerInfo::id);
    }

    public Lookup<LockerInfo> allLockers() {
        return all("lockers", () -> lockerLookupClient.getLockers(null), LockerInfo::id);
    }

    public Lookup<BoxInfo> boxes(Collection<Long> ids) {
        return batch("boxes", ids, chunk -> lockerLookupClient.getBoxes(chunk), BoxInfo::id);
    }

    public Lookup<StoreInfo> stores(Collection<Long> ids) {
        return batch("stores", ids, chunk -> storeLookupClient.getStores(chunk), StoreInfo::id);
    }

    public Lookup<StoreInfo> allStores() {
        return all("stores", () -> storeLookupClient.getStores(null), StoreInfo::id);
    }

    public Lookup<OrderPaymentSummary> paymentSummaries(Collection<Long> orderIds) {
        return batch("payment summaries", orderIds,
                chunk -> paymentReportClient.getOrderSummaries(chunk), OrderPaymentSummary::orderId);
    }

    /// Không có fallback: báo cáo doanh thu thiếu dữ liệu tiền thì phải báo lỗi, không trả số 0.
    public CollectedPayments collected(String from, String to, Long userId) {
        ApiResponse<CollectedPayments> response = paymentReportClient.getCollected(from, to, userId);
        CollectedPayments data = response == null ? null : response.data();
        return data == null ? new CollectedPayments(List.of(), List.of(), List.of()) : data;
    }

    /// Tìm khách theo đúng số điện thoại (ô tìm kiếm q); null nếu không có/lỗi.
    public Long userIdByPhone(String phone) {
        try {
            ApiResponse<UserSummary> response = userLookupClient.getUserByPhone(phone);
            return response == null || response.data() == null ? null : response.data().id();
        } catch (Exception ex) {
            log.debug("User lookup by phone failed: {}", ex.getMessage());
            return null;
        }
    }

    private <T> Lookup<T> batch(
            String what, Collection<Long> ids, Function<List<Long>, ApiResponse<List<T>>> call, Function<T, Long> key) {
        List<Long> distinct = ids == null
                ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Lookup.of(Map.of());
        }
        Map<Long, T> values = new LinkedHashMap<>();
        try {
            for (int i = 0; i < distinct.size(); i += CHUNK) {
                List<Long> chunk = new ArrayList<>(distinct.subList(i, Math.min(distinct.size(), i + CHUNK)));
                ApiResponse<List<T>> response = call.apply(chunk);
                if (response != null && response.data() != null) {
                    response.data().stream().filter(Objects::nonNull).forEach(v -> values.put(key.apply(v), v));
                }
            }
            return Lookup.of(values);
        } catch (Exception ex) {
            log.warn("Admin lookup of {} failed, rendering without it: {}", what, ex.getMessage());
            return Lookup.unavailable();
        }
    }

    private <T> Lookup<T> all(String what, java.util.function.Supplier<ApiResponse<List<T>>> call, Function<T, Long> key) {
        try {
            ApiResponse<List<T>> response = call.get();
            Map<Long, T> values = new LinkedHashMap<>();
            if (response != null && response.data() != null) {
                response.data().stream().filter(Objects::nonNull).forEach(v -> values.put(key.apply(v), v));
            }
            return Lookup.of(values);
        } catch (Exception ex) {
            log.warn("Admin lookup of all {} failed, rendering without it: {}", what, ex.getMessage());
            return Lookup.unavailable();
        }
    }

    /// Kết quả tra cứu theo lô; `available=false` khi service đích lỗi.
    public record Lookup<T>(Map<Long, T> values, boolean available) {

        static <T> Lookup<T> of(Map<Long, T> values) {
            return new Lookup<>(values, true);
        }

        static <T> Lookup<T> unavailable() {
            return new Lookup<>(Map.of(), false);
        }

        public T get(Long id) {
            return id == null ? null : values.get(id);
        }
    }
}
