package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.PromotionView;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.Promotion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Tủ áp dụng của mã giảm giá: tra tên/mã tủ theo lô (một lần cho cả danh sách), dựng {@link PromotionView}
/// và câu lý do nêu tên tủ. locker-service lỗi thì tên tủ để null — danh sách mã vẫn trả về.
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionViews {

    private final LockerLookupClient lockerLookupClient;

    public List<PromotionView> views(List<Promotion> promotions, boolean admin) {
        Map<Long, LockerInfo> lockers = lockers(promotions.stream().map(Promotion::getLockerId).toList());
        return promotions.stream().map(p -> PromotionView.of(p, lockers.get(p.getLockerId()), admin)).toList();
    }

    public PromotionView view(Promotion promotion, boolean admin) {
        return views(List.of(promotion), admin).getFirst();
    }

    /// id tủ → tủ; id null bỏ qua; lỗi tra cứu ⇒ map rỗng.
    public Map<Long, LockerInfo> lockers(Collection<Long> lockerIds) {
        List<Long> ids = lockerIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, LockerInfo> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        try {
            ApiResponse<List<LockerInfo>> response = lockerLookupClient.getLockers(ids);
            List<LockerInfo> lockers = response == null ? null : response.data();
            if (lockers != null) {
                lockers.stream().filter(l -> l != null && l.id() != null).forEach(l -> result.put(l.id(), l));
            }
        } catch (RuntimeException ex) {
            log.debug("Locker lookup for promotions failed: {}", ex.getMessage());
        }
        return result;
    }

    /// Tên tủ để ghi vào lý do: tên → mã → `tủ #id`.
    public String lockerLabel(Long lockerId) {
        LockerInfo locker = lockers(List.of(lockerId)).get(lockerId);
        if (locker != null && StringUtils.hasText(locker.name())) {
            return locker.name().trim();
        }
        if (locker != null && StringUtils.hasText(locker.code())) {
            return locker.code().trim();
        }
        return "tủ #" + lockerId;
    }

    /// Admin gắn mã vào một tủ: tủ phải tồn tại. Không tra được (locker-service lỗi) thì không lưu.
    public void assertLockerExists(Long lockerId) {
        if (lockerId == null) {
            return;
        }
        List<LockerInfo> lockers;
        try {
            ApiResponse<List<LockerInfo>> response = lockerLookupClient.getLockers(List.of(lockerId));
            lockers = response == null ? null : response.data();
        } catch (RuntimeException ex) {
            throw new BusinessException("LOCKER_LOOKUP_FAILED",
                    "Chưa kiểm tra được tủ #" + lockerId + ", vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
        boolean found = lockers != null && lockers.stream().anyMatch(l -> l != null && lockerId.equals(l.id()));
        if (!found) {
            throw new BusinessException("PROMOTION_LOCKER_NOT_FOUND", "Không tìm thấy tủ #" + lockerId);
        }
    }
}
