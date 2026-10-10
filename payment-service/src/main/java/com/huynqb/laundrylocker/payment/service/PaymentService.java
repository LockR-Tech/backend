package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.SecuritySecrets;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final RefundRepository refundRepository;
    private final UserBankAccountRepository userBankAccountRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Environment environment;
    private final WalletService walletService;
    private final OrderClient orderClient;
    private final NotificationClient notificationClient;
    private final MomoService momoService;
    private final SepayService sepayService;
    /// Hạn mức nạp ví, phương thức đang bật, tự hoàn tất tiền mặt… admin cấu hình (ADR-0005).
    private final PaymentRules rules;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpayPayUrl;

    @Value("${vnpay.tmn-code:DEMO}")
    private String vnpayTmnCode;

    @Value("${vnpay.hash-secret:demo-secret}")
    private String vnpayHashSecret;

    @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}")
    private String vnpayReturnUrl;

    @Value("${momo.redirect-url:http://localhost:8080/api/payments/momo/return}")
    private String momoRedirectUrl;

    @PostConstruct
    void validateProductionPaymentConfig() {
        String[] activeProfiles = environment.getActiveProfiles();
        SecuritySecrets.requireProductionSafeValue(vnpayTmnCode, "vnpay.tmn-code", activeProfiles);
        SecuritySecrets.requireProductionSafeValue(vnpayHashSecret, "vnpay.hash-secret", activeProfiles);
        SecuritySecrets.requireProductionSafeValue(vnpayPayUrl, "vnpay.pay-url", activeProfiles);
        SecuritySecrets.requireProductionSafeValue(vnpayReturnUrl, "vnpay.return-url", activeProfiles);
        SecuritySecrets.requireProductionSafeValue(momoRedirectUrl, "momo.redirect-url", activeProfiles);
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(request.orderId());
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        payment.setMethod(StringUtils.hasText(request.method()) ? request.method().toUpperCase() : "CASH");
        assertMethodEnabled(payment.getMethod());
        payment.setReferenceId(StringUtils.hasText(request.referenceId()) ? request.referenceId() : generateReference(request.orderId()));
        payment.setDescription(request.description());
        payment.setContent("Payment for order " + request.orderId());
        if ("VNPAY".equals(payment.getMethod())) {
            payment.setUrl(buildVnPayUrl(payment, request.bankCode(), request.language()));
        } else if ("MOMO".equals(payment.getMethod())) {
            momoService.createPayment(payment, null);
        } else if ("SEPAY".equals(payment.getMethod())) {
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
     * VNPAY/MOMO return a redirect URL and settle later via the provider callback. The amount is
     * taken from the order (authoritative), not the client. Methods not in
     * {@code app.payment.enabled-methods} are rejected with PAYMENT_METHOD_DISABLED.
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

        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(request.orderId());
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReferenceId(generateReference(request.orderId()));
        payment.setContent("Thanh toan don " + request.orderId());
        payment.setDescription(paymentPurpose(request.description(), request.orderId(), completedAmount));

        switch (method) {
            case "WALLET" -> {
                walletService.debit(
                        userId,
                        amount,
                        WalletService.SOURCE_ORDER_PAYMENT,
                        WalletTransactionRefs.ORDER_PAYMENT_REF_PREFIX + request.orderId(),
                        "Thanh toán đơn #" + request.orderId());
                payment.setStatus("COMPLETED");
            }
            case "CASH" -> {
                if (rules.cashAutoComplete()) {
                    payment.setStatus("COMPLETED");
                }
            }
            case "VNPAY" ->
                    payment.setUrl(buildVnPayUrl(payment, request.bankCode(), request.language(), request.returnUrl()));
            case "MOMO" -> momoService.createPayment(payment, request.returnUrl());
            case "SEPAY" -> sepayService.createPayment(payment, request.returnUrl());
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
    public PaymentResponse handleVnPayReturn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        PaymentRecord payment =
                repository.findByReferenceId(txnRef)
                        .orElseThrow(() -> new NotFoundException("Payment", -1L));
        payment.setReferenceTransactionId(params.get("vnp_TransactionNo"));
        boolean success = verifyVnPay(params) && "00".equals(params.get("vnp_ResponseCode"));
        payment.setStatus(success ? "COMPLETED" : "FAILED");
        PaymentRecord saved = repository.save(payment);
        // Wallet top-up: credit the user's balance once VNPay confirms (idempotent by txnRef,
        // so VNPay return + IPN both firing won't double-credit).
        if (success && "VNPAY_TOPUP".equals(saved.getMethod())) {
            walletService.credit(
                    saved.getUserId(),
                    saved.getAmount(),
                    WalletService.SOURCE_TOPUP,
                    saved.getReferenceId(),
                    "Nạp ví qua VNPay");
        }
        publish(success ? DomainEventNames.PAYMENT_COMPLETED : DomainEventNames.PAYMENT_FAILED, saved);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse handleMomoCallback(Map<String, String> params) {
        String momoOrderId = params.get("orderId"); // MoMo orderId == our referenceId
        PaymentRecord payment =
                repository.findByReferenceId(momoOrderId).orElseThrow(() -> new NotFoundException("Payment", -1L));
        payment.setReferenceTransactionId(params.get("transId"));
        boolean success = momoService.verifyIpn(params) && momoService.isSuccess(params);
        payment.setStatus(success ? "COMPLETED" : "FAILED");
        PaymentRecord saved = repository.save(payment);
        publish(success ? DomainEventNames.PAYMENT_COMPLETED : DomainEventNames.PAYMENT_FAILED, saved);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse handleSepayWebhook(Map<String, Object> body, String authHeader) {
        if (!sepayService.verifyWebhook(body, authHeader)) {
            throw new BusinessException("SEPAY_INVALID_SIGNATURE", "Chữ ký hoặc API Key SePay không hợp lệ");
        }
        String refId = sepayService.extractReferenceId(body);
        if (!StringUtils.hasText(refId)) {
            throw new BusinessException("SEPAY_MISSING_REF", "Không tìm thấy mã tham chiếu trong Webhook");
        }
        PaymentRecord payment = repository.findByReferenceId(refId)
                .orElse(null);
        if (payment == null && refId.startsWith("PAY-")) {
            String[] parts = refId.split("-");
            if (parts.length >= 2) {
                try {
                    Long orderId = Long.parseLong(parts[1]);
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
                                BigDecimal transferAmt = sepayService.extractAmount(body);
                                BigDecimal orderPrice = order.totalPrice() != null ? order.totalPrice() : BigDecimal.ZERO;
                                BigDecimal amount = transferAmt.compareTo(BigDecimal.ZERO) > 0 ? transferAmt : orderPrice;

                                PaymentRecord autoPayment = new PaymentRecord();
                                autoPayment.setOrderId(orderId);
                                autoPayment.setUserId(order.userId());
                                autoPayment.setAmount(amount);
                                autoPayment.setMethod("SEPAY");
                                autoPayment.setReferenceId(refId);
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
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (payment == null) {
            throw new NotFoundException("Payment with ref: " + refId, -1L);
        }

        Object refCode = body.get("referenceCode");
        if (refCode != null && StringUtils.hasText(refCode.toString())) {
            payment.setReferenceTransactionId(refCode.toString());
        }

        if ("COMPLETED".equals(payment.getStatus())) {
            return toResponse(payment);
        }

        BigDecimal transferAmount = sepayService.extractAmount(body);
        if (transferAmount.compareTo(BigDecimal.ZERO) > 0 && transferAmount.compareTo(payment.getAmount()) < 0) {
            log.warn("SePay payment amount mismatch for ref {}: received {} < required {}",
                    refId, transferAmount, payment.getAmount());
            payment.setStatus("FAILED");
        } else {
            payment.setStatus("COMPLETED");
        }

        PaymentRecord saved = repository.save(payment);
        if ("COMPLETED".equals(saved.getStatus())) {
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
        } else {
            publish(DomainEventNames.PAYMENT_FAILED, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse handleSepayReturn(Map<String, String> params) {
        // SePay PG có thể gửi order_invoice_number, order_id, orderId hoặc referenceId
        String rawRef = params.get("referenceId");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("order_invoice_number");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("order_id");
        if (!StringUtils.hasText(rawRef)) rawRef = params.get("orderId");
        if (!StringUtils.hasText(rawRef)) {
            throw new BusinessException("SEPAY_RETURN_INVALID", "Thiếu referenceId trong callback SePay");
        }
        final String refId = rawRef;
        PaymentRecord payment = repository.findByReferenceId(refId)
                .orElseThrow(() -> new NotFoundException("Payment with ref: " + refId, -1L));

        String status = params.get("status");
        if (!"COMPLETED".equals(payment.getStatus())) {
            if ("cancel".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                payment.setStatus("FAILED");
                payment = repository.save(payment);
                publish(DomainEventNames.PAYMENT_FAILED, payment);
            } else {
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
        }
        return toResponse(payment);
    }

    public String getSepayPayHtml(String referenceId) {
        PaymentRecord payment = repository.findByReferenceId(referenceId)
                .orElseThrow(() -> new NotFoundException("Payment with ref: " + referenceId, -1L));
        return sepayService.generateAutoSubmitHtml(payment, null);
    }

    @Transactional
    public TopupResponse createTopupUrl(Long userId, CreateTopupRequest request) {
        assertTopupAmount(request.amount());
        String method = StringUtils.hasText(request.method()) ? request.method().toUpperCase() : "VNPAY";
        String txnRef = "TOPUP_" + userId + "_" + System.currentTimeMillis();

        if ("SEPAY".equals(method)) {
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

        String effectiveReturnUrl = StringUtils.hasText(request.returnUrl()) ? request.returnUrl() : vnpayReturnUrl;

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayTmnCode);
        params.put("vnp_Amount", request.amount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Nap tien vao vi " + request.amount().toPlainString() + " VND");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", StringUtils.hasText(request.locale()) ? request.locale() : "vn");
        params.put("vnp_ReturnUrl", effectiveReturnUrl);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
        if (StringUtils.hasText(request.bankCode())) {
            params.put("vnp_BankCode", request.bankCode());
        }
        String hashData = query(params);
        params.put("vnp_SecureHash", hmac("HmacSHA512", vnpayHashSecret, hashData));
        String paymentUrl = vnpayPayUrl + "?" + query(params);

        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(0L);
        payment.setUserId(userId);
        payment.setAmount(request.amount());
        payment.setMethod("VNPAY_TOPUP");
        payment.setReferenceId(txnRef);
        payment.setUrl(paymentUrl);
        payment.setDescription("Nạp tiền ví qua VNPay");
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

        // Kiểm tra hạn mức hoàn tiền tối thiểu theo cấu hình admin
        BigDecimal refundAmount = (request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) > 0)
                ? request.amount()
                : payment.getAmount();
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
        refund.setAmount(refundAmount);
        refund.setReason(request.reason());
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
        RefundRecord saved = refundRepository.save(refund);

        try {
            String note = "Admin đã chuyển khoản hoàn tiền"
                    + (StringUtils.hasText(saved.getBankTransferRef()) ? " — Mã GD: " + saved.getBankTransferRef() : "");
            orderClient.updatePaymentStatus(refund.getOrderId(), "REFUNDED", note, adminUserId);
        } catch (Exception ex) {
            log.warn("Could not update order payment status to REFUNDED: {}", ex.getMessage());
        }

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

        try {
            orderClient.updatePaymentStatus(refund.getOrderId(), "PAID", "Từ chối hoàn tiền: " + reason, adminUserId);
        } catch (Exception ex) {
            log.warn("Could not restore order payment status: {}", ex.getMessage());
        }

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
            boolean alreadyRefunded = refundRepository.findByPaymentIdIn(List.of(payment.getId())).stream()
                    .anyMatch(refund -> "COMPLETED".equals(refund.getStatus()) || "PENDING".equals(refund.getStatus()));
            if (alreadyRefunded) {
                continue;
            }
            String transactionId = "RF-" + payment.getReferenceId();
            RefundRecord refund = new RefundRecord();
            refund.setPaymentId(payment.getId());
            refund.setOrderId(payment.getOrderId());
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

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(Long orderId) {
        return repository.findByOrderId(orderId).stream().map(this::toResponse).toList();
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
                payment.getUpdatedAt());
    }

    private RefundResponse toRefund(RefundRecord refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
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
                refund.getProcessedByUserId(),
                refund.getRequestedAt(),
                refund.getProcessedAt());
    }

    private String buildVnPayUrl(PaymentRecord payment, String bankCode, String language) {
        return buildVnPayUrl(payment, bankCode, language, null);
    }

    private String buildVnPayUrl(
            PaymentRecord payment, String bankCode, String language, String returnUrl) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayTmnCode);
        params.put("vnp_Amount", payment.getAmount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getReferenceId());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", StringUtils.hasText(language) ? language : "vn");
        params.put("vnp_ReturnUrl", StringUtils.hasText(returnUrl) ? returnUrl : vnpayReturnUrl);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
        if (StringUtils.hasText(bankCode)) {
            params.put("vnp_BankCode", bankCode);
        }
        String hashData = query(params);
        params.put("vnp_SecureHash", hmac("HmacSHA512", vnpayHashSecret, hashData));
        return vnpayPayUrl + "?" + query(params);
    }

    private boolean verifyVnPay(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        Map<String, String> verify = new TreeMap<>(params);
        verify.remove("vnp_SecureHash");
        verify.remove("vnp_SecureHashType");
        return received != null && received.equalsIgnoreCase(hmac("HmacSHA512", vnpayHashSecret, query(verify)));
    }

    private String query(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> url(e.getKey()) + "=" + url(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private String hmac(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign payment payload", ex);
        }
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
