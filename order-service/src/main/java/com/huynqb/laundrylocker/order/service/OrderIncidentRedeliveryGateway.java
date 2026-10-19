package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class OrderIncidentRedeliveryGateway implements IncidentRedeliveryGateway {

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DroneParcelIncidentRepository incidentRepository;

    @Override
    @Transactional
    public Submission createAttempt(Long incidentId, Long originalOrderId, Long customerUserId, String idempotencyKey) {
        try {
            LockerOrder order = orderRepository.findById(originalOrderId).orElse(null);
            if (order == null) {
                return new Submission(false, false, null, "Không tìm thấy đơn hàng gốc để giao lại");
            }
            DroneParcelIncident incident = incidentRepository.findById(incidentId).orElse(null);
            String incidentCode = incident != null ? incident.getIncidentCode() : ("DPI-" + incidentId);

            String oldStage = order.getDeliveryStage();
            // Cập nhật trạng thái chặng giao của đơn sang AWAITING_DISPATCH để sẵn sàng điều phối giao lại từ Hub
            order.setDeliveryStage("AWAITING_DISPATCH");
            orderRepository.save(order);

            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrderId(order.getId());
            history.setOldStatus(oldStage);
            history.setNewStatus("AWAITING_DISPATCH");
            history.setChangedByUserId(customerUserId);
            history.setNote("Khởi tạo tiến trình giao lại hàng miễn phí sau sự cố rơi kiện: " + incidentCode);
            historyRepository.save(history);

            log.info("Redelivery scheduled for incident {} order {}", incidentCode, originalOrderId);
            return new Submission(true, true, order.getId(),
                    "Đã khởi tạo chặng giao lại miễn phí cho đơn " + order.getOrderCode() + " từ Hub");
        } catch (Exception ex) {
            log.error("Failed to schedule redelivery for incident {}: {}", incidentId, ex.getMessage());
            return new Submission(false, false, null, "Lỗi khởi tạo giao lại: " + ex.getMessage());
        }
    }
}
