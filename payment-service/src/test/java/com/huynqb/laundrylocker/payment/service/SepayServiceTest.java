package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SepayServiceTest {

    private SepayService sepayService;

    @BeforeEach
    void setUp() {
        sepayService = new SepayService();
        ReflectionTestUtils.setField(sepayService, "merchantId", "TEST_MERCHANT");
        ReflectionTestUtils.setField(sepayService, "secretKey", "test_secret_key_123");
        ReflectionTestUtils.setField(sepayService, "apiKey", "test_api_key");
        ReflectionTestUtils.setField(sepayService, "checkoutUrl", "https://pay-sandbox.sepay.vn/v1/checkout/init");
        ReflectionTestUtils.setField(sepayService, "returnUrl", "http://localhost:8080/payments/sepay/callback");
        ReflectionTestUtils.setField(sepayService, "cancelUrl", "http://localhost:8080/payments/sepay/callback?status=cancel");
        ReflectionTestUtils.setField(sepayService, "payBaseUrl", "http://localhost:8080/api/payments/sepay/pay");
    }

    @Test
    void testBuildCheckoutFieldsAndSignature() {
        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceId("INV_12345");
        payment.setAmount(BigDecimal.valueOf(50000));
        payment.setDescription("Thanh toan don INV_12345");

        Map<String, String> fields = sepayService.buildCheckoutFields(payment, null);

        assertEquals("TEST_MERCHANT", fields.get("merchant"));
        assertEquals("PURCHASE", fields.get("operation"));
        assertEquals("50000", fields.get("order_amount"));
        assertEquals("VND", fields.get("currency"));
        assertEquals("INV_12345", fields.get("order_invoice_number"));
        assertNotNull(fields.get("signature"));
        assertFalse(fields.get("signature").isEmpty());

        // Kiểm tra tính nhất quán của chữ ký
        String sig1 = fields.get("signature");
        String sig2 = sepayService.signFields(fields);
        assertEquals(sig1, sig2);
    }

    @Test
    void testVerifyWebhookWithApiKey() {
        Map<String, Object> body = Map.of(
                "transferAmount", 50000,
                "content", "TOPUP_99_1234567890 chuyen khoan",
                "referenceCode", "FT123456"
        );

        assertTrue(sepayService.verifyWebhook(body, "Apikey test_api_key"));
        assertTrue(sepayService.verifyWebhook(body, "apikey test_api_key"));
        assertFalse(sepayService.verifyWebhook(body, "Apikey wrong_key"));
        assertFalse(sepayService.verifyWebhook(body, null));
    }

    @Test
    void webhookIsRejectedWhenKeysAreNotConfigured() {
        SepayService unconfigured = new SepayService();
        ReflectionTestUtils.setField(unconfigured, "merchantId", "");
        ReflectionTestUtils.setField(unconfigured, "secretKey", "");
        ReflectionTestUtils.setField(unconfigured, "apiKey", "");
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "transferAmount", 50000,
                "content", "PAY-38 chuyen khoan"));

        assertFalse(unconfigured.verifyWebhook(body, null));
        assertFalse(unconfigured.verifyWebhook(body, "Apikey "));
        assertFalse(unconfigured.verifyWebhook(body, "Apikey anything"));

        // Có chữ ký nhưng chưa cấu hình merchant/secret ⇒ không được nhận.
        body.put("signature", "any-signature");
        assertFalse(unconfigured.verifyWebhook(body, null));

        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceId("PAY-38");
        payment.setAmount(BigDecimal.valueOf(50000));
        assertThrows(com.huynqb.laundrylocker.common.exception.BusinessException.class,
                () -> unconfigured.buildCheckoutFields(payment, null));
    }

    @Test
    void signedCallbackIsAcceptedOnlyWithMatchingSignature() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_invoice_number", "PAY-38");
        fields.put("order_amount", "50000");
        String signature = sepayService.signFields(fields);

        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put("signature", signature);
        assertTrue(sepayService.verifyWebhook(body, null));

        body.put("signature", signature + "x");
        assertFalse(sepayService.verifyWebhook(body, null));
    }

    @Test
    void testExtractReferenceIdFromContent() {
        Map<String, Object> body = Map.of(
                "content", "MBVCB.12345 TOPUP_42_1728392102123 chuyen tien"
        );
        String ref = sepayService.extractReferenceId(body);
        assertEquals("TOPUP_42_1728392102123", ref);

        // PAY-38 với dấu chấm ngân hàng
        Map<String, Object> bankBody1 = Map.of("content", "MBVCB.12345.PAY-38.CT tu 0912345678");
        assertEquals("PAY-38", sepayService.extractReferenceId(bankBody1));

        // PAY 38 dấu cách
        Map<String, Object> bankBody2 = Map.of("content", "Thanh toan PAY 38 qua SePay");
        assertEquals("PAY-38", sepayService.extractReferenceId(bankBody2));

        // PAY38 dính liền
        Map<String, Object> bankBody3 = Map.of("content", "Chuyen tien PAY38");
        assertEquals("PAY-38", sepayService.extractReferenceId(bankBody3));

        // description thay vì content
        Map<String, Object> bankBody4 = Map.of("description", "MBVCB.PAY-99.CT");
        assertEquals("PAY-99", sepayService.extractReferenceId(bankBody4));
    }

    @Test
    void testGenerateAutoSubmitHtml() {
        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceId("TOPUP_1_100");
        payment.setAmount(BigDecimal.valueOf(100000));

        String html = sepayService.generateAutoSubmitHtml(payment, null);
        assertTrue(html.contains("id=\"sepay-form\""));
        assertTrue(html.contains("https://pay-sandbox.sepay.vn/v1/checkout/init"));
        assertTrue(html.contains("TOPUP_1_100"));
        assertTrue(html.contains("100000"));
        assertTrue(html.contains("name=\"signature\""));
    }

    @Test
    void bankTransferCarriesReceiverAccountAndTransferContentForSepay() {
        ReflectionTestUtils.setField(sepayService, "bankBin", "970422");
        ReflectionTestUtils.setField(sepayService, "bankAccountNo", "0123456789");
        ReflectionTestUtils.setField(sepayService, "accountName", "LOCKR");
        com.huynqb.laundrylocker.payment.model.PaymentRecord payment = new com.huynqb.laundrylocker.payment.model.PaymentRecord();
        payment.setMethod("SEPAY");
        payment.setAmount(new java.math.BigDecimal("15000.40"));
        payment.setReferenceId("PAY-42-ABC");

        var transfer = sepayService.bankTransfer(payment);

        org.junit.jupiter.api.Assertions.assertEquals("970422", transfer.bankBin());
        org.junit.jupiter.api.Assertions.assertEquals("0123456789", transfer.accountNo());
        org.junit.jupiter.api.Assertions.assertEquals("LOCKR", transfer.accountName());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("15000"), transfer.amount());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-42-ABC", transfer.transferContent());
    }

    @Test
    void bankTransferIsAbsentForOtherMethodsOrWithoutReceiverAccount() {
        com.huynqb.laundrylocker.payment.model.PaymentRecord payment = new com.huynqb.laundrylocker.payment.model.PaymentRecord();
        payment.setMethod("SEPAY");
        payment.setAmount(java.math.BigDecimal.TEN);
        org.junit.jupiter.api.Assertions.assertNull(sepayService.bankTransfer(payment), "chưa cấu hình tài khoản nhận");

        ReflectionTestUtils.setField(sepayService, "bankBin", "970422");
        ReflectionTestUtils.setField(sepayService, "bankAccountNo", "0123456789");
        payment.setMethod("WALLET");
        org.junit.jupiter.api.Assertions.assertNull(sepayService.bankTransfer(payment));
    }
}
