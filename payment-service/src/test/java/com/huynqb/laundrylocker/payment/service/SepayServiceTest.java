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
    }

    @Test
    void testExtractReferenceIdFromContent() {
        Map<String, Object> body = Map.of(
                "content", "MBVCB.12345 TOPUP_42_1728392102123 chuyen tien"
        );
        String ref = sepayService.extractReferenceId(body);
        assertEquals("TOPUP_42_1728392102123", ref);
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
}
