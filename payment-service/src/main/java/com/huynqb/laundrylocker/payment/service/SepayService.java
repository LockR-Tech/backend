package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tích hợp cổng thanh toán SePay (SePay Payment Gateway).
 * Hỗ trợ tạo phiên thanh toán VietQR/Napas qua trang checkout của SePay (pay.sepay.vn),
 * sinh chữ ký HMAC-SHA256 Base64 chuẩn SDK SePay PG, và xử lý Webhook / IPN.
 */
@Slf4j
@Service
public class SepayService {

    private static final List<String> SIGNED_FIELD_NAMES = List.of(
            "merchant",
            "env",
            "operation",
            "payment_method",
            "order_amount",
            "currency",
            "order_invoice_number",
            "order_description",
            "customer_id",
            "agreement_id",
            "agreement_name",
            "agreement_type",
            "agreement_payment_frequency",
            "agreement_amount_per_payment",
            "success_url",
            "error_url",
            "cancel_url",
            "order_id"
    );

    @Value("${sepay.merchant-id:DEMO}")
    private String merchantId;

    @Value("${sepay.secret-key:demo-secret}")
    private String secretKey;

    @Value("${sepay.api-key:demo-api-key}")
    private String apiKey;

    @Value("${sepay.checkout-url:https://pay-sandbox.sepay.vn/v1/checkout/init}")
    private String checkoutUrl;

    @Value("${sepay.return-url:http://localhost:8080/payments/sepay/callback}")
    private String returnUrl;

    @Value("${sepay.cancel-url:http://localhost:8080/payments/sepay/callback?status=cancel}")
    private String cancelUrl;

    @Value("${sepay.pay-base-url:http://localhost:8080/api/payments/sepay/pay}")
    private String payBaseUrl;

    // Cấu hình tài khoản ngân hàng để sinh VietQR inline (không bắt buộc).
    // Lấy từ SePay Dashboard → Ngân hàng → Tài khoản liên kết.
    @Value("${sepay.bank-bin:}")
    private String bankBin;   // VD: 970432 (VPBank), 970422 (MB Bank)

    @Value("${sepay.bank-account-no:}")
    private String bankAccountNo;

    @Value("${sepay.account-name:LaundryLocker}")
    private String accountName;

    public boolean isConfigured() {
        return StringUtils.hasText(merchantId) && !"DEMO".equalsIgnoreCase(merchantId);
    }

    /**
     * Khởi tạo URL chuyển tiếp thanh toán SePay cho payment record.
     */
    public String createPayment(PaymentRecord payment, String overrideReturnUrl) {
        payment.setUrl(payBaseUrl + "?referenceId=" + payment.getReferenceId());
        // Sinh VietQR inline nếu đã cấu hình tài khoản ngân hàng.
        if (StringUtils.hasText(bankBin) && StringUtils.hasText(bankAccountNo)) {
            payment.setQr(generateVietQrUrl(payment));
        }
        return payment.getUrl();
    }

    /**
     * Sinh URL ảnh VietQR (img.vietqr.io) để mobile hiển thị inline.
     * Nội dung chuyển khoản (addInfo) chứa referenceId để SePay webhook nhận diện.
     */
    public String generateVietQrUrl(PaymentRecord payment) {
        String amount = payment.getAmount() != null
                ? payment.getAmount().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                : "0";
        String info = payment.getReferenceId() != null ? payment.getReferenceId() : "";
        String name = java.net.URLEncoder.encode(accountName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.jpg?amount=%s&addInfo=%s&accountName=%s",
                bankBin, bankAccountNo, amount, info, name);
    }

    /**
     * Chuẩn bị danh sách tham số và chữ ký HMAC-SHA256 để POST sang SePay Checkout.
     */
    public Map<String, String> buildCheckoutFields(PaymentRecord payment, String overrideReturnUrl) {
        String effectiveReturn = StringUtils.hasText(overrideReturnUrl) ? overrideReturnUrl : returnUrl;
        String effectiveCancel = StringUtils.hasText(overrideReturnUrl) ? overrideReturnUrl + "?status=cancel" : cancelUrl;

        BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        String amountStr = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        String description = StringUtils.hasText(payment.getDescription())
                ? payment.getDescription()
                : "Thanh toan " + payment.getReferenceId();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant", merchantId);
        fields.put("operation", "PURCHASE");
        fields.put("order_amount", amountStr);
        fields.put("currency", "VND");
        fields.put("order_invoice_number", payment.getReferenceId());
        fields.put("order_description", description);
        fields.put("success_url", effectiveReturn);
        fields.put("cancel_url", effectiveCancel);

        String signature = signFields(fields);
        fields.put("signature", signature);
        return fields;
    }

