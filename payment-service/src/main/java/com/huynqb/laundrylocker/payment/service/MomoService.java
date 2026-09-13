package com.huynqb.laundrylocker.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MoMo All-in-One (AIO) v2 integration. Activates only when real credentials are configured
 * (env MOMO_PARTNER_CODE / MOMO_ACCESS_KEY / MOMO_SECRET_KEY); otherwise checkout via MoMo throws
 * a clear error instead of failing service boot.
 */
@Slf4j
@Service
public class MomoService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${momo.partner-code:DEMO}")
    private String partnerCode;

    @Value("${momo.access-key:demo}")
    private String accessKey;

    @Value("${momo.secret-key:demo-secret}")
    private String secretKey;

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String endpoint;

    @Value("${momo.redirect-url:http://localhost:8080/api/payments/momo/return}")
    private String redirectUrl;

    @Value("${momo.ipn-url:http://localhost:8080/api/payments/momo/callback}")
    private String ipnUrl;

    public boolean isConfigured() {
        return StringUtils.hasText(partnerCode) && !"DEMO".equalsIgnoreCase(partnerCode);
    }

    /**
     * Calls MoMo create-payment and fills payment.url/deeplink/qr. Returns the pay URL.
     */
    public String createPayment(PaymentRecord payment, String overrideRedirectUrl) {
        if (!isConfigured()) {
            throw new BusinessException(
                    "MOMO_NOT_CONFIGURED", "MoMo chưa được cấu hình (thiếu partnerCode/accessKey/secretKey)");
        }
        String requestId = payment.getReferenceId();
        String momoOrderId = payment.getReferenceId();
        String amount = payment.getAmount().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        String orderInfo = StringUtils.hasText(payment.getDescription()) ? payment.getDescription() : "Thanh toan don hang";
        String extraData = "";
        String requestType = "captureWallet";
        String effectiveRedirect = StringUtils.hasText(overrideRedirectUrl) ? overrideRedirectUrl : redirectUrl;

        String rawSignature =
                "accessKey=" + accessKey
                        + "&amount=" + amount
                        + "&extraData=" + extraData
                        + "&ipnUrl=" + ipnUrl
                        + "&orderId=" + momoOrderId
                        + "&orderInfo=" + orderInfo
                        + "&partnerCode=" + partnerCode
                        + "&redirectUrl=" + effectiveRedirect
                        + "&requestId=" + requestId
                        + "&requestType=" + requestType;
        String signature = hmacSha256(rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("partnerName", "Smart Laundry Locker");
        body.put("storeId", "LockerStore");
        body.put("requestId", requestId);
        body.put("amount", amount);
        body.put("orderId", momoOrderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", effectiveRedirect);
        body.put("ipnUrl", ipnUrl);
        body.put("lang", "vi");
        body.put("requestType", requestType);
        body.put("extraData", extraData);
        body.put("signature", signature);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Object resultCode = result.get("resultCode");
            if (resultCode == null || !"0".equals(resultCode.toString())) {
                throw new BusinessException(
                        "MOMO_CREATE_FAILED", "MoMo tạo thanh toán thất bại: " + result.get("message"));
            }
            String payUrl = (String) result.get("payUrl");
            payment.setUrl(payUrl);
            payment.setDeeplink((String) result.get("deeplink"));
            payment.setQr((String) result.get("qrCodeUrl"));
            return payUrl;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("MOMO_CREATE_ERROR", "Lỗi gọi MoMo: " + ex.getMessage());
        }
    }

    /**
     * Verifies a MoMo IPN/callback signature.
     */
    public boolean verifyIpn(Map<String, String> p) {
        String rawSignature =
                "accessKey=" + accessKey
                        + "&amount=" + p.get("amount")
                        + "&extraData=" + p.getOrDefault("extraData", "")
                        + "&message=" + p.get("message")
                        + "&orderId=" + p.get("orderId")
                        + "&orderInfo=" + p.get("orderInfo")
                        + "&orderType=" + p.get("orderType")
                        + "&partnerCode=" + p.get("partnerCode")
                        + "&payType=" + p.get("payType")
                        + "&requestId=" + p.get("requestId")
                        + "&responseTime=" + p.get("responseTime")
                        + "&resultCode=" + p.get("resultCode")
                        + "&transId=" + p.get("transId");
        String expected = hmacSha256(rawSignature);
        return expected.equals(p.get("signature"));
    }

    public boolean isSuccess(Map<String, String> p) {
        return "0".equals(p.get("resultCode"));
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign MoMo payload", ex);
        }
    }
}
