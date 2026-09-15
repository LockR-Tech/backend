package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/// Số liệu thanh toán từ payment-service (order-service không sở hữu payment_db).
@FeignClient(name = "payment-service", contextId = "paymentReportClient", path = "/internal/payments")
public interface PaymentReportClient {

    @GetMapping("/order-summaries")
    ApiResponse<List<OrderPaymentSummary>> getOrderSummaries(@RequestParam("orderIds") Collection<Long> orderIds);

    /// from/to: ISO local date-time theo múi giờ JVM, nửa mở [from, to).
    @GetMapping("/collected")
    ApiResponse<CollectedPayments> getCollected(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "userId", required = false) Long userId);
}