    /**
     * Sinh mã HTML auto-submit POST form để mobile WebView mở trực tiếp.
     */
    public String generateAutoSubmitHtml(PaymentRecord payment, String overrideReturnUrl) {
        Map<String, String> fields = buildCheckoutFields(payment, overrideReturnUrl);
        StringBuilder formInputs = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            formInputs.append(String.format(
                    "        <input type=\"hidden\" name=\"%s\" value=\"%s\" />\n",
                    escapeHtml(entry.getKey()), escapeHtml(entry.getValue())));
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"vi\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "    <title>Chuyển hướng đến SePay</title>\n"
                + "    <style>\n"
                + "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 80vh; margin: 0; background: #f8fafc; color: #334155; }\n"
                + "        .loader { width: 44px; height: 44px; border: 4px solid #e2e8f0; border-top-color: #0284c7; border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 20px; }\n"
                + "        @keyframes spin { to { transform: rotate(360deg); } }\n"
                + "        h2 { font-size: 18px; margin: 0 0 8px; color: #0f172a; }\n"
                + "        p { font-size: 14px; margin: 0; color: #64748b; }\n"
                + "    </style>\n"
                + "</head>\n"
                + "<body onload=\"document.getElementById('sepay-form').submit()\">\n"
                + "    <div class=\"loader\"></div>\n"
                + "    <h2>Đang mở cổng thanh toán SePay...</h2>\n"
                + "    <p>Vui lòng đợi trong giây lát</p>\n"
                + "    <form id=\"sepay-form\" action=\"" + escapeHtml(checkoutUrl) + "\" method=\"POST\">\n"
                + formInputs
                + "        <noscript><button type=\"submit\" style=\"margin-top:20px;padding:10px 20px;background:#0284c7;color:#fff;border:none;border-radius:8px;\">Bấm vào đây nếu không tự chuyển hướng</button></noscript>\n"
                + "    </form>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Ký các trường dữ liệu theo quy cách SePay PG:
     * Lọc các trường theo danh sách SIGNED_FIELD_NAMES, định dạng key=value nối bằng dấu phẩy ',',
     * sau đó băm HMAC-SHA256 với secret_key và mã hóa Base64.
     */
    public String signFields(Map<String, String> fields) {
        List<String> signed = new ArrayList<>();
        for (String fieldName : SIGNED_FIELD_NAMES) {
            String val = fields.get(fieldName);
            if (val != null) {
                signed.add(fieldName + "=" + val);
            }
        }
        String payload = String.join(",", signed);
        return hmacSha256Base64(payload, secretKey);
    }

    /**
     * Xác thực Webhook nhận được từ SePay.
     * Hỗ trợ 2 kiểu:
     * 1. SePay Bank Transfer Webhook: kiểm tra header `Authorization: Apikey {apiKey}`.
     * 2. SePay PG Callback: kiểm tra chữ ký signature nếu có.
     */
    public boolean verifyWebhook(Map<String, Object> body, String authHeader) {
        // Kiểm tra qua API Key (Webhook biến động số dư ngân hàng)
        if (StringUtils.hasText(authHeader)) {
            String token = authHeader.replace("Apikey ", "").replace("apikey ", "").trim();
            if (StringUtils.hasText(apiKey) && apiKey.equals(token)) {
                return true;
            }
        }

        // Kiểm tra signature nếu là SePay PG Callback
        Object sigObj = body.get("signature");
        if (sigObj != null && StringUtils.hasText(sigObj.toString())) {
            Map<String, String> strMap = new HashMap<>();
            body.forEach((k, v) -> strMap.put(k, v == null ? null : v.toString()));
            strMap.remove("signature");
            String expected = signFields(strMap);
            return expected.equals(sigObj.toString());
        }

        // Cho phép chế độ demo khi chưa cấu hình key nghiêm ngặt
        if (!isConfigured() || "demo-api-key".equals(apiKey)) {
            log.warn("SePay webhook verified in DEMO/permissive mode");
            return true;
        }

        return false;
    }

    /**
     * Trích xuất referenceId từ body Webhook.
     * Hỗ trợ các trường: order_invoice_number, orderInvoiceNumber, orderId, hoặc quét trong content.
     */
    public String extractReferenceId(Map<String, Object> body) {
        if (body.get("order_invoice_number") != null) {
            return body.get("order_invoice_number").toString();
        }
        if (body.get("orderInvoiceNumber") != null) {
            return body.get("orderInvoiceNumber").toString();
        }
        if (body.get("orderId") != null) {
            return body.get("orderId").toString();
        }
        // Trường hợp webhook ngân hàng chuyển khoản: content chứa TOPUP_... hoặc mã đơn
        if (body.get("content") != null) {
            String content = body.get("content").toString();
            return parseReferenceIdFromContent(content);
        }
        return null;
    }

    /**
     * Trích xuất số tiền giao dịch từ body Webhook.
     */
    public BigDecimal extractAmount(Map<String, Object> body) {
        Object amt = body.get("transferAmount");
        if (amt == null) amt = body.get("order_amount");
        if (amt == null) amt = body.get("amount");
        if (amt != null) {
            try {
                return new BigDecimal(amt.toString());
            } catch (Exception ignored) {
            }
        }
        return BigDecimal.ZERO;
    }

    private String parseReferenceIdFromContent(String content) {
        if (!StringUtils.hasText(content)) return null;
        // Quét tìm chuỗi TOPUP_...
        int topupIdx = content.indexOf("TOPUP_");
        if (topupIdx >= 0) {
            int end = content.indexOf(" ", topupIdx);
            return end > 0 ? content.substring(topupIdx, end) : content.substring(topupIdx);
        }
        // Quét tìm chuỗi PAY-...
        int payIdx = content.indexOf("PAY-");
        if (payIdx >= 0) {
            int end = content.indexOf(" ", payIdx);
            return end > 0 ? content.substring(payIdx, end) : content.substring(payIdx);
        }
        return content.trim();
    }

    private String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Lỗi khi ký số SePay payload", ex);
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
