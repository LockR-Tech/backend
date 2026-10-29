package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneIncidentResolutionProposal;
import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentIncidentCompensationGatewayTest {

    /// Tỷ lệ in trên phiếu là tỷ lệ thực so với giá trị khai báo, không phải tỷ lệ chính sách (test
    /// 2026-10-29: 150.000đ / 500.000đ bị ghi "Hoàn 60%").
    @Test
    void reasonStatesActualShareOfDeclaredValueAndParcelCondition() {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-1");
        order.setParcelDeclaredValue(new BigDecimal("500000"));
        order.setIncidentCompensationRate(new BigDecimal("60"));
        DroneParcelIncident incident = new DroneParcelIncident();
        incident.setParcelCondition("MINOR_DAMAGE");
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setResolutionType("REDELIVERY_PARTIAL_COMPENSATION");

        String reason = PaymentIncidentCompensationGateway.compensationReason(
                order, incident, proposal, new BigDecimal("150000"), true);

        assertEquals("Giao lại kèm bồi thường một phần sau sự cố rơi kiện (đơn ORD-1) — kiện hư hại nhẹ. "
                + "Bồi thường 150.000đ = 30% giá trị khai báo 500.000đ. Phí giao được hoàn bằng phiếu riêng.", reason);
        assertFalse(reason.contains("60%"));
    }

    @Test
    void reasonForLostParcelSaysLostAndCarriesTheAgreement() {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-2");
        order.setParcelDeclaredValue(new BigDecimal("600000"));
        DroneParcelIncident incident = new DroneParcelIncident();
        incident.setRecoveryOutcome("NOT_FOUND");
        incident.setParcelStatus("LOST");
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setResolutionType("MANUAL_RESOLUTION");
        proposal.setResolutionNote("Đền thêm voucher 50.000đ");

        String reason = PaymentIncidentCompensationGateway.compensationReason(
                order, incident, proposal, new BigDecimal("360000"), false);

        assertTrue(reason.startsWith("Thỏa thuận riêng sau sự cố rơi kiện (đơn ORD-2) — kiện thất lạc."));
        assertTrue(reason.contains("360.000đ = 60% giá trị khai báo 600.000đ"));
        assertTrue(reason.endsWith("Nội dung: Đền thêm voucher 50.000đ"));
        assertFalse(reason.contains("Phí giao"));
    }
}
