package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.SecuritySecrets;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.*;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
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
    private final RabbitTemplate rabbitTemplate;
    private final Environment environment;
    private final WalletService walletService;
    private final OrderClient orderClient;
    private final MomoService momoService;

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
        payment.setReferenceId(StringUtils.hasText(request.referenceId()) ? request.referenceId() : generateReference(request.orderId()));
        payment.setDescription(request.description());
        payment.setContent("Payment for order " + request.orderId());
        if ("VNPAY".equals(payment.getMethod())) {
            payment.setUrl(buildVnPayUrl(payment, request.bankCode(), request.language()));
        } else if ("MOMO".equals(payment.getMethod())) {
            momoService.createPayment(payment, null);
        } else if ("CASH".equals(payment.getMethod())) {
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
     * WALLET/CASH settle immediately (status COMPLETED + event); VNPAY/MOMO return a redirect URL
     * and settle later via the provider callback. The amount is taken from the order (authoritative),
     * not the client.
     */
    @Transactional
    public PaymentResponse checkout(Long userId, CheckoutRequest request) {
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

        String method = request.method() == null ? "" : request.method().toUpperCase();
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(request.orderId());
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReferenceId(generateReference(request.orderId()));
        payment.setContent("Thanh toan don " + request.orderId());
        payment.setDescription("Thanh toán đơn #" + request.orderId());

        switch (method) {
            case "WALLET" -> {
                walletService.debit(
                        userId,
                        amount,
                        WalletService.SOURCE_ORDER_PAYMENT,
                        "ORDERPAY-" + request.orderId(),
                        "Thanh toán đơn #" + request.orderId());
                payment.setStatus("COMPLETED");
            }
            case "CASH" -> payment.setStatus("COMPLETED");
            case "VNPAY" ->
                    payment.setUrl(buildVnPayUrl(payment, request.bankCode(), request.language(), request.returnUrl()));
            case "MOMO" -> momoService.createPayment(payment, request.returnUrl());
            default ->
                    throw new BusinessException("PAYMENT_METHOD_INVALID", "Phương thức thanh toán không hợp lệ: " + method);
        }

        PaymentRecord saved = repository.save(payment);
        if ("COMPLETED".equals(saved.getStatus())) {
            publish(DomainEventNames.PAYMENT_COMPLETED, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse updateStatus(Long id, UpdatePaymentStatusRequest request) {
        PaymentRecord payment = find(id);
        payment.setStatus(request.status().toUpperCase());
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
    public TopupResponse createTopupUrl(Long userId, CreateTopupRequest request) {
        String txnRef = "TOPUP_" + userId + "_" + System.currentTimeMillis();
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

    @Transactional
    public RefundResponse refund(Long paymentId, RefundRequest request, Long processedByUserId) {
        PaymentRecord payment = find(paymentId);
        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(payment.getOrderId());
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setProcessedByUserId(processedByUserId);
        refund.setStatus("COMPLETED");
        refund.setProcessedAt(LocalDateTime.now());
        refund.setTransactionId("RF-" + payment.getReferenceId() + "-" + RANDOM.nextInt(1_000_000));
        return toRefund(refundRepository.save(refund));
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
                payment.getDescription());
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
