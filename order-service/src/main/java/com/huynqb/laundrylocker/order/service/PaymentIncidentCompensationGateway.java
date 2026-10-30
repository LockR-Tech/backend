package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.PaymentRefundClient;
import com.huynqb.laundrylocker.order.dto.CompensationRefundResult;
import com.huynqb.laundrylocker.order.model.DroneIncidentResolutionProposal;
import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneIncidentResolutionProposalRepository;
import com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PaymentIncidentCompensationGateway implements IncidentCompensationGateway {

    private final PaymentRefundClient paymentRefundClient;
    private final LockerOrderRepository orderRepository;
    private final DroneParcelIncidentRepository incidentRepository;
    private final DroneIncidentResolutionProposalRepository proposalRepository;

    @Override
    public Submission submit(Long incidentId, Long orderId, Long beneficiaryUserId,
                             BigDecimal amount, boolean refundShippingFee, String idempotencyKey) {
        try {
            LockerOrder order = orderRepository.findById(orderId).orElse(null);
            DroneParcelIncident incident = incidentRepository.findById(incidentId).orElse(null);
            DroneIncidentResolutionProposal proposal = proposalRepository
                    .findFirstByIncidentIdOrderByProposalVersionDesc(incidentId).orElse(null);
            String incidentCode = incident != null ? incident.getIncidentCode() : "DPI-" + incidentId;

            log.info("Submitting compensation refund for incident {} order {} amount {} shippingFee {}",
                    incidentCode, orderId, amount, refundShippingFee);

            ApiResponse<CompensationRefundResult> response = paymentRefundClient.createCompensationRefund(
                    orderId,
                    amount,
                    null,
                    incidentCode,
                    compensationReason(order, incident, proposal, amount, refundShippingFee),
                    refundShippingFee,
                    beneficiaryUserId);

            if (response != null && response.data() != null) {
                CompensationRefundResult result = response.data();
                String note = (result.bankAccountLinked()
                        ? "Đã tạo phiếu chi bồi thường (đã gắn STK ngân hàng)"
                        : "Đã tạo phiếu chi bồi thường (chờ khách hàng bổ sung STK ngân hàng)")
                        + (refundShippingFee ? " và yêu cầu hoàn phí giao" : "");
                return new Submission(true, true, result.transactionId(), note);
            }
            return new Submission(true, true, "RF-CMP-" + incidentId, "Phiếu hoàn tiền bồi thường đã được tạo");
        } catch (Exception ex) {
            log.error("Failed to submit compensation refund to payment-service: {}", ex.getMessage());
            return new Submission(false, false, null, "Lỗi tạo phiếu hoàn tiền: " + ex.getMessage());
        }
    }

    /// Lý do ghi trên phiếu chi (khách và admin đều đọc): phương án, tình trạng kiện, số tiền và cách tính.
    /// Tỷ lệ in ra là tỷ lệ THỰC so với giá trị khai báo — trước đây in tỷ lệ chính sách (60 %) cả khi admin
    /// đề xuất số khác (150.000đ / 500.000đ bị ghi "Hoàn 60%").
    static String compensationReason(LockerOrder order, DroneParcelIncident incident,
                                     DroneIncidentResolutionProposal proposal, BigDecimal amount,
                                     boolean refundShippingFee) {
        StringBuilder text = new StringBuilder();
        text.append(resolutionLabel(proposal == null ? null : proposal.getResolutionType()))
                .append(" sau sự cố rơi kiện");
        if (order != null && StringUtils.hasText(order.getOrderCode())) {
            text.append(" (đơn ").append(order.getOrderCode()).append(")");
        }
        String condition = parcelLabel(incident);
        if (condition != null) {
            text.append(" — kiện ").append(condition);
        }
        text.append(". Bồi thường ").append(formatVnd(amount));
        BigDecimal declared = order == null ? null : order.getParcelDeclaredValue();
        if (declared != null && declared.signum() > 0) {
            BigDecimal percent = amount.multiply(BigDecimal.valueOf(100)).divide(declared, 0, RoundingMode.HALF_UP);
            text.append(" = ").append(percent.toPlainString()).append("% giá trị khai báo ").append(formatVnd(declared));
        }
        text.append(".");
        if (refundShippingFee) {
            text.append(" Phí giao được hoàn bằng phiếu riêng.");
        }
        if (proposal != null && StringUtils.hasText(proposal.getOverrideReason())
                && !proposal.getOverrideReason().trim().equals(
                        proposal.getResolutionNote() == null ? null : proposal.getResolutionNote().trim())) {
            text.append(" Lý do vượt mức gợi ý: ").append(proposal.getOverrideReason().trim()).append(".");
        }
        if (proposal != null && StringUtils.hasText(proposal.getResolutionNote())) {
            text.append(" Nội dung: ").append(proposal.getResolutionNote().trim());
        }
        return text.toString();
    }

    static String resolutionLabel(String type) {
        if (type == null) {
            return "Bồi thường";
        }
        return switch (type) {
            case "FREE_REDELIVERY" -> "Giao lại miễn phí";
            case "REDELIVERY_PARTIAL_COMPENSATION" -> "Giao lại kèm bồi thường một phần";
            case "COMPENSATION_ONLY" -> "Bồi thường";
            case "MANUAL_RESOLUTION" -> "Thỏa thuận riêng";
            default -> "Bồi thường";
        };
    }

    static String parcelLabel(DroneParcelIncident incident) {
        if (incident == null) {
            return null;
        }
        if ("NOT_FOUND".equals(incident.getRecoveryOutcome()) || "LOST".equals(incident.getParcelStatus())) {
            return "thất lạc";
        }
        if (incident.getParcelCondition() == null) {
            return null;
        }
        return switch (incident.getParcelCondition()) {
            case "INTACT" -> "còn nguyên vẹn";
            case "MINOR_DAMAGE" -> "hư hại nhẹ";
            case "MAJOR_DAMAGE" -> "hư hại nặng";
            case "UNUSABLE" -> "không thể sử dụng";
            default -> null;
        };
    }

    /// 150000 ⇒ "150.000đ".
    static String formatVnd(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,d", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', '.') + "đ";
    }
}
