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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DroneSourceLockerSelector sourceLockerSelector;

    @Override
    @Transactional
    public Submission createAttempt(Long incidentId, Long originalOrderId, Long customerUserId, String idempotencyKey) {
        // Không bắt lỗi ở đây: hàm chạy chung transaction với bước khách đồng ý phương án. Nuốt lỗi giữa chừng
        // thì hoặc commit dở (đơn mới đã lưu, đơn cũ chưa đóng), hoặc transaction đã bị đánh rollback-only và
        // caller nổ UnexpectedRollbackException. Để lỗi đi lên ⇒ cả bước đồng ý rollback, khách thử lại được.
        LockerOrder oldOrder = orderRepository.findById(originalOrderId).orElse(null);
        if (oldOrder == null) {
            return new Submission(false, false, null, "Không tìm thấy đơn hàng gốc để giao lại");
        }
        DroneParcelIncident incident = incidentRepository.findById(incidentId).orElse(null);
        String incidentCode = incident != null ? incident.getIncidentCode() : ("DPI-" + incidentId);
        // Đã có đơn giao lại (gọi lặp) ⇒ trả lại đơn đó, không tạo đơn thứ hai cho cùng một kiện.
        if (incident != null && incident.getRedeliveryOrderId() != null) {
            return new Submission(true, true, incident.getRedeliveryOrderId(),
                    "Đơn giao lại cho sự cố " + incidentCode + " đã được tạo trước đó");
        }

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

        // Tuyến bay xuất phát từ nơi đang giữ kiện: tủ KTV bàn giao kiện về (recoveryLockerId) nếu tủ đó là trạm
        // bay. Trước đây luôn lấy tủ gửi của đơn cũ ⇒ KTV drone ra tủ bay mà kiện lại nằm ở kho khác (test
        // 2026-10-29, DPI-143: kiện ở CAB-TU01, đơn giao lại xuất phát HCM-TD-01). Kho không có bãi đáp thì giữ
        // tủ bay cũ và ghi rõ phải mang kiện từ kho tới trạm trước khi nạp.
        Long hubLockerId = incident != null ? incident.getRecoveryLockerId() : null;
        boolean launchFromHub = hubLockerId != null && !hubLockerId.equals(oldOrder.getSourceLockerId())
                && sourceLockerSelector != null && sourceLockerSelector.isDroneStation(hubLockerId);
        newOrder.setSourceLockerId(launchFromHub ? hubLockerId : oldOrder.getSourceLockerId());
        newOrder.setDestinationLockerId(oldOrder.getDestinationLockerId());
        newOrder.setLockerId(oldOrder.getDestinationLockerId());
        // Ô nhận chuyển hẳn sang đơn giao lại: đơn cũ (đã đóng) không còn giữ ô, để không đường nhả ô nào của
        // đơn cũ lấy mất ô của đơn mới.
        Long boxId = oldOrder.getReservedBoxId();
        newOrder.setReservedBoxId(boxId);
        newOrder.setReceiveBoxId(boxId);
        String transferNote = hubLockerId != null && !launchFromHub
                && !hubLockerId.equals(oldOrder.getSourceLockerId())
                ? "Kiện đang ở kho tủ #" + hubLockerId + " — mang kiện tới trạm bay tủ #"
                        + oldOrder.getSourceLockerId() + " trước khi nạp lên drone."
                : null;

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
        // Đổi trạm cất cánh thì quãng bay của đơn cũ không còn đúng.
        newOrder.setRouteDistanceMeters(launchFromHub ? null : oldOrder.getRouteDistanceMeters());
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
        newOrder.setStaffNote(transferNote);

        // Kiện đã nằm ở Hub (không có điểm lấy hàng, đã "bỏ kiện"): đơn vào hàng chờ chung của đội bay như đơn
        // gửi tại tủ — KTV drone nào rảnh cũng thấy, tự gán drone ở tủ gửi rồi nạp và phóng như thường.
        // Không tự giữ drone ở đây: nhiệm vụ phải do chính KTV nhận, nếu không không ai thao tác được.
        newOrder.setStatus("AWAITING_DISPATCH");
        newOrder.setDeliveryStage("AWAITING_DISPATCH");
        newOrder = orderRepository.save(newOrder);
        // Ghi nhận lịch sử tạo đơn mới
        OrderStatusHistory newHistory = new OrderStatusHistory();
        newHistory.setOrderId(newOrder.getId());
        newHistory.setOldStatus(null);
        newHistory.setNewStatus(newOrder.getDeliveryStage());
        newHistory.setChangedByUserId(customerUserId);
        newHistory.setNote("Khởi tạo đơn giao lại miễn phí từ Hub sau sự cố " + incidentCode + " (thay thế đơn "
                + oldOrder.getOrderCode() + ")" + (transferNote == null ? "" : ". " + transferNote));
        historyRepository.save(newHistory);

        // ĐÓNG ĐƠN CŨ với trạng thái rõ ràng
        String oldStage = oldOrder.getDeliveryStage();
        oldOrder.setStatus("CANCELED");
        oldOrder.setDeliveryStage("DROP_REPORTED");
        oldOrder.setStaffNote("Đóng đơn do sự cố rơi kiện (" + incidentCode + "). Đã tạo đơn mới giao lại miễn phí: " + newOrderCode);
        oldOrder.setReservedBoxId(null);
        oldOrder.setReceiveBoxId(null);
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
    }
}
