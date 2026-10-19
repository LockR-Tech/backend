package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.PaymentRefundClient;
import com.huynqb.laundrylocker.order.dto.CompensationRefundResult;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PaymentIncidentCompensationGateway implements IncidentCompensationGateway {

    private final PaymentRefundClient paymentRefundClient;
    private final LockerOrderRepository orderRepository;
    private final DroneParcelIncidentRepository incidentRepository;

    @Override
    public Submission submit(Long incidentId, Long orderId, Long beneficiaryUserId,
                             BigDecimal amount, boolean refundShippingFee, String idempotencyKey) {
        try {
            LockerOrder order = orderRepository.findById(orderId).orElse(null);
            String incidentCode = incidentRepository.findById(incidentId)
                    .map(inc -> inc.getIncidentCode())
                    .orElse("DPI-" + incidentId);
            BigDecimal rate = order != null ? order.getIncidentCompensationRate() : null;

            log.info("Submitting compensation refund for incident {} order {} amount {}",
                    incidentCode, orderId, amount);

            ApiResponse<CompensationRefundResult> response = paymentRefundClient.createCompensationRefund(
                    orderId,
                    amount,
                    rate,
                    incidentCode,
                    "Bồi thường sự cố rơi kiện",
                    beneficiaryUserId);

            if (response != null && response.data() != null) {
                CompensationRefundResult result = response.data();
                String note = result.bankAccountLinked()
                        ? "Đã tạo phiếu hoàn tiền bồi thường (đã gắn STK ngân hàng)"
                        : "Đã tạo phiếu hoàn tiền bồi thường (chờ khách hàng bổ sung STK ngân hàng)";
                return new Submission(true, true, result.transactionId(), note);
            }
            return new Submission(true, true, "RF-CMP-" + incidentId, "Phiếu hoàn tiền bồi thường đã được tạo");
        } catch (Exception ex) {
            log.error("Failed to submit compensation refund to payment-service: {}", ex.getMessage());
            return new Submission(false, false, null, "Lỗi tạo phiếu hoàn tiền: " + ex.getMessage());
        }
    }
}
