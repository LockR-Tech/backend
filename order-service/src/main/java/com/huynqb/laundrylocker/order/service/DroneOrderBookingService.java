package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.CreateDroneDeliveryOrderRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryQuoteRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryQuoteResponse;
import com.huynqb.laundrylocker.order.dto.DroneLockerPointResponse;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Đặt đơn giao hàng bằng drone, lấy hàng tận nhà.
 *
 * <p>Người gửi cho biết điểm lấy hàng (vị trí hiện tại hoặc điểm ghim trên bản đồ), liên hệ của
 * mình và của người nhận, tủ nhận và kiện hàng. Hệ thống chọn tủ gửi là tủ có bãi đáp gần điểm lấy
 * hàng nhất còn phục vụ được ({@link DroneSourceLockerSelector}), giữ một ô DRONE ở tủ nhận và tính
 * phí theo khối lượng khai báo. Đơn chờ thanh toán; trả tiền xong mới bắt đầu tìm KTV drone tới lấy
 * hàng ({@link DronePickupService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneOrderBookingService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> PARCEL_CATEGORIES =
            Set.of("DOCUMENT", "FOOD", "CLOTHING", "ELECTRONICS", "COSMETICS", "OTHER");

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserClient userClient;
    private final LockerClient lockerClient;
    private final LockerCellClient lockerCellClient;
    private final NotificationClient notificationClient;
    private final RabbitTemplate rabbitTemplate;
    private final OrderRules rules;
    private final DroneSourceLockerSelector sourceLockerSelector;
    private final DroneDeliveryQueryService queryService;
    /// Mã giảm giá (kiểm theo TỦ GỬI) + điểm thưởng; đơn còn 0đ được ghi đã thanh toán ngay.
    private final OrderBenefitService benefits;
    private final PaidOrderEffects paidEffects;

    /// Báo trước cho người gửi: kiện sẽ đi qua tủ gửi nào, bao xa, phí bao nhiêu. Không giữ gì.
    @Transactional(readOnly = true)
    public DroneDeliveryQuoteResponse quote(DroneDeliveryQuoteRequest request) {
        assertFlightsOpen();
        int weight = validateWeight(request.parcelWeightGrams());
        DroneSourceLockerSelector.Selection selection = sourceLockerSelector.select(
                request.pickupLatitude(), request.pickupLongitude(), request.destinationLockerId(), null);
        return new DroneDeliveryQuoteResponse(
                point(selection.source()),
                point(selection.destination()),
                selection.pickupDistanceMeters(),
                selection.routeDistanceMeters(),
                rules.droneDeliveryFee(weight));
    }

    @Transactional
    public DroneDeliveryOrderResponse create(
            CreateDroneDeliveryOrderRequest request, Long userId, String idempotencyKey) {
        Optional<LockerOrder> existing = StringUtils.hasText(idempotencyKey)
                ? orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                : Optional.empty();
        if (existing.isPresent()) {
            return queryService.get(existing.get().getId(), userId);
        }

        String fulfillmentMode = resolveFulfillmentMode(request.fulfillmentMode(), userId);
        userClient.getUser(userId);
        assertFlightsOpen();
        assertOpenOrderLimit(userId);
        int weight = validateWeight(request.parcelWeightGrams());
        String category = validateParcel(request);
        Contact sender = contact(request.senderName(), request.senderPhone(), request.senderEmail(), "SENDER");
        Contact receiver =
                contact(request.receiverName(), request.receiverPhone(), request.receiverEmail(), "RECEIVER");
        String pickupAddress = required(request.pickupAddress(), "DRONE_PICKUP_ADDRESS_REQUIRED",
                "Pickup address is required");
        if (request.pickupLatitude() == null || request.pickupLongitude() == null) {
            throw new BusinessException(
                    "DRONE_PICKUP_LOCATION_REQUIRED", "Pickup location (latitude and longitude) is required");
        }

        DroneSourceLockerSelector.Selection route = sourceLockerSelector.select(
                request.pickupLatitude(), request.pickupLongitude(), request.destinationLockerId(), null);
        // Mọi kiểm tra xong mới giữ ô nhận: lỗi sau bước này phải nhả ô.
        Long reservedBoxId = reserveDestinationBox(request);

        try {
            LockerOrder order = new LockerOrder();
            order.setOrderCode(generateOrderCode());
            order.setUserId(userId);
            order.setSenderName(sender.name());
            order.setSenderPhone(sender.phone());
            order.setSenderEmail(sender.email());
            order.setReceiverName(receiver.name());
            order.setReceiverPhone(receiver.phone());
            order.setReceiverEmail(receiver.email());
            Long receiverUserId = receiverAccount(receiver.phone());
            order.setReceiverId(receiverUserId);
            order.setReceiverUserId(receiverUserId);
            order.setPickupAddress(pickupAddress);
            order.setPickupLatitude(request.pickupLatitude());
            order.setPickupLongitude(request.pickupLongitude());
            order.setPickupNote(trimToNull(request.pickupNote()));
            order.setPickupDistanceMeters(route.pickupDistanceMeters());
            order.setLockerId(request.destinationLockerId());
            order.setSourceLockerId(route.source().id());
            order.setDestinationLockerId(request.destinationLockerId());
            order.setSendBoxId(reservedBoxId);
            order.setReservedBoxId(reservedBoxId);
            order.setRouteDistanceMeters(route.routeDistanceMeters());
            order.setType("DRONE_DELIVERY");
            order.setServiceCategory("DRONE_DELIVERY");
            order.setStatus("AWAITING_DISPATCH");
            order.setPaymentStatus("UNPAID");
            order.setDeliveryStage("AWAITING_DISPATCH");
            order.setFulfillmentMode(fulfillmentMode);
            order.setParcelWeightGrams(weight);
            order.setParcelCategory(category);
            order.setDescription(trimToNull(request.description()));
            order.setParcelLengthCm(request.parcelLengthCm());
            order.setParcelWidthCm(request.parcelWidthCm());
            order.setParcelHeightCm(request.parcelHeightCm());
            order.setParcelDeclaredValue(request.declaredValue());
            order.setParcelFragile(Boolean.TRUE.equals(request.fragile()));
            order.setProhibitedItemsDeclaredAt(LocalDateTime.now());
            order.setIdempotencyKey(idempotencyKey);
            var incidentPolicy = rules.incidentPolicy();
            order.setIncidentPolicyVersion(incidentPolicy.version());
            order.setIncidentCompensationEnabled(incidentPolicy.enabled());
            order.setIncidentCompensationRate(incidentPolicy.compensationRate());
            order.setIncidentCompensationCap(incidentPolicy.compensationCap());
            order.setIncidentFreeRedelivery(incidentPolicy.freeRedelivery());
            order.setIncidentRefundShippingFee(incidentPolicy.refundShippingFee());
            order.setIncidentRecoverySlaHours(incidentPolicy.recoverySlaHours());
            order.setIncidentApprovalRequired(incidentPolicy.approvalRequired());
            order.setIncidentDisputeAllowed(incidentPolicy.disputeAllowed());
            // Phí theo khối lượng khai báo; đội bay cân lại lúc nạp hàng, nặng hơn thì thu thêm
            // phần chênh (DroneOrderMaintenanceService.confirmLoading).
            BigDecimal fee = rules.droneDeliveryFee(weight);
            order.setTotalPrice(fee);
            order.setOriginalPrice(fee);

            LockerOrder saved = orderRepository.saveAndFlush(order);
            // Giá gốc → mã giảm giá → điểm thưởng (cần id đơn). Mã theo tủ kiểm theo tủ GỬI: tủ khách thật sự
            // dùng dịch vụ là tủ drone cất cánh, tủ nhận chỉ là nơi người nhận tới lấy.
            benefits.applyVouchers(saved,
                    OrderBenefitService.normalizeCodes(request.promotionCode(), null), route.source().id());
            benefits.redeemPoints(saved, request.pointsToUse());
            saved = orderRepository.saveAndFlush(saved);
            addHistory(saved, userId,
                    "Đặt giao drone: lấy hàng tại " + pickupAddress
                            + " · tủ gửi " + lockerLabel(route.source())
                            + " · tủ nhận " + lockerLabel(route.destination()));
            publishCreated(saved);
            // Mã giảm giá + điểm phủ hết phí ⇒ đã thanh toán, mở lượt tìm KTV lấy hàng sau commit.
            boolean free = paidEffects.settleIfFree(saved);
            notifyQuietly(saved.getUserId(), "Đơn hàng mới",
                    "Đơn drone " + saved.getOrderCode()
                            + (free
                                    ? " đã được tạo và được giảm giá toàn bộ. Hệ thống đang tìm kỹ thuật viên tới lấy hàng."
                                    : " đã được tạo. Thanh toán để kỹ thuật viên tới lấy hàng."),
                    saved.getId());
            return queryService.get(saved.getId(), userId);
        } catch (RuntimeException failure) {
            releaseBoxQuietly(reservedBoxId);
            throw failure;
        }
    }

    // ------------------------------------------------------------------ rules

    /// Không nhận đơn mới khi admin tạm dừng bay (thời tiết, sự cố).
    private void assertFlightsOpen() {
        if (rules.droneFlightsSuspended()) {
            throw new BusinessException("DRONE_FLIGHTS_SUSPENDED", "Drone delivery is temporarily suspended");
        }
    }

    /// Mỗi đơn drone chưa giao xong giữ một ô ở tủ nhận và một lượt KTV.
    private void assertOpenOrderLimit(Long userId) {
        int maxOpen = rules.droneMaxOpenOrdersPerUser();
        if (maxOpen > 0
                && orderRepository.countByUserIdAndTypeAndStatus(userId, "DRONE_DELIVERY", "AWAITING_DISPATCH")
                        >= maxOpen) {
            throw new BusinessException(
                    "DRONE_OPEN_ORDER_LIMIT", "You already have " + maxOpen + " drone orders in progress");
        }
    }

    private int validateWeight(Integer parcelWeightGrams) {
        if (parcelWeightGrams == null || parcelWeightGrams <= 0) {
            throw new BusinessException("DRONE_PARCEL_WEIGHT_INVALID", "Parcel weight must be greater than zero");
        }
        if (parcelWeightGrams > rules.droneMaxPayloadWeightGrams()) {
            throw new BusinessException(
                    "DRONE_PAYLOAD_TOO_HEAVY",
                    "Payload exceeds the drone limit of " + rules.droneMaxPayloadWeightGrams()
                            + " grams per order");
        }
        return parcelWeightGrams;
    }

    /// Loại hàng, cam kết không hàng cấm, kích thước lọt khoang, giá trị khai báo trong trần.
    private String validateParcel(CreateDroneDeliveryOrderRequest request) {
        if (!Boolean.TRUE.equals(request.prohibitedItemsDeclared())) {
            throw new BusinessException(
                    "DRONE_PROHIBITED_ITEMS_NOT_DECLARED",
                    "Sender must confirm the parcel contains no prohibited items");
        }
        String category = required(request.parcelCategory(), "DRONE_PARCEL_CATEGORY_REQUIRED",
                "Parcel category is required").toUpperCase();
        if (!PARCEL_CATEGORIES.contains(category)) {
            throw new BusinessException(
                    "DRONE_PARCEL_CATEGORY_INVALID", "Unknown parcel category: " + request.parcelCategory());
        }
        Integer length = request.parcelLengthCm();
        Integer width = request.parcelWidthCm();
        Integer height = request.parcelHeightCm();
        if (length != null || width != null || height != null) {
            if (length == null || width == null || height == null || length <= 0 || width <= 0 || height <= 0) {
                throw new BusinessException(
                        "DRONE_PARCEL_SIZE_INVALID", "Parcel length, width and height must all be provided");
            }
            // Kiện xoay được: so cạnh dài nhất với cạnh dài nhất của khoang, v.v.
            int[] parcel = {length, width, height};
            int[] bay = rules.droneMaxParcelSizeCm();
            Arrays.sort(parcel);
            Arrays.sort(bay);
            for (int i = 0; i < 3; i++) {
                if (parcel[i] > bay[i]) {
                    throw new BusinessException(
                            "DRONE_PARCEL_TOO_LARGE",
                            "Parcel does not fit the drone cargo bay of "
                                    + bay[2] + " x " + bay[1] + " x " + bay[0] + " cm");
                }
            }
        }
        BigDecimal declared = request.declaredValue();
        if (declared != null && (declared.signum() < 0 || declared.compareTo(rules.droneMaxDeclaredValue()) > 0)) {
            throw new BusinessException(
                    "DRONE_DECLARED_VALUE_TOO_HIGH",
                    "Declared value exceeds the limit of " + rules.droneMaxDeclaredValue().toBigInteger() + " VND");
        }
        return category;
    }

    /// Tên, số điện thoại và email đều bắt buộc cho cả người gửi lẫn người nhận: thư báo đơn đã
    /// phóng, mã mở ô và thư "giao không hoàn tất" đều đi qua email.
    private Contact contact(String name, String phone, String email, String who) {
        String cleanName = required(name, "DRONE_" + who + "_NAME_REQUIRED", who + " name is required");
        String cleanPhone = required(phone, "DRONE_" + who + "_PHONE_INVALID", who + " phone is required")
                .replace(" ", "");
        if (!cleanPhone.matches(CreateDroneDeliveryOrderRequest.PHONE)) {
            throw new BusinessException("DRONE_" + who + "_PHONE_INVALID", who + " phone number is not valid");
        }
        String cleanEmail = required(email, "DRONE_" + who + "_EMAIL_INVALID", who + " email is required");
        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException("DRONE_" + who + "_EMAIL_INVALID", who + " email is not valid");
        }
        return new Contact(cleanName, cleanPhone, cleanEmail.toLowerCase());
    }

    private String resolveFulfillmentMode(String requestedMode, Long userId) {
        boolean demoAllowed = rules.droneDemoEnabled() && rules.droneDemoAllowedFor(userId);
        if (!StringUtils.hasText(requestedMode)) {
            return demoAllowed ? "DEMO" : "STANDARD";
        }
        String normalized = requestedMode.trim().toUpperCase();
        if (!"DEMO".equals(normalized) && !"STANDARD".equals(normalized)) {
            throw new BusinessException(
                    "DRONE_FULFILLMENT_MODE_INVALID", "fulfillmentMode must be DEMO or STANDARD");
        }
        if ("DEMO".equals(normalized) && !demoAllowed) {
            throw new BusinessException(
                    "DRONE_DEMO_NOT_ALLOWED", "Drone demo mode is not enabled for this user");
        }
        return normalized;
    }

    // ------------------------------------------------------------------ collaborators

    /// Người nhận có tài khoản Lock.R (trùng số điện thoại) thì thấy được hành trình trong app.
    private Long receiverAccount(String phone) {
        try {
            var response = userClient.getUserByPhone(phone);
            return response == null || response.data() == null ? null : response.data().id();
        } catch (Exception ex) {
            log.info("Drone receiver {} has no Lock.R account or lookup failed: {}", phone, ex.getMessage());
            return null;
        }
    }

    private Long reserveDestinationBox(CreateDroneDeliveryOrderRequest request) {
        Long boxId = request.preferredBoxId();
        if (boxId == null) {
            CellDto cell;
            try {
                cell = lockerCellClient.findAvailable(request.destinationLockerId(), null, "DRONE").data();
            } catch (Exception ex) {
                cell = null;
            }
            if (cell == null || cell.id() == null) {
                throw new BusinessException(
                        "BOX_NOT_AVAILABLE", "Destination locker has no available DRONE cell");
            }
            boxId = cell.id();
        } else {
            CellDto cell = lockerCellClient.getCell(boxId).data();
            if (cell == null) {
                throw new BusinessException("BOX_NOT_FOUND", "Box not found");
            }
            if (!"DRONE".equalsIgnoreCase(cell.cellType())) {
                throw new BusinessException("DRONE_CELL_REQUIRED", "Selected box must be a DRONE cell");
            }
            if (!java.util.Objects.equals(cell.lockerId(), request.destinationLockerId())) {
                throw new BusinessException(
                        "DRONE_DESTINATION_CELL_LOCKER_MISMATCH",
                        "Selected drone cell does not belong to the destination locker");
            }
            if (!"AVAILABLE".equalsIgnoreCase(cell.status())) {
                throw new BusinessException("BOX_NOT_AVAILABLE", "Selected drone cell is not available");
            }
        }
        try {
            lockerClient.reserveBox(boxId, "DRONE");
            return boxId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("BOX_RESERVE_FAILED", "Could not reserve drone box " + boxId);
        }
    }

    private void releaseBoxQuietly(Long boxId) {
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.releaseBox(boxId);
        } catch (RuntimeException ex) {
            log.warn("Could not release drone box {} after a failed booking: {}", boxId, ex.getMessage());
        }
    }

    private void addHistory(LockerOrder order, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(null);
        history.setNewStatus("AWAITING_DISPATCH");
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private void publishCreated(LockerOrder order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderCode", order.getOrderCode());
        payload.put("userId", order.getUserId());
        payload.put("status", order.getStatus());
        payload.put("type", order.getType());
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.ORDER_CREATED,
                    DomainEvent.of(DomainEventNames.ORDER_CREATED, "order-service", payload));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for order {}: {}", DomainEventNames.ORDER_CREATED, order.getId(),
                    ex.getMessage());
        }
    }

    private void notifyQuietly(Long userId, String title, String message, Long orderId) {
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(userId, title, message, "ORDER_CREATED", orderId, "ORDER"));
        } catch (RuntimeException ex) {
            log.warn("Could not notify user {} about drone order {}: {}", userId, orderId, ex.getMessage());
        }
    }

    private static String generateOrderCode() {
        // 36^6 vượt quá Integer.MAX_VALUE nên phải sinh bằng long.
        String randomPart = Long.toString(RANDOM.nextLong(2_176_782_336L), 36).toUpperCase();
        return "ORD-" + LocalDate.now().toString().replace("-", "") + "-" + randomPart;
    }

    private static DroneLockerPointResponse point(LockerInfo locker) {
        return new DroneLockerPointResponse(
                locker.id(), locker.code(), locker.name(), locker.address(), locker.latitude(), locker.longitude());
    }

    private static String lockerLabel(LockerInfo locker) {
        return StringUtils.hasText(locker.name()) ? locker.name() : "#" + locker.id();
    }

    private static String required(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Contact(String name, String phone, String email) {
    }
}
