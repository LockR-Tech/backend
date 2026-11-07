package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.dto.OrderBleAccessView;
import com.huynqb.laundrylocker.iot.model.BleOrderToken;
import com.huynqb.laundrylocker.iot.repository.BleOrderTokenRepository;
import com.huynqb.laundrylocker.iot.settings.IotRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

/// Mở ô theo ĐƠN qua Bluetooth (F3-G11). Dùng nguyên mã 91 byte v1 của chế độ thử (Pi không đổi cách kiểm):
/// 1. App xin mã cho một đơn — order-service quyết định ai được mở, lúc nào, để làm gì (DROP / RENTAL_ACCESS /
///    PICKUP), cùng luật với PIN/QR; ở đây ký mã và lưu `nonce → đơn` (`ble_order_tokens`).
/// 2. Pi mở ô xong báo `BOX_OPEN_BLE` kèm `nonce` trên `cabinet/{lockerId}/event/control`. Chỉ lúc đó backend
///    mới ghi nhật ký mở ô (credential `BLE`) và chuyển trạng thái đơn như mở bằng PIN/QR — app không tự báo
///    "đã mở". Pi gửi lại sự kiện (outbox) thì `processed_at` chặn xử lý lần hai.
///
/// Không tính vào khoá ô vì nhập sai mã: mã gắn với tài khoản đã đăng nhập, không phải mã dò được.
@Slf4j
@Service
public class BleOrderService {

    static final String ACTION_BOX_OPEN_BLE = "BOX_OPEN_BLE";
    static final String CREDENTIAL_BLE = "BLE";
    static final String NOT_OPENABLE = "BLE_ORDER_NOT_OPENABLE";
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{16}");
    /// Mã chặn của OrderAccessPolicy / getByAccess — dùng đúng câu tiếng Việt của kiosk.
    private static final Set<String> BLOCK_CODES =
            Set.of("ORDER_UNPAID", "RENTAL_EXPIRED", "OVERTIME_FEE_UNPAID", "RENTAL_UNPAID", "BOX_INCIDENT_OPEN");
    private static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final OrderClient orderClient;
    private final BleTokenSigner signer;
    private final CabinetLayoutLookup layoutLookup;
    private final CabinetControlLogService controlLogs;
    private final BleOrderTokenRepository tokens;
    private final IotService iotService;
    private final IotRules rules;
    /// Gọi locker-/order-service sau khi ô mở — chạy ngoài luồng MQTT để không làm chậm kết quả lệnh mở khác.
    private final Executor followUps;

    @Autowired
    public BleOrderService(OrderClient orderClient, BleTokenSigner signer, CabinetLayoutLookup layoutLookup,
                           CabinetControlLogService controlLogs, BleOrderTokenRepository tokens,
                           IotService iotService, IotRules rules) {
        this(orderClient, signer, layoutLookup, controlLogs, tokens, iotService, rules, ForkJoinPool.commonPool());
    }

    BleOrderService(OrderClient orderClient, BleTokenSigner signer, CabinetLayoutLookup layoutLookup,
                    CabinetControlLogService controlLogs, BleOrderTokenRepository tokens,
                    IotService iotService, IotRules rules, Executor followUps) {
        this.orderClient = orderClient;
        this.signer = signer;
        this.layoutLookup = layoutLookup;
        this.controlLogs = controlLogs;
        this.tokens = tokens;
        this.iotService = iotService;
        this.rules = rules;
        this.followUps = followUps;
    }

    // ─── 1. cấp mã ───

