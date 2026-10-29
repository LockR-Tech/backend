package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.payment.client.NotificationClient;
import com.huynqb.laundrylocker.payment.dto.*;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.UserBankAccount;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.UserBankAccountRepository;
import com.huynqb.laundrylocker.payment.settings.PaymentRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final RefundRepository refundRepository;
    private final UserBankAccountRepository userBankAccountRepository;
    private final RabbitTemplate rabbitTemplate;
    private final WalletService walletService;
    private final OrderClient orderClient;
    private final NotificationClient notificationClient;
    private final SepayService sepayService;
    /// Hạn mức nạp ví, phương thức đang bật, tự hoàn tất tiền mặt… admin cấu hình (ADR-0005).
    private final PaymentRules rules;
    /// Báo kết quả chi bồi thường sự cố về order-service; null trong unit test không cần nó.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.huynqb.laundrylocker.payment.client.DroneIncidentClient droneIncidentClient;

    /// Tiền tố mã phiếu bồi thường sự cố rơi kiện: `RF-CMP-<mã sự cố>-<millis>`.
    static final String COMPENSATION_PREFIX = "RF-CMP-";

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(request.orderId());
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        payment.setMethod(StringUtils.hasText(request.method()) ? request.method().toUpperCase() : "CASH");
        assertMethodNotRetired(payment.getMethod());
        assertMethodEnabled(payment.getMethod());
        payment.setReferenceId(StringUtils.hasText(request.referenceId()) ? request.referenceId() : generateReference(request.orderId()));
        payment.setDescription(request.description());
        payment.setContent("Payment for order " + request.orderId());
        if ("SEPAY".equals(payment.getMethod())) {
            sepayService.createPayment(payment, null);
        } else if ("CASH".equals(payment.getMethod()) && rules.cashAutoComplete()) {
            payment.setStatus("COMPLETED");
        }
        PaymentRecord saved = repository.save(payment);
        if ("COMPLETED".equals(saved.getStatus())) {
            publish(DomainEventNames.PAYMENT_COMPLETED, saved);
        }
        return toResponse(saved);
    }

    /**
     * Pay for an existing order with the chosen method.
     * WALLET settles immediately (status COMPLETED + event); CASH too unless admin turned off
     * {@code app.payment.cash-auto-complete} (then it stays PENDING for staff confirmation);
     * SEPAY returns a pay URL and settles later via the SePay webhook. The amount is taken from
     * the order (authoritative), not the client. Retired methods (VNPAY, MOMO) are rejected with
     * PAYMENT_METHOD_UNSUPPORTED; methods not in {@code app.payment.enabled-methods} are rejected
     * with PAYMENT_METHOD_DISABLED.
     */
    /// Mô tả để khách phân biệt nhiều lần trả trên cùng một đơn: thuê rồi gia hạn, hoặc
    /// bị tính thêm phí quá hạn. Trước đây mọi lần đều ghi "Thanh toán đơn #N" nên chi
    /// tiết đơn hiện hai khối tiền giống hệt nhau, khách không biết khoản nào là gì.
    ///
    /// Client gửi lý do cụ thể thì dùng; không gửi thì suy từ việc đơn đã có lần trả nào
    /// chưa — bản app cũ vẫn phân biệt được mà không phải sửa gì. Cắt ngắn vì chuỗi này
    /// hiện thẳng lên màn hình khách.
    static String paymentPurpose(String requested, Long orderId, BigDecimal alreadyPaid) {
        if (StringUtils.hasText(requested)) {
            String clean = requested.strip();
            return clean.length() <= 120 ? clean : clean.substring(0, 120);
        }
        return alreadyPaid.signum() > 0
                ? "Thanh toán bổ sung đơn #" + orderId
                : "Thanh toán đơn #" + orderId;
    }

    @Transactional
    public PaymentResponse checkout(Long userId, CheckoutRequest request) {
        String method = request.method() == null ? "" : request.method().toUpperCase();
        assertMethodNotRetired(method);
        assertMethodEnabled(method);
        OrderSummary order;
        try {
            order = orderClient.getOrder(request.orderId()).data();
        } catch (Exception ex) {
            throw new BusinessException("ORDER_LOOKUP_FAILED", "Không lấy được thông tin đơn: " + ex.getMessage());
        }
        if (order == null) {
            throw new NotFoundException("Order", request.orderId());
        }
        // Ô của đơn đang chờ KTV xử lý sự cố: khách chưa lấy được đồ nên chưa thu (phí quá giờ đang dừng).
        if (Boolean.TRUE.equals(order.boxIncidentOpen())) {
            throw new BusinessException("ORDER_BOX_INCIDENT_OPEN",
                    "Ô của đơn đang chờ KTV xử lý sự cố — phí quá giờ đang tạm dừng, bạn chưa cần thanh toán",
                    HttpStatus.CONFLICT);
        }
        BigDecimal totalAmount = order.totalPrice() == null ? BigDecimal.ZERO : order.totalPrice();
        BigDecimal completedAmount = repository.findByOrderId(request.orderId()).stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(PaymentRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = totalAmount.subtract(completedAmount).max(BigDecimal.ZERO);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ORDER_ALREADY_PAID", "Đơn này đã được thanh toán");
        }

        String effectiveOrderCode = StringUtils.hasText(request.orderCode())
                ? request.orderCode()
                : (order != null ? order.orderCode() : null);
        String transferNote = StringUtils.hasText(effectiveOrderCode)
                ? "PAY-" + request.orderId() + "-" + effectiveOrderCode
                : "PAY-" + request.orderId();

        PaymentRecord payment = repository.findByOrderId(request.orderId()).stream()
                .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()) && method.equalsIgnoreCase(p.getMethod()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (payment == null) {
            payment = new PaymentRecord();
            payment.setOrderId(request.orderId());
            payment.setUserId(userId);
            payment.setMethod(method);
            payment.setReferenceId(generateReference(request.orderId()));
            payment.setContent(transferNote);
            payment.setDescription(paymentPurpose(request.description(), request.orderId(), completedAmount));
        } else {
            payment.setUserId(userId);
            payment.setContent(transferNote);
        }
        payment.setAmount(amount);

        switch (method) {
            case "WALLET" -> {
                // Mỗi lần trả bằng ví (tiền thuê, gia hạn, phí quá hạn…) là một khoản trừ riêng. Mã chống
                // trùng theo số thứ tự lần trả: bấm lặp cùng lúc ra cùng mã nên chỉ trừ một lần.
                long walletPayments = repository.findByOrderId(request.orderId()).stream()
                        .filter(p -> "WALLET".equalsIgnoreCase(p.getMethod()) && "COMPLETED".equals(p.getStatus()))
                        .count();
                var debited = walletService.debit(
                        userId,
                        amount,
                        WalletService.SOURCE_ORDER_PAYMENT,
                        WalletTransactionRefs.orderPaymentRef(request.orderId(), walletPayments + 1),
                        "Thanh toán đơn #" + request.orderId());
                if (debited == null) {
                    throw new BusinessException(
                            "PAYMENT_IN_PROGRESS",
                            "Khoản thanh toán này vừa được xử lý, vui lòng tải lại đơn",
                            HttpStatus.CONFLICT);
                }
                payment.setStatus("COMPLETED");
            }
            case "CASH" -> {
                if (rules.cashAutoComplete()) {
                    payment.setStatus("COMPLETED");
                }
            }
            case "SEPAY" -> sepayService.createPayment(
                    payment,
                    request.returnUrl(),
                    "DRONE_DELIVERY".equalsIgnoreCase(order.type()));
            default ->
                    throw new BusinessException("PAYMENT_METHOD_INVALID", "Phương thức thanh toán không hợp lệ: " + method);
        }

        PaymentRecord saved = repository.save(payment);
        if ("COMPLETED".equals(saved.getStatus())) {
            publish(DomainEventNames.PAYMENT_COMPLETED, saved);
        }
        return toResponse(saved);
    }

    /// Trạng thái admin đặt tay được cho một giao dịch.
    static final java.util.Set<String> ADMIN_SETTABLE_PAYMENT_STATUSES = java.util.Set.of("PENDING", "COMPLETED", "FAILED");
    /// Đã thu tiền/đã hoàn tiền: đổi tay sẽ lệch sổ (doanh thu, ví, hoàn tiền) nên khoá.
    static final java.util.Set<String> LOCKED_PAYMENT_STATUSES = java.util.Set.of("COMPLETED", "REFUNDED");

    /// Admin đổi trạng thái giao dịch bằng tay. Chặn (409 PAYMENT_STATUS_LOCKED):
    /// - nạp ví (`*_TOPUP`): tiền chỉ được cộng vào ví trong callback cổng thanh toán, đánh dấu
    ///   COMPLETED bằng tay không cộng ví ⇒ lệch tiền;
    /// - giao dịch đã COMPLETED/REFUNDED: hoàn tiền đi qua luồng hoàn tiền, không sửa tay.
    /// Gửi lại đúng trạng thái hiện tại thì không làm gì (không phát lại sự kiện).
    @Transactional
    public PaymentResponse updateStatus(Long id, UpdatePaymentStatusRequest request) {
        String target = request.status() == null ? "" : request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if (!ADMIN_SETTABLE_PAYMENT_STATUSES.contains(target)) {
            throw new BusinessException(
                    "PAYMENT_STATUS_INVALID", "Trạng thái giao dịch không hợp lệ: " + request.status()
                    + " (chỉ PENDING, COMPLETED, FAILED)");
        }
        PaymentRecord payment = find(id);
        String current = payment.getStatus() == null ? "" : payment.getStatus().toUpperCase(java.util.Locale.ROOT);
        if (target.equals(current)) {
            return toResponse(payment);
        }
        if (payment.getMethod() != null && payment.getMethod().toUpperCase(java.util.Locale.ROOT).endsWith("_TOPUP")) {
            throw new BusinessException(
                    "PAYMENT_STATUS_LOCKED",
                    "Không thể đổi tay trạng thái giao dịch nạp ví — ví chỉ được cộng khi cổng thanh toán xác nhận",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        if (LOCKED_PAYMENT_STATUSES.contains(current)) {
            throw new BusinessException(
                    "PAYMENT_STATUS_LOCKED",
                    "Giao dịch đã " + ("REFUNDED".equals(current) ? "hoàn tiền" : "hoàn tất")
                            + " — không thể đổi trạng thái, dùng chức năng hoàn tiền nếu cần",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        payment.setStatus(target);
        PaymentResponse response = toResponse(repository.save(payment));
        if ("COMPLETED".equals(payment.getStatus())) {
            publish(DomainEventNames.PAYMENT_COMPLETED, payment);
        } else if ("FAILED".equals(payment.getStatus())) {
            publish(DomainEventNames.PAYMENT_FAILED, payment);
        }
        return response;
    }

    @Transactional
    public PaymentResponse handleSepayWebhook(Map<String, Object> body, String authHeader) {
        if (!sepayService.verifyWebhook(body, authHeader)) {
            throw new BusinessException("SEPAY_INVALID_SIGNATURE", "Chữ ký hoặc API Key SePay không hợp lệ");
        }
        // Biến động tiền RA khỏi tài khoản nhận (vd admin chuyển hoàn cho khách, nội dung vẫn mang
        // mã PAY-… của đơn) không phải tiền khách trả.
        if (sepayService.isOutgoingTransfer(body)) {
            log.info("SePay webhook ignored: outgoing transfer {}", body.get("referenceCode"));
            return null;
        }
        String refId = sepayService.extractReferenceId(body);
        if (!StringUtils.hasText(refId)) {
            throw new BusinessException("SEPAY_MISSING_REF", "Không tìm thấy mã tham chiếu trong Webhook");
        }
        // SePay gửi lại cùng một giao dịch ngân hàng ⇒ đã ghi nhận rồi.
        String bankTxn = sepayService.bankTransactionId(body);
        if (bankTxn != null) {
            PaymentRecord seen = repository.findFirstByReferenceTransactionId(bankTxn).orElse(null);
            if (seen != null) {
                return toResponse(seen);
            }
        }
        PaymentRecord payment = repository.findByReferenceId(refId)
                .orElse(null);
        Long refOrderId = orderIdOf(refId);
        // Mã này đã thu xong mà lại có tiền về (khách chuyển bù phần thiếu, hoặc chuyển hai lần cùng
        // nội dung): đó là một khoản mới của đơn, không phải webhook lặp.
        boolean anotherTransfer = payment != null && "COMPLETED".equals(payment.getStatus())
                && refOrderId != null && bankTxn != null;
        if (anotherTransfer) {
            payment = null;
        }
        if (payment == null && refOrderId != null) {
            Long orderId = refOrderId;
            payment = repository.findByOrderId(orderId).stream()
                    .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            // Nếu chưa có payment record trong DB (ví dụ: khách quét VietQR trực tiếp từ màn hình đơn),
            // tự động tra cứu order và tạo PaymentRecord để hoàn tất thanh toán!
            if (payment == null) {
                try {
                    OrderSummary order = orderClient.getOrder(orderId).data();
                    if (order != null) {
                        PaymentRecord autoPayment = new PaymentRecord();
                        autoPayment.setOrderId(orderId);
                        autoPayment.setUserId(order.userId());
                        // Số phải trả do server tính (phần đơn còn thiếu), không lấy theo số khách chuyển.
                        autoPayment.setAmount(outstanding(orderId, order));
                        autoPayment.setMethod("SEPAY");
                        autoPayment.setReferenceId(anotherTransfer || repository.findByReferenceId(refId).isPresent()
                                ? refId + "-" + bankTxn
                                : refId);
                        autoPayment.setContent("Thanh toan don " + orderId);
                        autoPayment.setDescription("Thanh toán đơn #" + orderId + " qua SePay");
                        autoPayment.setStatus("PENDING");
                        payment = repository.save(autoPayment);
                        log.info("Auto-created PaymentRecord for order {} from SePay webhook", orderId);
                    }
                } catch (Exception ex) {
                    log.error("Could not fetch order {} to auto-create payment: {}", orderId, ex.getMessage());
                }
            }
        }
        if (payment == null && (refId.startsWith("ORD-") || refId.startsWith("ORD"))) {
            payment = repository.findByContentContaining(refId).stream()
                    .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        if (payment == null) {
            throw new NotFoundException("Payment with ref: " + refId, -1L);
        }

        if ("COMPLETED".equals(payment.getStatus())) {
            return toResponse(payment);
        }
        if (bankTxn != null) {
            payment.setReferenceTransactionId(bankTxn);
        }

        BigDecimal received = sepayService.extractAmount(body);
        if (received.signum() <= 0) {
            // Không biết đã nhận bao nhiêu thì không ghi nhận gì; giao dịch giữ PENDING để đối soát tay.
            log.warn("SePay webhook for ref {} has no transfer amount; payment {} left {}",
                    refId, payment.getId(), payment.getStatus());
            return toResponse(repository.save(payment));
        }
        return completeTransfer(payment, received, refId);
    }

    /// Ghi nhận khoản chuyển khoản đã nhận `received` đồng cho giao dịch SePay đang chờ — dùng chung cho
    /// webhook thật và nút giả lập.
    private PaymentResponse completeTransfer(PaymentRecord payment, BigDecimal received, String refId) {
        // Ghi đúng số tiền thực nhận. Chuyển thiếu vẫn là tiền đã thu (hoàn được khi huỷ đơn), nhưng
        // order-service cộng dồn theo số này nên đơn chỉ thành PAID khi tổng đã thu đủ.
        BigDecimal required = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        if (received.compareTo(required) != 0) {
            log.warn("SePay payment amount mismatch for ref {}: received {}, required {}", refId, received, required);
            if (received.compareTo(required) < 0) {
                payment.setDescription(underpaidNote(payment.getDescription(), received, required));
            }
        }
        payment.setAmount(received);
        payment.setStatus("COMPLETED");

        PaymentRecord saved = repository.save(payment);
        if ("SEPAY_TOPUP".equals(saved.getMethod())
                || (saved.getOrderId() != null && saved.getOrderId() <= 0)) {
            walletService.credit(
                    saved.getUserId(),
                    saved.getAmount(),
                    WalletService.SOURCE_TOPUP,
                    saved.getReferenceId(),
                    "Nạp ví qua SePay");
        }
        publish(DomainEventNames.PAYMENT_COMPLETED, saved);
        return toResponse(saved);
    }

    /// Nút "Giả lập thanh toán" (test): coi như SePay vừa báo đã nhận `amount` đồng (bỏ trống = đủ số của
    /// giao dịch) cho giao dịch chuyển khoản đang chờ — thanh toán đơn hoặc nạp ví. Chỉ khi admin bật
    /// `app.payment.simulation-enabled` và tài khoản nằm trong danh sách cho phép; chỉ người trả (hoặc ADMIN).
    @Transactional
    public PaymentResponse simulateTransfer(Long userId, String roles, Long paymentId, String referenceId, BigDecimal amount) {
        // Hai lý do tách riêng: app chỉ biết công tắc bật/tắt (danh sách tài khoản không công khai)
        // nên vẫn hiện nút cho tài khoản ngoài danh sách — câu báo phải nói đúng phải sửa ở đâu.
        if (!rules.simulationEnabled()) {
            throw new BusinessException("PAYMENT_SIMULATION_DISABLED",
                    "Giả lập thanh toán đang tắt (Admin → Cài đặt → Thanh toán → Giả lập thanh toán)",
                    HttpStatus.FORBIDDEN);
        }
        if (!rules.simulationAllowedFor(userId)) {
            throw new BusinessException("PAYMENT_SIMULATION_NOT_ALLOWED",
                    "Tài khoản #" + userId + " chưa có trong danh sách \"Tài khoản được giả lập thanh toán\" "
                            + "(Admin → Cài đặt → Thanh toán → Giả lập thanh toán)",
                    HttpStatus.FORBIDDEN);
        }
        PaymentRecord payment = paymentId != null
                ? find(paymentId)
                : repository.findByReferenceId(StringUtils.hasText(referenceId) ? referenceId.trim() : "")
                        .orElseThrow(() -> new NotFoundException("Payment with ref: " + referenceId, -1L));
        if (!UserRoles.isAdmin(roles) && !userId.equals(payment.getUserId())) {
            throw orderForbidden();
        }
        String method = payment.getMethod() == null ? "" : payment.getMethod().toUpperCase(Locale.ROOT);
        if (!method.startsWith("SEPAY") || !"PENDING".equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException("PAYMENT_SIMULATION_INVALID",
                    "Chỉ giả lập được giao dịch chuyển khoản đang chờ thanh toán (giao dịch này đang "
                            + payment.getStatus() + ")", HttpStatus.CONFLICT);
        }
        BigDecimal received = amount != null && amount.signum() > 0 ? amount : payment.getAmount();
        if (received == null || received.signum() <= 0) {
            throw new BusinessException("PAYMENT_SIMULATION_INVALID", "Số tiền giả lập phải lớn hơn 0");
        }
        payment.setReferenceTransactionId("SIMULATED-" + System.currentTimeMillis());
        payment.setDescription(simulatedNote(payment.getDescription()));
        log.info("Payment {} simulated as received {} by user {}", payment.getId(), received, userId);
        return completeTransfer(payment, received, payment.getReferenceId());
    }

    static String simulatedNote(String description) {
        String full = "[Giả lập] " + (StringUtils.hasText(description) ? description : "Thanh toán");
        return full.length() <= 255 ? full : full.substring(0, 255);
    }

    /// `PAY-{orderId}` hoặc `PAY-{orderId}-…` ⇒ orderId; mã khác (nạp ví, ORD-…) ⇒ null.
    static Long orderIdOf(String refId) {
        if (refId == null || !refId.startsWith("PAY-")) {
            return null;
        }
        String[] parts = refId.split("-");
        try {
            long orderId = parts.length >= 2 ? Long.parseLong(parts[1]) : 0L;
            return orderId > 0 ? orderId : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /// Phần đơn còn phải trả: tổng đơn trừ các khoản đã thu. Đơn đã đủ tiền ⇒ 0.
    private BigDecimal outstanding(Long orderId, OrderSummary order) {
        BigDecimal total = order.totalPrice() == null ? BigDecimal.ZERO : order.totalPrice();
        return total.subtract(completedAmount(orderId)).max(BigDecimal.ZERO);
    }

    private BigDecimal completedAmount(Long orderId) {
        return repository.findByOrderId(orderId).stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(PaymentRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /// Tiền đơn đã thu trừ phần đã hoàn hoặc đang chờ hoàn.
    BigDecimal refundableAmount(Long orderId) {
        BigDecimal claimed = refundRepository.findByOrderId(orderId).stream()
                .filter(r -> !isCompensation(r))
                .filter(r -> "PENDING".equals(r.getStatus()) || "COMPLETED".equals(r.getStatus()))
                .map(RefundRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return completedAmount(orderId).subtract(claimed).max(BigDecimal.ZERO);
    }

    /// Trạng thái thanh toán của đơn sau khi admin duyệt/từ chối một yêu cầu hoàn:
    /// còn yêu cầu chờ ⇒ REFUND_PENDING; đã hoàn đủ số đã thu ⇒ REFUNDED (order-service trả lại mã
    /// giảm giá và điểm đã dùng); hoàn một phần hoặc bị từ chối ⇒ PAID. Phiếu bồi thường sự cố (RF-CMP-…) là
    /// tiền Lock.R đền cho kiện, không phải trả lại tiền đơn ⇒ không tính vào đây (trước đây chi bồi thường
    /// làm đơn hiện "Đã hoàn tiền" dù phí giao chưa hoàn).
    static String orderRefundStatus(List<RefundRecord> refunds, BigDecimal paid) {
        if (refunds.stream().anyMatch(r -> "PENDING".equals(r.getStatus()) && !isCompensation(r))) {
            return "REFUND_PENDING";
        }
        BigDecimal refunded = completedRefunds(refunds, true);
        return refunded.signum() > 0 && refunded.compareTo(paid) >= 0 ? "REFUNDED" : "PAID";
    }

    static boolean isCompensation(RefundRecord refund) {
        return refund.getTransactionId() != null && refund.getTransactionId().startsWith(COMPENSATION_PREFIX);
    }

    /// `RF-CMP-DPI-136-1793250234358-1793250472222` ⇒ `DPI-136-1793250234358` (bỏ tiền tố và mốc tạo phiếu).
    static String incidentCodeOf(String transactionId) {
        if (transactionId == null || !transactionId.startsWith(COMPENSATION_PREFIX)) {
            return null;
        }
        String rest = transactionId.substring(COMPENSATION_PREFIX.length());
        int cut = rest.lastIndexOf('-');
        return cut > 0 ? rest.substring(0, cut) : rest;
    }

    /// Phiếu bồi thường vừa được duyệt (PAID) hoặc từ chối (PAYMENT_FAILED) ⇒ báo order-service cập nhật sự
    /// cố. Lỗi gọi không làm hỏng việc duyệt: admin xem được ở log, sự cố vẫn đứng ở "đang chi trả".
    private void notifyIncidentCompensation(RefundRecord refund, String status, String detail) {
        String incidentCode = incidentCodeOf(refund.getTransactionId());
        if (incidentCode == null || droneIncidentClient == null) {
            return;
        }
        try {
            droneIncidentClient.updateCompensation(incidentCode,
                    new com.huynqb.laundrylocker.payment.dto.internal.IncidentCompensationUpdate(
                            status, refund.getTransactionId(), detail));
        } catch (Exception ex) {
            log.warn("Could not report compensation {} of incident {} to order-service: {}",
                    status, incidentCode, ex.getMessage());
        }
    }

    /// Tổng các khoản đã hoàn xong; `excludeCompensation` bỏ khoản bồi thường sự cố (RF-CMP-…).
    private static BigDecimal completedRefunds(List<RefundRecord> refunds, boolean excludeCompensation) {
        return refunds.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .filter(r -> !excludeCompensation
                        || r.getTransactionId() == null || !r.getTransactionId().startsWith("RF-CMP-"))
                .map(RefundRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void syncOrderRefundStatus(Long orderId, String note, Long actorUserId) {
        String status = orderRefundStatus(refundRepository.findByOrderId(orderId), completedAmount(orderId));
        try {
            orderClient.updatePaymentStatus(orderId, status, note, actorUserId);
        } catch (Exception ex) {
            log.warn("Could not update order {} payment status to {}: {}", orderId, status, ex.getMessage());
        }
    }

    /// loyalty-service thu hồi điểm đã tích theo tỉ lệ tiền đã hoàn / tiền đã thu (luỹ kế, idempotent).
    /// Khoản bồi thường sự cố không tính: đó không phải trả lại tiền khách đã trả.
    private void publishRefunded(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("refundedAmount", completedRefunds(refundRepository.findByOrderId(orderId), true));
        payload.put("paidAmount", completedAmount(orderId));
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.PAYMENT_REFUNDED,
                    DomainEvent.of(DomainEventNames.PAYMENT_REFUNDED, "payment-service", payload));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for order {}: {}", DomainEventNames.PAYMENT_REFUNDED, orderId, ex.getMessage());
        }
    }

    static String underpaidNote(String description, BigDecimal received, BigDecimal required) {
        String note = "Chuyển thiếu: nhận " + received.toBigInteger() + " đ / cần "
                + required.toBigInteger() + " đ";
        String base = StringUtils.hasText(description) ? description + " — " : "";
        String full = base + note;
        return full.length() <= 255 ? full : full.substring(0, 255);
    }

    /// Trang quay về sau cổng SePay (`success_url`/`cancel_url`). Tham số trên URL này do trình duyệt mang
    /// theo và không có chữ ký nên chỉ dùng để tìm giao dịch rồi BÁO trạng thái hiện tại — không đổi trạng
    /// thái, không cộng ví, không phát sự kiện. Tiền chỉ được ghi nhận qua webhook SePay đã xác thực
    /// ({@link #handleSepayWebhook}); app sau khi đóng WebView tự poll đơn / giao dịch ví theo txnRef.
    @Transactional(readOnly = true)
    public SepayReturnStatusResponse sepayReturnStatus(Map<String, String> params) {
        // SePay PG có thể gửi order_invoice_number, order_id, orderId hoặc referenceId
        String rawRef = params.get("referenceId");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("order_invoice_number");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("order_id");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("orderId");
        if (!StringUtils.hasText(rawRef)) {
            throw new BusinessException("SEPAY_RETURN_INVALID", "Thiếu referenceId trong callback SePay");
        }
        final String refId = rawRef.trim();
        PaymentRecord payment = repository.findByReferenceId(refId)
                .orElseThrow(() -> new NotFoundException("Payment with ref: " + refId, -1L));
        return new SepayReturnStatusResponse(
                payment.getReferenceId(),
                payment.getStatus(),
                "COMPLETED".equals(payment.getStatus()),
                payment.getMethod(),
                payment.getAmount());
    }

    public String getSepayPayHtml(String referenceId) {
        PaymentRecord payment = repository.findByReferenceId(referenceId)
                .orElseThrow(() -> new NotFoundException("Payment with ref: " + referenceId, -1L));
        return sepayService.generateAutoSubmitHtml(payment, null);
    }

    /// Nạp ví chỉ qua SePay (VietQR); không gửi `method` ⇒ SEPAY. VNPAY/MOMO đã ngừng hỗ trợ ⇒
    /// 400 PAYMENT_METHOD_UNSUPPORTED; phương thức khác ⇒ 400 PAYMENT_METHOD_INVALID.
    @Transactional
    public TopupResponse createTopupUrl(Long userId, CreateTopupRequest request) {
        String method = StringUtils.hasText(request.method())
                ? request.method().trim().toUpperCase(Locale.ROOT)
                : "SEPAY";
        assertMethodNotRetired(method);
        if (!"SEPAY".equals(method)) {
            throw new BusinessException("PAYMENT_METHOD_INVALID", "Phương thức nạp ví không hợp lệ: " + method);
        }
        assertTopupAmount(request.amount());
        String txnRef = "TOPUP_" + userId + "_" + System.currentTimeMillis();

        String effectiveReturnUrl = StringUtils.hasText(request.returnUrl()) ? request.returnUrl() : null;
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(0L);
        payment.setUserId(userId);
        payment.setAmount(request.amount());
        payment.setMethod("SEPAY_TOPUP");
        payment.setReferenceId(txnRef);
        payment.setDescription("Nạp tiền ví qua SePay");
        String paymentUrl = sepayService.createPayment(payment, effectiveReturnUrl);
        repository.save(payment);
        return new TopupResponse(paymentUrl, txnRef);
    }

    @Transactional(readOnly = true)
    public UserBankAccountDto getUserBankAccount(Long userId) {
        if (userId == null) return null;
        return userBankAccountRepository.findByUserId(userId)
                .map(this::toBankAccountDto)
                .orElse(null);
    }

    @Transactional
    public UserBankAccountDto saveUserBankAccount(Long userId, SaveBankAccountRequest request) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User ID is required");
        }
        UserBankAccount account = userBankAccountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserBankAccount a = new UserBankAccount();
                    a.setUserId(userId);
                    return a;
                });
        account.setBankName(request.bankName().trim());
        account.setBankCode(request.bankCode().trim().toUpperCase());
        account.setAccountNumber(request.accountNumber().trim());
        account.setAccountHolderName(request.accountHolderName().trim().toUpperCase());
        return toBankAccountDto(userBankAccountRepository.save(account));
    }

    private UserBankAccountDto toBankAccountDto(UserBankAccount a) {
        return new UserBankAccountDto(
                a.getUserId(),
                a.getBankName(),
                a.getBankCode(),
                a.getAccountNumber(),
                a.getAccountHolderName(),
                a.getUpdatedAt());
    }

    @Transactional
    public RefundResponse requestRefund(Long userId, CreateRefundRequest request) {
        if (!rules.isRefundEnabled()) {
            throw new BusinessException("REFUND_DISABLED", "Chức năng hoàn tiền hiện đang tạm khóa bởi quản trị viên");
        }
        // Chỉ chủ đơn được yêu cầu hoàn tiền cho đơn của mình.
        assertOrderOwner(request.orderId(), userId);

        List<PaymentRecord> payments = repository.findByOrderId(request.orderId());
        PaymentRecord payment = payments.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()) && p.getAmount() != null && p.getAmount().signum() > 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException("ORDER_NOT_PAID", "Đơn hàng chưa có khoản thanh toán thành công để hoàn"));

        // Kiểm tra thời hạn yêu cầu hoàn tiền theo cấu hình admin
        if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(LocalDateTime.now().minusDays(rules.refundMaxDays()))) {
            throw new BusinessException("REFUND_EXPIRED",
                    "Đã quá thời hạn " + rules.refundMaxDays() + " ngày kể từ khi thanh toán đơn hàng để yêu cầu hoàn tiền");
        }

        // Hoàn tối đa phần tiền đơn đã thu mà chưa hoàn / chưa chờ hoàn; không gửi số tiền ⇒ hoàn hết phần đó.
        BigDecimal refundable = refundableAmount(request.orderId());
        BigDecimal refundAmount = (request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) > 0)
                ? request.amount()
                : refundable;
        if (refundAmount.compareTo(refundable) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_PAID",
                    "Số tiền hoàn tối đa của đơn này là " + refundable.toBigInteger() + " đ");
        }
        // Kiểm tra hạn mức hoàn tiền tối thiểu theo cấu hình admin
        if (refundAmount.compareTo(rules.refundMinAmount()) < 0) {
            throw new BusinessException("REFUND_AMOUNT_TOO_SMALL",
                    "Số tiền hoàn tối thiểu theo quy định là " + rules.refundMinAmount().toBigInteger() + " đ");
        }

        List<RefundRecord> existing = refundRepository.findByOrderId(request.orderId());
        boolean hasActive = existing.stream().anyMatch(r -> "PENDING".equals(r.getStatus()) || "COMPLETED".equals(r.getStatus()));
        if (hasActive) {
            throw new BusinessException("REFUND_ALREADY_REQUESTED", "Đơn hàng đã có yêu cầu hoàn tiền đang chờ hoặc đã hoàn tất");
        }

        String bankName = request.bankName();
        String bankCode = request.bankCode();
        String accountNumber = request.accountNumber();
        String accountHolderName = request.accountHolderName();

        if (!StringUtils.hasText(bankName) || !StringUtils.hasText(accountNumber)) {
            UserBankAccount savedAcc = userId != null ? userBankAccountRepository.findByUserId(userId).orElse(null) : null;
            if (savedAcc != null) {
                bankName = savedAcc.getBankName();
                bankCode = savedAcc.getBankCode();
                accountNumber = savedAcc.getAccountNumber();
                accountHolderName = savedAcc.getAccountHolderName();
            } else {
                throw new BusinessException("BANK_ACCOUNT_REQUIRED", "Vui lòng cung cấp thông tin tài khoản ngân hàng nhận tiền");
            }
        } else if (Boolean.TRUE.equals(request.saveAsDefault()) && userId != null) {
            saveUserBankAccount(userId, new SaveBankAccountRequest(bankName, bankCode, accountNumber, accountHolderName));
        }

        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(payment.getOrderId());
        refund.setUserId(userId != null ? userId : payment.getUserId());
        refund.setAmount(refundAmount);
        refund.setReason(request.reason());
        refund.setCustomerAttachmentUrl(request.customerAttachmentUrl());
        refund.setStatus("PENDING");
        refund.setBankName(bankName);
        refund.setBankCode(bankCode);
        refund.setAccountNumber(accountNumber);
        refund.setAccountHolderName(accountHolderName);
        refund.setTransactionId("RF-" + payment.getReferenceId());
        RefundRecord saved = refundRepository.save(refund);

        try {
            orderClient.updatePaymentStatus(payment.getOrderId(), "REFUND_PENDING", "Khách hàng yêu cầu hoàn tiền", userId);
        } catch (Exception ex) {
            log.warn("Could not update order payment status to REFUND_PENDING: {}", ex.getMessage());
        }

        return toRefund(saved);
    }

    @Transactional
    public RefundResponse adminApproveRefund(Long refundId, Long adminUserId, ProcessRefundRequest request) {
        RefundRecord refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund", refundId));
        if (!"PENDING".equalsIgnoreCase(refund.getStatus())) {
            throw new BusinessException("REFUND_INVALID_STATUS", "Yêu cầu hoàn tiền không ở trạng thái Chờ xử lý");
        }

        // Kiểm tra quy định bắt buộc mã giao dịch chuyển khoản từ admin rules
        if (rules.isRequireBankTransferRef() && (request == null || !StringUtils.hasText(request.bankTransferRef()))) {
            throw new BusinessException("TRANSFER_REF_REQUIRED",
                    "Quy định hệ thống yêu cầu phải nhập mã giao dịch ngân hàng / UNC khi xác nhận hoàn tiền");
        }
        refund.setStatus("COMPLETED");
        refund.setProcessedByUserId(adminUserId);
        refund.setProcessedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.bankTransferRef())) {
            refund.setBankTransferRef(request.bankTransferRef().trim());
        }
        if (request != null && StringUtils.hasText(request.proofImageUrl())) {
            refund.setProofImageUrl(request.proofImageUrl().trim());
        }
        RefundRecord saved = refundRepository.save(refund);

        String note = "Admin đã chuyển khoản hoàn " + saved.getAmount().toBigInteger() + " đ"
                + (StringUtils.hasText(saved.getBankTransferRef()) ? " — Mã GD: " + saved.getBankTransferRef() : "");
        syncOrderRefundStatus(refund.getOrderId(), note, adminUserId);
        publishRefunded(refund.getOrderId());
        notifyIncidentCompensation(saved, "PAID", note);

        PaymentRecord payment = repository.findById(refund.getPaymentId()).orElse(null);
        Long targetUserId = payment != null ? payment.getUserId() : null;
        if (targetUserId != null) {
            try {
                notificationClient.requestNotification(new NotificationRequest(
                        targetUserId,
                        "Hoàn tiền thành công",
                        "Yêu cầu hoàn tiền " + refund.getAmount().toBigInteger() + " đ cho đơn #" + refund.getOrderId()
                                + " đã được chuyển khoản tới " + (refund.getBankName() != null ? refund.getBankName() : "")
                                + " (" + (refund.getAccountNumber() != null ? refund.getAccountNumber() : "") + ").",
                        "REFUND_COMPLETED",
                        refund.getOrderId(),
                        "ORDER"
                ));
            } catch (Exception ex) {
                log.warn("Could not send notification for approved refund {}: {}", refundId, ex.getMessage());
            }
        }

        return toRefund(saved);
    }

    @Transactional
    public RefundResponse adminRejectRefund(Long refundId, Long adminUserId, ProcessRefundRequest request) {
        RefundRecord refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund", refundId));
        if (!"PENDING".equalsIgnoreCase(refund.getStatus())) {
            throw new BusinessException("REFUND_INVALID_STATUS", "Yêu cầu hoàn tiền không ở trạng thái Chờ xử lý");
        }
        String reason = (request != null && StringUtils.hasText(request.rejectionReason()))
                ? request.rejectionReason().trim()
                : "Admin từ chối yêu cầu hoàn tiền";
        refund.setStatus("REJECTED");
        refund.setRejectionReason(reason);
        refund.setProcessedByUserId(adminUserId);
        refund.setProcessedAt(LocalDateTime.now());
        RefundRecord saved = refundRepository.save(refund);

        syncOrderRefundStatus(refund.getOrderId(), "Từ chối hoàn tiền: " + reason, adminUserId);
        notifyIncidentCompensation(saved, "PAYMENT_FAILED", "Từ chối chi bồi thường: " + reason);

        PaymentRecord payment = repository.findById(refund.getPaymentId()).orElse(null);
        Long targetUserId = payment != null ? payment.getUserId() : null;
        if (targetUserId != null) {
            try {
                notificationClient.requestNotification(new NotificationRequest(
                        targetUserId,
                        "Yêu cầu hoàn tiền bị từ chối",
                        "Yêu cầu hoàn tiền cho đơn #" + refund.getOrderId() + " đã bị từ chối. Lý do: " + reason,
                        "REFUND_REJECTED",
                        refund.getOrderId(),
                        "ORDER"
                ));
            } catch (Exception ex) {
                log.warn("Could not send notification for rejected refund {}: {}", refundId, ex.getMessage());
            }
        }

        return toRefund(saved);
    }

    @Transactional
    public RefundResponse refund(Long paymentId, RefundRequest request, Long processedByUserId) {
        PaymentRecord payment = find(paymentId);
        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(payment.getOrderId());
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setProcessedByUserId(processedByUserId);
        refund.setStatus("PENDING");
        refund.setProcessedAt(LocalDateTime.now());
        refund.setTransactionId("RF-" + payment.getReferenceId() + "-" + RANDOM.nextInt(1_000_000));
        return toRefund(refundRepository.save(refund));
    }

    /**
     * Tạo yêu cầu hoàn toàn bộ tiền đã thu của một đơn khi đơn bị huỷ (order-service gọi qua /internal).
     *
     * <p>Không hoàn tiền tự động về ví. Yêu cầu hoàn tiền được tạo ở trạng thái PENDING kèm
     * thông tin tài khoản ngân hàng đã lưu của khách hàng (nếu có), để Admin xem xét và
     * thực hiện chuyển khoản thủ công.
     */
    @Transactional
    public com.huynqb.laundrylocker.payment.dto.internal.OrderRefundResult refundOrder(
            Long orderId, String reason, Long processedByUserId) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (PaymentRecord payment : repository.findByOrderId(orderId)) {
            if (!"COMPLETED".equals(payment.getStatus())
                    || payment.getAmount() == null
                    || payment.getAmount().signum() <= 0) {
                continue;
            }
            // Phiếu bồi thường sự cố gắn cùng payment nhưng không phải là hoàn tiền đơn.
            boolean alreadyRefunded = refundRepository.findByPaymentIdIn(List.of(payment.getId())).stream()
                    .filter(refund -> !isCompensation(refund))
                    .anyMatch(refund -> "COMPLETED".equals(refund.getStatus()) || "PENDING".equals(refund.getStatus()));
            if (alreadyRefunded) {
                continue;
            }
            String transactionId = "RF-" + payment.getReferenceId();
            RefundRecord refund = new RefundRecord();
            refund.setPaymentId(payment.getId());
            refund.setOrderId(payment.getOrderId());
            refund.setUserId(payment.getUserId());
            refund.setAmount(payment.getAmount());
            refund.setReason(reason);
            refund.setProcessedByUserId(processedByUserId);
            refund.setStatus("PENDING");
            refund.setTransactionId(transactionId);

            // Nạp thông tin ngân hàng đã lưu của khách nếu có
            if (payment.getUserId() != null) {
                userBankAccountRepository.findByUserId(payment.getUserId()).ifPresent(acc -> {
                    refund.setBankName(acc.getBankName());
                    refund.setBankCode(acc.getBankCode());
                    refund.setAccountNumber(acc.getAccountNumber());
                    refund.setAccountHolderName(acc.getAccountHolderName());
                });
            }

            refundRepository.save(refund);
            total = total.add(payment.getAmount());
            count++;
        }
        return new com.huynqb.laundrylocker.payment.dto.internal.OrderRefundResult(orderId, total, count);
    }

    /// Phiếu chi bồi thường sự cố rơi kiện (order-service gọi khi khách đồng ý phương án có bồi thường).
    /// `reason` do order-service dựng đủ ý (tình trạng kiện, phương án, cách tính); ở đây chỉ gắn mã sự cố.
    /// `refundShippingFee` ⇒ hoàn thêm phí giao đã thu bằng phiếu hoàn đơn thường (RF-…), tách khỏi phiếu
    /// bồi thường để trạng thái thanh toán của đơn phản ánh đúng việc phí đã được trả lại hay chưa.
    @Transactional
    public com.huynqb.laundrylocker.payment.dto.internal.CompensationRefundResult createCompensationRefund(
            Long orderId, BigDecimal amount, String incidentCode, String reason, boolean refundShippingFee,
            Long actorUserId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("REFUND_AMOUNT_INVALID", "Số tiền bồi thường phải lớn hơn 0");
        }

        List<PaymentRecord> payments = repository.findByOrderId(orderId);
        PaymentRecord payment = payments.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()) && p.getAmount() != null && p.getAmount().signum() > 0)
                .findFirst()
                .orElse(null);

        Long paymentId = payment != null ? payment.getId() : 0L;
        Long customerUserId = payment != null ? payment.getUserId() : actorUserId;

        String code = StringUtils.hasText(incidentCode) ? incidentCode.trim() : String.valueOf(orderId);
        String compReason = "[Bồi thường sự cố " + code + "] "
                + (StringUtils.hasText(reason) ? reason.trim() : "Bồi thường " + formatVnd(amount) + ".");

        String transactionId = COMPENSATION_PREFIX + code + "-" + System.currentTimeMillis();

        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(paymentId);
        refund.setOrderId(orderId);
        refund.setUserId(customerUserId);
        refund.setAmount(amount);
        refund.setStatus("PENDING");
        refund.setReason(compReason);
        refund.setProcessedByUserId(actorUserId);
        refund.setTransactionId(transactionId);

        boolean hasBankAccount = false;
        if (customerUserId != null) {
            var bankAcc = userBankAccountRepository.findByUserId(customerUserId);
            if (bankAcc.isPresent()) {
                var acc = bankAcc.get();
                refund.setBankName(acc.getBankName());
                refund.setBankCode(acc.getBankCode());
                refund.setAccountNumber(acc.getAccountNumber());
                refund.setAccountHolderName(acc.getAccountHolderName());
                hasBankAccount = true;
            } else {
                // Khách hàng chưa có tài khoản ngân hàng -> gửi thông báo nhắc nhở nhập tài khoản ngân hàng
                try {
                    notificationClient.requestNotification(new com.huynqb.laundrylocker.common.dto.NotificationRequest(
                            customerUserId,
                            "Cập nhật tài khoản ngân hàng nhận bồi thường",
                            "Đơn hàng #" + orderId + " đã được tạo phiếu hoàn tiền bồi thường. Vui lòng cập nhật tài khoản ngân hàng để nhận tiền hoàn.",
                            "BANK_ACCOUNT_REQUIRED",
                            orderId,
                            "REFUND"
                    ));
                } catch (Exception ex) {
                    log.warn("Could not notify customer about missing bank account: {}", ex.getMessage());
                }
            }
        }

        RefundRecord saved = refundRepository.save(refund);

        BigDecimal feeRefunded = BigDecimal.ZERO;
        if (refundShippingFee) {
            feeRefunded = refundOrder(orderId,
                    "Hoàn phí giao drone theo phương án xử lý sự cố " + code, actorUserId).refundedAmount();
            if (feeRefunded != null && feeRefunded.signum() > 0) {
                syncOrderRefundStatus(orderId,
                        "Đã tạo yêu cầu hoàn phí giao " + formatVnd(feeRefunded) + " (sự cố " + code + ")", actorUserId);
            }
        }

        return new com.huynqb.laundrylocker.payment.dto.internal.CompensationRefundResult(
                saved.getId(), orderId, amount, transactionId, saved.getStatus(), hasBankAccount);
    }

    /// 150000 ⇒ "150.000đ".
    static String formatVnd(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,d", amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                .replace(',', '.') + "đ";
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(Long orderId) {
        return repository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    /// Thanh toán của một đơn: chủ đơn hoặc ADMIN.
    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(Long orderId, Long userId, String roles) {
        assertOrderOwnerOrAdmin(orderId, userId, roles);
        return listByOrder(orderId);
    }

    /// Một giao dịch: người trả, chủ đơn hoặc ADMIN.
    @Transactional(readOnly = true)
    public PaymentResponse get(Long id, Long userId, String roles) {
        PaymentRecord payment = find(id);
        assertPaymentAccess(payment, userId, roles);
        return toResponse(payment);
    }

    /// Yêu cầu hoàn tiền của một đơn: chủ đơn hoặc ADMIN.
    @Transactional(readOnly = true)
    public List<RefundResponse> refundsByOrder(Long orderId, Long userId, String roles) {
        assertOrderOwnerOrAdmin(orderId, userId, roles);
        return refundsByOrder(orderId);
    }

    /// Một yêu cầu hoàn tiền (có số tài khoản nhận tiền): người trả, chủ đơn hoặc ADMIN.
    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId, Long userId, String roles) {
        RefundRecord refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund", refundId));
        if (!UserRoles.isAdmin(roles)) {
            requireCaller(userId);
            PaymentRecord payment = refund.getPaymentId() == null
                    ? null
                    : repository.findById(refund.getPaymentId()).orElse(null);
            boolean payer = payment != null && userId.equals(payment.getUserId());
            if (!payer) {
                assertOrderOwner(refund.getOrderId(), userId);
            }
        }
        return toRefund(refund);
    }

    private void assertPaymentAccess(PaymentRecord payment, Long userId, String roles) {
        if (UserRoles.isAdmin(roles)) {
            return;
        }
        requireCaller(userId);
        if (userId.equals(payment.getUserId())) {
            return;
        }
        if (payment.getOrderId() == null || payment.getOrderId() <= 0) {
            throw orderForbidden();
        }
        assertOrderOwner(payment.getOrderId(), userId);
    }

    void assertOrderOwnerOrAdmin(Long orderId, Long userId, String roles) {
        if (!UserRoles.isAdmin(roles)) {
            assertOrderOwner(orderId, userId);
        }
    }

    /// Chủ đơn tra từ order-service (nguồn đúng), không tin id trong request.
    void assertOrderOwner(Long orderId, Long userId) {
        requireCaller(userId);
        if (orderId == null || orderId <= 0) {
            throw orderForbidden();
        }
        OrderSummary order;
        try {
            order = orderClient.getOrder(orderId).data();
        } catch (Exception ex) {
            throw new BusinessException("ORDER_LOOKUP_FAILED", "Không lấy được thông tin đơn: " + ex.getMessage());
        }
        if (order == null) {
            throw new NotFoundException("Order", orderId);
        }
        if (!userId.equals(order.userId())) {
            throw orderForbidden();
        }
    }

    private static void requireCaller(Long userId) {
        if (userId == null) {
            throw new BusinessException("AUTH_REQUIRED", "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED);
        }
    }

    private static BusinessException orderForbidden() {
        return new BusinessException("ORDER_FORBIDDEN", "Đơn hàng không thuộc tài khoản của bạn", HttpStatus.FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> refundsByOrder(Long orderId) {
        return refundRepository.findByOrderId(orderId).stream().map(this::toRefund).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId) {
        return refundRepository
                .findById(refundId)
                .map(this::toRefund)
                .orElseThrow(() -> new NotFoundException("Refund", refundId));
    }

    /// Phương thức đã ngừng hỗ trợ (VNPAY, MOMO, kể cả dạng `*_TOPUP`) ⇒ 400 PAYMENT_METHOD_UNSUPPORTED.
    /// Chỉ chặn tạo giao dịch mới; giao dịch cũ mang các phương thức này vẫn đọc và hiển thị bình thường.
    private static void assertMethodNotRetired(String method) {
        if (PaymentRules.isRetiredMethod(method)) {
            throw new BusinessException(
                    "PAYMENT_METHOD_UNSUPPORTED",
                    "Phương thức thanh toán " + method + " không còn được hỗ trợ, vui lòng chọn phương thức khác");
        }
    }

    /// Phương thức thanh toán đơn đã biết nhưng admin đang tắt ⇒ từ chối. Phương thức lạ để luồng cũ xử lý.
    private void assertMethodEnabled(String method) {
        if (PaymentRules.SUPPORTED_METHODS.contains(method) && !rules.isMethodEnabled(method)) {
            throw new BusinessException(
                    "PAYMENT_METHOD_DISABLED", "Phương thức thanh toán đang tạm tắt: " + method);
        }
    }

    /// Hạn mức nạp ví theo cấu hình admin (app.payment.topup-min-amount / topup-max-amount).
    private void assertTopupAmount(BigDecimal amount) {
        BigDecimal min = rules.topupMinAmount();
        BigDecimal max = rules.topupMaxAmount();
        if (amount == null || amount.compareTo(min) < 0 || amount.compareTo(max) > 0) {
            throw new BusinessException(
                    "TOPUP_AMOUNT_OUT_OF_RANGE",
                    "Số tiền nạp phải từ " + min.toPlainString() + " đến " + max.toPlainString() + " VND");
        }
    }

    private PaymentRecord find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Payment", id));
    }

    private PaymentResponse toResponse(PaymentRecord payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReferenceId(),
                payment.getReferenceTransactionId(),
                payment.getUrl(),
                payment.getQr(),
                payment.getDeeplink(),
                payment.getDescription(),
                payment.getContent(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                sepayService.bankTransfer(payment));
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> myRefunds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return refundRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(this::toRefund)
                .toList();
    }

    private RefundResponse toRefund(RefundRecord refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getUserId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getReason(),
                refund.getTransactionId(),
                refund.getBankName(),
                refund.getBankCode(),
                refund.getAccountNumber(),
                refund.getAccountHolderName(),
                refund.getRejectionReason(),
                refund.getBankTransferRef(),
                refund.getProofImageUrl(),
                refund.getCustomerAttachmentUrl(),
                refund.getProcessedByUserId(),
                refund.getRequestedAt(),
                refund.getProcessedAt());
    }

    private String generateReference(Long orderId) {
        return "PAY-" + orderId + "-" + System.currentTimeMillis();
    }

    private void publish(String eventName, PaymentRecord payment) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    eventName,
                    DomainEvent.of(eventName, "payment-service", eventPayload(payment)));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for payment {}: {}", eventName, payment.getId(), ex.getMessage());
        }
    }

    private Map<String, Object> eventPayload(PaymentRecord payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("orderId", payment.getOrderId());
        payload.put("userId", payment.getUserId());
        payload.put("amount", payment.getAmount());
        payload.put("status", payment.getStatus());
        return payload;
    }
}
