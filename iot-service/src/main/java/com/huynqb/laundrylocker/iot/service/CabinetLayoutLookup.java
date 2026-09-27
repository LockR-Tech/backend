package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/// Đổi ô (`boxId`) ↔ vị trí trên bộ điều khiển tủ (`slotIndex = boxNumber − 1`) theo
/// sơ đồ của locker-service (ADR-0008, docs/01-overview/mqtt-contract.md § 1).
///
/// Không cache: mỗi lần mở ô tra lại một lần (vài ms) để admin đánh số lại ô là có hiệu
/// lực ngay. Tra lỗi thì trả rỗng — lệnh vẫn đi với `boxId`, Pi tự tra trong sơ đồ setup.
@Slf4j
@Component
@RequiredArgsConstructor
public class CabinetLayoutLookup {

    private final LockerClient lockerClient;

    public Optional<LockerLayoutView> layout(Long lockerId) {
        if (lockerId == null) {
            return Optional.empty();
        }
        try {
            ApiResponse<LockerLayoutView> response = lockerClient.layout(lockerId);
            return Optional.ofNullable(response == null ? null : response.data());
        } catch (RuntimeException ex) {
            log.warn("Cannot load layout of locker {}: {}", lockerId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Integer> slotIndexOf(Long lockerId, Long boxId) {
        if (boxId == null) {
            return Optional.empty();
        }
        return layout(lockerId).flatMap(layout -> layout.cells().stream()
                .filter(cell -> boxId.equals(cell.id()))
                .map(LockerLayoutView.Cell::slotIndex)
                .filter(Objects::nonNull)
                .findFirst());
    }

    public Optional<Long> boxIdAt(Long lockerId, Integer slotIndex) {
        if (slotIndex == null) {
            return Optional.empty();
        }
        return layout(lockerId).flatMap(layout -> layout.cells().stream()
                .filter(cell -> slotIndex.equals(cell.slotIndex()))
                .map(LockerLayoutView.Cell::id)
                .findFirst());
    }
}