    public BleOpenTokenResponse issueToken(Long orderId, Long userId) {
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "Cần đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        if (orderId == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND);
        }
        OrderBleAccessView access = lookupAccess(orderId, userId);
        if (access.denyCode() != null) {
            throw new BusinessException(access.denyCode(), denyMessage(access), denyStatus(access.denyStatus()));
        }
        if (access.action() == null || access.lockerId() == null || access.boxId() == null) {
            throw new BusinessException(NOT_OPENABLE, "Đơn này không gắn với ô tủ nào.", HttpStatus.CONFLICT);
        }
        Long lockerId = access.lockerId();
        LockerLayoutView layout = layoutLookup.layout(lockerId).orElseThrow(() -> new BusinessException(
                "LOCKER_LOOKUP_FAILED", "Không tra được sơ đồ tủ, chưa cấp mã được", HttpStatus.SERVICE_UNAVAILABLE));
        LockerLayoutView.Cell cell = layout.cells().stream()
                .filter(c -> access.boxId().equals(c.id()) && c.boxNumber() != null && c.boxNumber() > 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "BOX_NOT_IN_LOCKER", "Ô của đơn không có trong sơ đồ tủ", HttpStatus.CONFLICT));
        int boxNumber = cell.boxNumber();
        Instant expiresAt = Instant.now().plusSeconds(rules.bleTokenTtlSeconds());
        BleTokenSigner.SignedToken token = signer.sign(lockerId, boxNumber, userId, expiresAt);

        BleOrderToken row = new BleOrderToken();
        row.setNonce(token.nonceHex());
        row.setOrderId(orderId);
        row.setLockerId(lockerId);
        row.setBoxId(cell.id());
        row.setBoxNumber(boxNumber);
        row.setUserId(userId);
        row.setAction(access.action());
        row.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        tokens.save(row);

        controlLogs.recordWeb(lockerId, userId, "BLE_TOKEN", null, cell.id(), null, "ISSUED",
                "Cấp mã mở ô " + boxNumber + " qua Bluetooth cho đơn #" + orderId + " (" + access.action()
                        + "), hết hạn " + CLOCK_TIME.format(expiresAt));
        String code = layout.code();
        return new BleOpenTokenResponse(token.base64Url(), token.nonceHex(), expiresAt, lockerId, code, boxNumber,
                cell.id(), cell.status(), BleTestService.deviceName(code, lockerId), BleTestService.SERVICE_UUID,
                BleTestService.OPEN_CHAR_UUID, BleTestService.RESULT_CHAR_UUID, orderId, access.action());
    }

    private OrderBleAccessView lookupAccess(Long orderId, Long userId) {
        try {
            ApiResponse<OrderBleAccessView> response = orderClient.bleAccess(orderId, userId);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception ex) {
            if (ex instanceof feign.FeignException feignError && feignError.status() == 404) {
                throw new BusinessException("ORDER_NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND);
            }
            log.warn("Order BLE access lookup failed for order {}: {}", orderId, ex.getMessage());
        }
        throw new BusinessException(IotService.ORDER_LOOKUP_UNAVAILABLE, IotService.ORDER_LOOKUP_UNAVAILABLE_MESSAGE,
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String denyMessage(OrderBleAccessView access) {
        if (BLOCK_CODES.contains(access.denyCode())) {
            return IotService.blockMessage(access.denyCode());
        }
        if (access.denyMessage() != null && !access.denyMessage().isBlank()) {
            return access.denyMessage();
        }
        return IotService.blockMessage(access.denyCode());
    }

    private static HttpStatus denyStatus(Integer status) {
        HttpStatus resolved = status == null ? null : HttpStatus.resolve(status);
        return resolved != null ? resolved : HttpStatus.BAD_REQUEST;
    }

    // ─── 2. Pi báo đã mở ───

    /// `cabinet/{lockerId}/event/control` — trả true khi sự kiện khớp một mã theo đơn chưa xử lý và vừa được
    /// xử lý. Mã thử của admin (nonce không có trong bảng), sự kiện lặp, sai tủ ⇒ false, chỉ còn control log.
    public boolean onCabinetEvent(Long lockerId, JsonNode data) {
        if (lockerId == null || data == null || !ACTION_BOX_OPEN_BLE.equalsIgnoreCase(text(data, "action"))) {
            return false;
        }
        String nonce = text(data, "nonce");
        nonce = nonce == null ? null : nonce.trim().toLowerCase(Locale.ROOT);
        if (nonce == null || !NONCE.matcher(nonce).matches()) {
            return false;
        }
        Optional<BleOrderToken> found = tokens.findByNonceAndLockerId(nonce, lockerId);
        if (found.isEmpty() || found.get().getProcessedAt() != null) {
            return false;
        }
        BleOrderToken token = found.get();
        String status = upper(text(data, "status"));
        if (status == null) {
            status = upper(text(data, "result"));
        }
        boolean ok = "OK".equals(status) || "SUCCESS".equals(status);
        String errorCode = upper(text(data, "errorCode"));
        String result = ok ? BleOrderToken.RESULT_SUCCESS
                : "JAMMED".equals(errorCode) ? BleOrderToken.RESULT_JAMMED : BleOrderToken.RESULT_FAILED;
        if (tokens.claim(nonce, lockerId, result, LocalDateTime.now()) == 0) {
            return false;   // cùng sự kiện tới hai lần gần như đồng thời
        }
        if (ok) {
            iotService.logExternalAccess(token.getBoxId(), lockerId, token.getOrderId(), token.getUserId(),
                    CREDENTIAL_BLE, "SUCCESS",
                    "Mở ô " + token.getBoxNumber() + " qua Bluetooth bằng mã của đơn (" + token.getAction() + ")");
            followUps.execute(() -> afterOpen(token));
        } else {
            String code = errorCode != null ? errorCode : "OPEN_FAILED";
            String message = text(data, "message");
            String detail = message != null ? message : "Phần cứng không mở được chốt ô";
            iotService.logExternalAccess(token.getBoxId(), lockerId, token.getOrderId(), token.getUserId(),
                    CREDENTIAL_BLE, result, DeviceManagementService.truncate(
                            "Lỗi phần cứng Bluetooth [" + code + "]: " + detail, IotService.ACCESS_LOG_MESSAGE_MAX));
            iotService.reportBoxFault(token.getBoxId(), code, detail);
        }
        return true;
    }

    /// Cùng việc `afterOpen` của `/api/iot/unlock`, theo việc đã xin mã: bỏ hàng ⇒ drop-opened; lấy hàng ⇒
    /// hoàn tất (theo trạng thái hiện tại của đơn); thuê ô ⇒ không đổi đơn.
    private void afterOpen(BleOrderToken token) {
        try {
            switch (token.getAction()) {
                case "DROP" -> iotService.afterBleOrderOpened(
                        token.getBoxId(), token.getOrderId(), "INITIALIZED", null, null);
                case "PICKUP" -> {
                    OrderSummary order = currentOrder(token.getOrderId());
                    boolean stillStored = order != null
                            && ("STORING".equalsIgnoreCase(order.status()) || "RETURNED".equalsIgnoreCase(order.status()));
                    iotService.afterBleOrderOpened(token.getBoxId(), token.getOrderId(),
                            stillStored ? order.status() : null, order == null ? null : order.type(),
                            stillStored ? order.userId() : null);
                }
                default -> iotService.afterBleOrderOpened(
                        token.getBoxId(), token.getOrderId(), "STORING", "RENTAL", null);
            }
        } catch (Exception ex) {
            log.warn("Follow-up after Bluetooth open of order {} failed: {}", token.getOrderId(), ex.getMessage());
        }
    }

    private OrderSummary currentOrder(Long orderId) {
        try {
            ApiResponse<OrderSummary> response = orderClient.summary(orderId);
            return response == null ? null : response.data();
        } catch (Exception ex) {
            log.warn("Could not load order {} after Bluetooth pickup: {}", orderId, ex.getMessage());
            return null;
        }
    }

    private static String text(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText() : null;
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
