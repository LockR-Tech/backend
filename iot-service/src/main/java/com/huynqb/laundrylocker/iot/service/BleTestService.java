package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.dto.BleTestGrantResponse;
import com.huynqb.laundrylocker.iot.dto.BleTestLockerResponse;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.model.BleTestGrant;
import com.huynqb.laundrylocker.iot.repository.BleTestGrantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Thử mở ô qua Bluetooth (F3-G11). Admin cấp quyền cho từng tài khoản trên từng tủ; tài khoản được
/// cấp xin mã mở một ô bất kỳ của tủ đó, mã sống {@link #TOKEN_TTL_SECONDS} giây và chỉ dùng được
/// một lần. App ghi mã cho Pi qua BLE GATT — Pi kiểm chữ ký rồi kích relay, không cần mạng lúc mở.
@Slf4j
@Service
@RequiredArgsConstructor
public class BleTestService {

    public static final long TOKEN_TTL_SECONDS = 120;
    /// GATT của Pi — phải khớp iot `services/ble_gatt_service.py`.
    public static final String SERVICE_UUID = "06776521-9674-432d-b886-5e951244ffa3";
    public static final String OPEN_CHAR_UUID = "54af4ad7-dcde-47dc-ad44-fa2c7438dd36";
    public static final String RESULT_CHAR_UUID = "8a6e33a5-1b26-4e80-8faa-026bab93ad4e";
    private static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final BleTestGrantRepository grants;
    private final BleTokenSigner signer;
    private final LockerClient lockerClient;
    private final CabinetLayoutLookup layoutLookup;
    private final CabinetControlLogService controlLogs;

    // ─── admin ───

    public List<BleTestGrantResponse> grantsOf(Long lockerId) {
        requireLocker(lockerId);
        return grants.findByLockerIdOrderByCreatedAtDesc(lockerId).stream().map(BleTestService::toResponse).toList();
    }

    @Transactional
    public BleTestGrantResponse grant(Long lockerId, Long userId, Long adminId) {
        requireLocker(lockerId);
        if (userId == null || userId <= 0) {
            throw new BusinessException("USER_REQUIRED", "Thiếu tài khoản cần cấp quyền");
        }
        BleTestGrant grant = grants.findByLockerIdAndUserId(lockerId, userId).orElseGet(() -> {
            BleTestGrant created = new BleTestGrant();
            created.setLockerId(lockerId);
            created.setUserId(userId);
            created.setGrantedBy(adminId);
            return grants.save(created);
        });
        return toResponse(grant);
    }

    @Transactional
    public void revoke(Long lockerId, Long userId) {
        grants.findByLockerIdAndUserId(lockerId, userId).ifPresent(grants::delete);
    }

    // ─── tài khoản được cấp ───

    public List<BleTestLockerResponse> lockersOf(Long userId) {
        requireUser(userId);
        List<Long> ids = grants.findByUserIdOrderByLockerIdAsc(userId).stream().map(BleTestGrant::getLockerId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, LockerView> byId = Map.of();
        try {
            ApiResponse<List<LockerView>> response = lockerClient.lockers(ids);
            if (response != null && response.data() != null) {
                byId = response.data().stream().filter(l -> l != null && l.id() != null)
                        .collect(Collectors.toMap(LockerView::id, Function.identity(), (a, b) -> a));
            }
        } catch (RuntimeException ex) {
            log.warn("Cannot look up BLE test lockers {}: {}", ids, ex.getMessage());
        }
        Map<Long, LockerView> lockers = byId;
        return ids.stream().map(id -> {
            LockerView l = lockers.get(id);
            return new BleTestLockerResponse(id, l == null ? null : l.code(), l == null ? null : l.name());
        }).toList();
    }

    /// Mã mở ô `boxNumber` của tủ. Không chặn ô đang có hàng — đây là chế độ thử do admin cấp;
    /// app hỏi lại người dùng khi `boxStatus` là OCCUPIED. Mỗi lần cấp đều ghi nhật ký điều khiển tủ.
    public BleOpenTokenResponse issueToken(Long lockerId, Integer boxNumber, Long userId) {
        requireUser(userId);
        if (lockerId == null || !grants.existsByLockerIdAndUserId(lockerId, userId)) {
            throw new BusinessException("BLE_TEST_NOT_GRANTED",
                    "Tài khoản này chưa được admin cho thử mở ô qua Bluetooth ở tủ này", HttpStatus.FORBIDDEN);
        }
        if (boxNumber == null || boxNumber <= 0) {
            throw new BusinessException("BOX_REQUIRED", "Thiếu số ô cần mở");
        }
        LockerLayoutView layout = layoutLookup.layout(lockerId).orElseThrow(() -> new BusinessException(
                "LOCKER_LOOKUP_FAILED", "Không tra được sơ đồ tủ, chưa cấp mã được", HttpStatus.SERVICE_UNAVAILABLE));
        LockerLayoutView.Cell cell = layout.cells().stream().filter(c -> boxNumber.equals(c.boxNumber())).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "BOX_NOT_IN_LOCKER", "Tủ không có ô số " + boxNumber, HttpStatus.NOT_FOUND));
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);
        BleTokenSigner.SignedToken token = signer.sign(lockerId, boxNumber, userId, expiresAt);
        controlLogs.recordWeb(lockerId, userId, "BLE_TOKEN", null, cell.id(), null, "ISSUED",
                "Cấp mã mở ô " + boxNumber + " qua Bluetooth, hết hạn " + CLOCK_TIME.format(expiresAt));
        String code = layout.code();
        return new BleOpenTokenResponse(token.base64Url(), token.nonceHex(), expiresAt, lockerId, code, boxNumber,
                cell.id(), cell.status(), deviceName(code, lockerId), SERVICE_UUID, OPEN_CHAR_UUID, RESULT_CHAR_UUID);
    }

    public Map<String, Object> publicKey() {
        return Map.of("algorithm", BleTokenSigner.ALGORITHM, "publicKey", signer.publicKeyBase64(),
                "tokenVersion", (int) BleTokenSigner.VERSION);
    }

    /// Tên Pi phát BLE (iot `BleAdvertiserService.resolve_device_name`).
    static String deviceName(String code, Long lockerId) {
        return "LOCKR_" + (code == null || code.isBlank() ? String.valueOf(lockerId) : code);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "Cần đăng nhập", HttpStatus.UNAUTHORIZED);
        }
    }

    private void requireLocker(Long lockerId) {
        if (lockerId == null) {
            throw new BusinessException("LOCKER_REQUIRED", "Thiếu mã tủ");
        }
        List<LockerView> found;
        try {
            ApiResponse<List<LockerView>> response = lockerClient.lockers(List.of(lockerId));
            found = response == null || response.data() == null ? List.of() : response.data();
        } catch (RuntimeException ex) {
            throw new BusinessException("LOCKER_LOOKUP_FAILED", "Không tra được thông tin tủ: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (found.stream().noneMatch(l -> l != null && lockerId.equals(l.id()))) {
            throw new BusinessException("LOCKER_NOT_FOUND", "Không tìm thấy tủ " + lockerId, HttpStatus.NOT_FOUND);
        }
    }

    private static BleTestGrantResponse toResponse(BleTestGrant g) {
        return new BleTestGrantResponse(g.getLockerId(), g.getUserId(), g.getGrantedBy(), g.getCreatedAt());
    }
}
