package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class OrderIncidentRedeliveryGateway implements IncidentRedeliveryGateway {

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DroneParcelIncidentRepository incidentRepository;
    private final DroneMissionRepository missionRepository;
    private final LockerDroneClient lockerDroneClient;

    @Override
    @Transactional
    public Submission createAttempt(Long incidentId, Long originalOrderId, Long customerUserId, String idempotencyKey) {
        try {
            LockerOrder oldOrder = orderRepository.findById(originalOrderId).orElse(null);
            if (oldOrder == null) {
                return new Submission(false, false, null, "Không tìm thấy đơn hàng gốc để giao lại");
            }
            DroneParcelIncident incident = incidentRepository.findById(incidentId).orElse(null);
            String incidentCode = incident != null ? incident.getIncidentCode() : ("DPI-" + incidentId);

            // Sinh mã đơn giao lại mới
            String randomPart = Long.toString(ThreadLocalRandom.current().nextLong(2_176_782_336L), 36).toUpperCase();
            String newOrderCode = "ORD-" + LocalDate.now().toString().replace("-", "") + "-" + randomPart;

            // Tạo đơn mới kế thừa dữ liệu từ đơn cũ
            LockerOrder newOrder = new LockerOrder();
            newOrder.setOrderCode(newOrderCode);
            newOrder.setUserId(oldOrder.getUserId());
            newOrder.setSenderName(oldOrder.getSenderName());
            newOrder.setSenderPhone(oldOrder.getSenderPhone());
            newOrder.setSenderEmail(oldOrder.getSenderEmail());
            newOrder.setReceiverName(oldOrder.getReceiverName());
            newOrder.setReceiverPhone(oldOrder.getReceiverPhone());
            newOrder.setReceiverEmail(oldOrder.getReceiverEmail());
            newOrder.setReceiverId(oldOrder.getReceiverId());
            newOrder.setReceiverUserId(oldOrder.getReceiverUserId());
            newOrder.setDelegateName(oldOrder.getDelegateName());
            newOrder.setDelegatePhone(oldOrder.getDelegatePhone());
            newOrder.setDelegateNote(oldOrder.getDelegateNote());

            // Tuyến đường bay: xuất phát từ Hub (sourceLockerId) tới tủ nhận
            newOrder.setSourceLockerId(oldOrder.getSourceLockerId());
            newOrder.setDestinationLockerId(oldOrder.getDestinationLockerId());
            newOrder.setLockerId(oldOrder.getDestinationLockerId());
            newOrder.setReservedBoxId(oldOrder.getReservedBoxId());
            newOrder.setReceiveBoxId(oldOrder.getReservedBoxId());

            // Vì hàng đã được KTV thu hồi về Hub nên đơn mới BỎ QUA LUÔN bước lấy hàng
            newOrder.setPickupAddress(null);
            newOrder.setPickupLatitude(null);
            newOrder.setPickupLongitude(null);
            newOrder.setPickupNote(null);
            newOrder.setPickupDistanceMeters(null);
            newOrder.setParcelDroppedAt(LocalDateTime.now()); // Hàng đã ở Hub sẵn sàng nạp lên drone

            // Thông tin chi tiết kiện hàng
            newOrder.setParcelWeightGrams(oldOrder.getParcelWeightGrams());
            newOrder.setParcelCategory(oldOrder.getParcelCategory());
            newOrder.setParcelLengthCm(oldOrder.getParcelLengthCm());
            newOrder.setParcelWidthCm(oldOrder.getParcelWidthCm());
            newOrder.setParcelHeightCm(oldOrder.getParcelHeightCm());
            newOrder.setParcelDeclaredValue(oldOrder.getParcelDeclaredValue());
            newOrder.setParcelFragile(oldOrder.getParcelFragile());
            newOrder.setRouteDistanceMeters(oldOrder.getRouteDistanceMeters());
            newOrder.setProhibitedItemsDeclaredAt(LocalDateTime.now());
            newOrder.setType("DRONE_DELIVERY");
            newOrder.setServiceCategory("DRONE_DELIVERY");
            newOrder.setFulfillmentMode(oldOrder.getFulfillmentMode());

            // Giao lại miễn phí (toàn bộ cước phí = 0đ, trạng thái PAID)
            newOrder.setPaymentStatus("PAID");
            newOrder.setPaidAt(LocalDateTime.now());
            newOrder.setPaidAmount(BigDecimal.ZERO);
            newOrder.setTotalPrice(BigDecimal.ZERO);
            newOrder.setOriginalPrice(BigDecimal.ZERO);
            newOrder.setShippingFee(BigDecimal.ZERO);
            newOrder.setDescription("Giao lại miễn phí từ Hub sau sự cố " + incidentCode + " (đơn cũ: " + oldOrder.getOrderCode() + ")");
            newOrder.setCustomerNote("Đơn giao lại miễn phí cho sự cố rơi kiện " + incidentCode);

            // Bắt đầu từ bước check để nạp hàng:
            // Kiểm tra drone rảnh (IDLE) tại Hub để gán và chuyển thẳng sang AWAITING_LOADING (check để nạp hàng)
            DroneUnitDto assignedDrone = null;
            if (newOrder.getSourceLockerId() != null) {
                try {
                    var dronesResp = lockerDroneClient.listDroneUnits(newOrder.getSourceLockerId());
                    if (dronesResp != null && dronesResp.data() != null) {
                        assignedDrone = dronesResp.data().stream()
                                .filter(d -> "IDLE".equalsIgnoreCase(d.status()))
                                .findFirst()
                                .orElse(null);
                    }
                } catch (Exception ex) {
                    log.warn("Could not list drone units for source locker {}: {}", newOrder.getSourceLockerId(), ex.getMessage());
                }
            }

            if (assignedDrone != null) {
                try {
                    lockerDroneClient.transitionDroneStatus(assignedDrone.id(),
                            new DroneStatusTransitionRequest("IDLE", "RESERVED", "Reserved for redelivery order " + newOrderCode));
                } catch (Exception ex) {
                    log.warn("Could not transition drone {} to RESERVED: {}", assignedDrone.id(), ex.getMessage());
                    assignedDrone = null;
                }
            }

            if (assignedDrone != null) {
                newOrder.setStatus("AWAITING_DISPATCH");
                newOrder.setDeliveryStage("ACCEPTED");
            } else {
                newOrder.setStatus("AWAITING_DISPATCH");
                newOrder.setDeliveryStage("AWAITING_DISPATCH");
            }

            newOrder = orderRepository.save(newOrder);

            if (assignedDrone != null) {
                DroneMission mission = new DroneMission();
                mission.setOrderId(newOrder.getId());
                mission.setDroneUnitId(assignedDrone.id());
                mission.setDroneCode(assignedDrone.code());
                mission.setSourceLockerId(newOrder.getSourceLockerId());
                mission.setDestinationLockerId(newOrder.getDestinationLockerId());
                mission.setStatus("AWAITING_LOADING");
                mission.setAssignedByUserId(customerUserId);
                missionRepository.save(mission);
            }

            // Ghi nhận lịch sử tạo đơn mới
            OrderStatusHistory newHistory = new OrderStatusHistory();
            newHistory.setOrderId(newOrder.getId());
            newHistory.setOldStatus(null);
            newHistory.setNewStatus(newOrder.getDeliveryStage());
            newHistory.setChangedByUserId(customerUserId);
            newHistory.setNote("Khởi tạo đơn giao lại miễn phí từ Hub sau sự cố " + incidentCode + " (thay thế đơn " + oldOrder.getOrderCode() + ")");
            historyRepository.save(newHistory);

            // ĐÓNG ĐƠN CŨ với trạng thái rõ ràng
            String oldStage = oldOrder.getDeliveryStage();
            oldOrder.setStatus("CANCELED");
            oldOrder.setDeliveryStage("DROP_REPORTED");
            oldOrder.setStaffNote("Đóng đơn do sự cố rơi kiện (" + incidentCode + "). Đã tạo đơn mới giao lại miễn phí: " + newOrderCode);
            orderRepository.save(oldOrder);

            OrderStatusHistory oldHistory = new OrderStatusHistory();
            oldHistory.setOrderId(oldOrder.getId());
            oldHistory.setOldStatus(oldStage);
            oldHistory.setNewStatus("CANCELED");
            oldHistory.setChangedByUserId(customerUserId);
            oldHistory.setNote("Đóng đơn do sự cố rơi kiện (" + incidentCode + "). Đã chuyển giao lại qua đơn mới: " + newOrderCode);
            historyRepository.save(oldHistory);

            // Cập nhật incident sang mã đơn mới
            if (incident != null) {
                incident.setRedeliveryOrderId(newOrder.getId());
                incidentRepository.save(incident);
            }

            log.info("Closed old order {} and created new redelivery order {} for incident {}",
                    oldOrder.getOrderCode(), newOrderCode, incidentCode);
            return new Submission(true, true, newOrder.getId(),
                    "Đã tạo đơn mới " + newOrderCode + " giao lại miễn phí từ Hub và đóng đơn cũ " + oldOrder.getOrderCode());
        } catch (Exception ex) {
            log.error("Failed to schedule redelivery for incident {}: {}", incidentId, ex.getMessage());
            return new Submission(false, false, null, "Lỗi khởi tạo giao lại: " + ex.getMessage());
        }
    }
}
