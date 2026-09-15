package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.locker.dto.BoxLookupResponse;
import com.huynqb.laundrylocker.locker.dto.LockerResponse;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Tra cứu tủ/ô theo lô cho báo cáo admin bên order-service. Đếm ô bằng một
/// truy vấn cho cả lô thay vì hai truy vấn count mỗi tủ.
@Service
@RequiredArgsConstructor
public class LockerLookupService {

    private final LockerUnitRepository lockerRepository;
    private final LockerBoxRepository boxRepository;

    /// `ids` rỗng/null ⇒ toàn bộ tủ (báo cáo theo tủ cần cả tủ chưa có doanh thu).
    @Transactional(readOnly = true)
    public List<LockerResponse> lockers(Collection<Long> ids) {
        List<Long> distinct = distinct(ids);
        List<LockerUnit> lockers = distinct.isEmpty() ? lockerRepository.findAll() : lockerRepository.findAllById(distinct);
        if (lockers.isEmpty()) {
            return List.of();
        }
        List<Long> lockerIds = lockers.stream().map(LockerUnit::getId).toList();
        Map<Long, List<LockerBox>> boxesByLocker =
                boxRepository.findByLockerIdIn(lockerIds).stream().collect(Collectors.groupingBy(LockerBox::getLockerId));
        return lockers.stream()
                .map(locker -> {
                    List<LockerBox> boxes = boxesByLocker.getOrDefault(locker.getId(), List.of());
                    int available = (int) boxes.stream()
                            .filter(box -> Boolean.TRUE.equals(box.getActive()) && "AVAILABLE".equals(box.getStatus()))
                            .count();
                    return new LockerResponse(
                            locker.getId(), locker.getStoreId(), locker.getCode(), locker.getName(), locker.getStatus(),
                            locker.getAddress(), locker.getLatitude(), locker.getLongitude(),
                            locker.getLandingPad(), locker.getLandingMarkerId(), boxes.size(), available);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BoxLookupResponse> boxes(Collection<Long> ids) {
        List<Long> distinct = distinct(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        return boxRepository.findAllById(distinct).stream()
                .map(box -> new BoxLookupResponse(
                        box.getId(), box.getLockerId(), box.getBoxNumber(), box.getSize(),
                        box.getCellType(), box.getStatus(), box.getActive()))
                .toList();
    }

    private List<Long> distinct(Collection<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
