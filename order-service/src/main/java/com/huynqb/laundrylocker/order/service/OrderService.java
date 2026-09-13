package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.model.*;
import com.huynqb.laundrylocker.order.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> CANCELABLE = Set.of("INITIALIZED", "RESERVED", "WAITING", "AWAITING_DISPATCH");

    private final LockerOrderRepository orderRepository;
    private final OrderDetailRepository detailRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderRatingRepository ratingRepository;
    private final OrderComplaintRepository complaintRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PromotionClaimRepository promotionClaimRepository;
    private final RabbitTemplate rabbitTemplate;
    private final UserClient userClient;
    private final LockerClient lockerClient;
    private final LockerCellClient lockerCellClient;
    private final NotificationClient notificationClient;
    private final QrTokenService qrTokenService;

    @Value("${app.order.pickup-hours-limit:24}")
    private int pickupHoursLimit;

    @Value("${app.order.pickup-overtime-fee-per-hour:500}")
    private int overtimeFeePerHour;

    @Value("${app.order.pickup-max-overtime-fee:50000}")
    private int maxOvertimeFee;

    @Value("${app.order.pickup-max-overtime-percent:50}")
    private int maxOvertimePercent;

    @Value("${app.order.send-pickup-hours-limit:48}")
    private int sendPickupHoursLimit;

    @Value("${app.order.send-base-fee:15000}")
    private long sendBaseFee;

    @Value("${app.drone.demo.enabled:true}")
    private boolean droneDemoEnabled = true;

    @Value("${app.drone.demo.allowed-user-ids:}")
    private String droneDemoAllowedUserIds = "";

    @Value("${app.order.rental-rate-standard:5000}")
    private long rentalRateStandard;

    @Value("${app.order.rental-rate-xl:10000}")
    private long rentalRateXl;

    @Value("${app.order.reminder-cooldown-minutes:60}")
    private int reminderCooldownMinutes;

    @Value("${app.order.auto-cancel-hours:24}")
    private int autoCancelHours;

    // Chặn khách bỏ hàng / bắt đầu thuê khi đơn có phí nhưng chưa thanh toán.
    @Value("${app.order.require-payment-before-drop:true}")
    private boolean requirePaymentBeforeDrop;

    // G3: quá hạn lấy hàng quá số giờ này thì coi như đồ được dời vào kho —
    // đơn sang EXPIRED, chốt phí quá hạn và nhả ô cho khách khác. 0 = tắt.
    @Value("${app.order.overdue-release-hours:24}")
    private int overdueReleaseHours;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        userClient.getUser(request.userId());
        LockerOrder order = new LockerOrder();
        order.setOrderCode(generateOrderCode());
        order.setUserId(request.userId());
        order.setReceiverId(request.receiverId());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverName(request.receiverName());
        order.setLockerId(request.lockerId());
        order.setStoreId(request.storeId());
        order.setSendBoxId(resolveAndReserveSendBox(request));
        order.setType(StringUtils.hasText(request.type()) ? request.type().toUpperCase() : "STORAGE");
        order.setServiceCategory(
                StringUtils.hasText(request.serviceCategory()) ? request.serviceCategory().toUpperCase() : "STORAGE");
        order.setStatus("INITIALIZED");
        order.setPinCode(generatePinCode());
        order.setPinCodeIssuedAt(LocalDateTime.now());
        order.setCustomerNote(request.customerNote());
        order.setDeliveryAddress(request.deliveryAddress());
        order.setIntendedReceiveAt(request.intendedReceiveAt());
        if (request.estimatedWeight() != null) {
            order.setActualWeight(request.estimatedWeight());
        }

        LockerOrder saved = orderRepository.save(order);
        BigDecimal calculatedTotal = saveDetailsAndCalculate(saved.getId(), request);
        saved.setTotalPrice(request.totalPrice() == null ? calculatedTotal : request.totalPrice());
        saved.setOriginalPrice(saved.getTotalPrice());
        applyPromotion(saved, request.promotionCode(), request.promotionCodes());
        saved = orderRepository.save(saved);
        addHistory(saved.getId(), null, saved.getStatus(), saved.getUserId(), "Order created");
        publish(DomainEventNames.ORDER_CREATED, saved, Map.of("orderId", saved.getId(), "userId", saved.getUserId()));
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        LockerOrder order = find(id);
        String target = request.status() == null ? "" : request.status().toUpperCase();
        if ("CANCELED".equals(target) || "COMPLETED".equals(target)) {
            releaseBoxes(order);
        }
        if ("RETURNED".equals(target)) {
            occupyBoxQuietly(request.receiveBoxId());
        }
        return transition(order, request.status(), request.staffId(), request.receiveBoxId(), null);
    }

    @Transactional
    public OrderResponse confirm(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        validateStatus(order, Set.of("INITIALIZED"));
        if ("RENTAL".equalsIgnoreCase(order.getType())) {
            occupyBoxQuietly(order.getSendBoxId());
            order.setPickupDeadline(LocalDateTime.now().plusHours(resolveRentalDurationHours(order)));
            return transition(order, "STORING", userId, null, "Renter placed items; multi-use PIN active until deadline");
        }
        assertPaidBeforeDrop(order);
        occupyBoxQuietly(order.getSendBoxId());
        if ("SEND".equalsIgnoreCase(order.getType())) {
            // Stage 2 of the SEND flow: the drop PIN dies here, a fresh pickup PIN
            // goes to the receiver — the sender can no longer open the cell.
            order.setPinCode(generatePinCode());
            order.setPinCodeIssuedAt(LocalDateTime.now());
            order.setPickupDeadline(LocalDateTime.now().plusHours(sendPickupHoursLimit));
            notifyParcelReadyForReceiver(order);
            return transition(order, "STORING", userId, null,
                    "Sender dropped parcel; pickup PIN issued to receiver " + order.getReceiverPhone());
        }
        return transition(order, "STORING", userId, null, "Customer confirmed items dropped in locker");
    }

    @Transactional
    public OrderResponse createSend(SendOrderRequest request, Long userId) {
        Long boxId = request.boxId();
        if (boxId == null) {
            boxId = findAvailableCell(request.lockerId(), request.size(), "STANDARD");
        }
        BigDecimal price = request.totalPrice() == null ? BigDecimal.valueOf(sendBaseFee) : request.totalPrice();
        return create(
                new CreateOrderRequest(
                        userId, request.lockerId(), boxId, null, null,
                        "SEND", "PARCEL",
                        null, request.receiverPhone(), request.receiverName(),
                        null, null, request.note(), null, null, null, request.promotionCode(), null, price));
    }

    @Transactional
    public OrderResponse createRental(RentalOrderRequest request, Long userId) {
        String cellType = StringUtils.hasText(request.cellType()) ? request.cellType().toUpperCase() : "STANDARD";
        if ("DRONE".equals(cellType)) {
            throw new BusinessException("DRONE_CELL_RESTRICTED", "Drone cells cannot be rented");
        }
        Long boxId = request.boxId();
        if (boxId == null) {
            boxId = findAvailableCell(request.lockerId(), null, cellType);
        }
        BigDecimal price = rentalRate(cellType).multiply(BigDecimal.valueOf(request.hours()));
        OrderResponse created =
                create(
                        new CreateOrderRequest(
                                userId, request.lockerId(), boxId, null, null,
                                "RENTAL", "RENTAL",
                                null, null, null, null, null, request.note(), null, null, null, request.promotionCode(), null, price));
        LockerOrder order = find(created.id());
        order.setPickupDeadline(null);
        order.setRentalDurationHours(request.hours());
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public DroneDeliveryOrderResponse createDroneDelivery(
            CreateDroneDeliveryOrderRequest request, Long userId, String idempotencyKey) {
        Optional<LockerOrder> existing =
                StringUtils.hasText(idempotencyKey)
                        ? orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        : Optional.empty();
        if (existing.isPresent()) {
            return toDroneDeliveryResponse(existing.get());
        }

        String fulfillmentMode = resolveDroneFulfillmentMode(request.fulfillmentMode(), userId);
        userClient.getUser(userId);
        Long reservedBoxId = resolveAndReserveDroneBox(request);

        LockerOrder order = new LockerOrder();
        order.setOrderCode(generateOrderCode());
        order.setUserId(userId);
        order.setReceiverId(userId);
        order.setReceiverUserId(userId);
        order.setLockerId(request.destinationLockerId());
        order.setDestinationLockerId(request.destinationLockerId());
        order.setSendBoxId(reservedBoxId);
        order.setReservedBoxId(reservedBoxId);
        order.setType("DRONE_DELIVERY");
        order.setServiceCategory("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setPaymentStatus("UNPAID");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setFulfillmentMode(fulfillmentMode);
        order.setParcelWeightGrams(request.parcelWeightGrams());
        order.setDescription(request.description());
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalPrice(BigDecimal.valueOf(sendBaseFee));
        order.setOriginalPrice(BigDecimal.valueOf(sendBaseFee));

        LockerOrder saved = orderRepository.save(order);
        notifyMaintenanceDroneOrderCreated(saved);
        return toDroneDeliveryResponse(saved);
    }

    private String resolveDroneFulfillmentMode(String requestedMode, Long userId) {
        if (!StringUtils.hasText(requestedMode)) {
            return canUseDroneDemo(userId) ? "DEMO" : "STANDARD";
        }
        String normalized = requestedMode.trim().toUpperCase();
        if (!Set.of("DEMO", "STANDARD").contains(normalized)) {
            throw new BusinessException(
                    "DRONE_FULFILLMENT_MODE_INVALID", "fulfillmentMode must be DEMO or STANDARD");
        }
        if ("DEMO".equals(normalized) && !canUseDroneDemo(userId)) {
            throw new BusinessException(
                    "DRONE_DEMO_NOT_ALLOWED", "Drone demo mode is not enabled for this user");
        }
        return normalized;
    }

    private boolean canUseDroneDemo(Long userId) {
        if (!droneDemoEnabled) {
            return false;
        }
        if (!StringUtils.hasText(droneDemoAllowedUserIds)) {
            return true;
        }
        String expected = String.valueOf(userId);
        return java.util.Arrays.stream(droneDemoAllowedUserIds.split(","))
                .map(String::trim)
                .anyMatch(expected::equals);
    }

    @Transactional(readOnly = true)
    public DroneDeliveryOrderResponse getDroneDelivery(Long orderId, Long userId) {
        LockerOrder order = find(orderId);
        assertOwner(order, userId);
        return toDroneDeliveryResponse(order);
    }

    @Transactional
    public OrderResponse extendRental(Long id, Long userId, int hours) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        if (!"RENTAL".equalsIgnoreCase(order.getType())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Only rental orders can be extended");
        }
        validateStatus(order, Set.of("INITIALIZED", "STORING"));
        order.setRentalDurationHours(resolveRentalDurationHours(order) + hours);
        if (!"INITIALIZED".equalsIgnoreCase(order.getStatus())) {
            LocalDateTime base =
                    order.getPickupDeadline() == null || order.getPickupDeadline().isBefore(LocalDateTime.now())
                            ? LocalDateTime.now()
                            : order.getPickupDeadline();
            order.setPickupDeadline(base.plusHours(hours));
        }
        BigDecimal extra = rentalRate(cellTypeOfRental(order)).multiply(BigDecimal.valueOf(hours));
        order.setTotalPrice(order.getTotalPrice().add(extra));
        order.setOriginalPrice(order.getOriginalPrice().add(extra));
        if (extra.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus("UNPAID");
            order.setPaidAt(null);
        }
        LockerOrder saved = orderRepository.save(order);
        addHistory(saved.getId(), saved.getStatus(), saved.getStatus(), userId,
                "Rental extended by " + hours + "h until " + saved.getPickupDeadline());
        notifyQuietly(saved.getUserId(), "Rental extended",
                "Rental " + saved.getOrderCode() + " extended until " + saved.getPickupDeadline(),
                "ORDER_RENTAL_EXTENDED", saved.getId());
        return toResponse(saved);
    }

    private String cellTypeOfRental(LockerOrder order) {
        if (order.getSendBoxId() == null) {
            return "STANDARD";
        }
        try {
            CellDto cell = lockerCellClient.getCell(order.getSendBoxId()).data();
            return cell == null || cell.cellType() == null ? "STANDARD" : cell.cellType();
        } catch (Exception ex) {
            return "STANDARD";
        }
    }

    private BigDecimal rentalRate(String cellType) {
        return BigDecimal.valueOf("XL".equalsIgnoreCase(cellType) ? rentalRateXl : rentalRateStandard);
    }

    private Long findAvailableCell(Long lockerId, String size, String cellType) {
        try {
            CellDto cell = lockerCellClient.findAvailable(lockerId, size, cellType).data();
            if (cell == null || cell.id() == null) {
                throw new BusinessException("BOX_NOT_AVAILABLE", "No available cell of requested type");
            }
            return cell.id();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "No available cell of requested type");
        }
    }

    private Long resolveAndReserveDroneBox(CreateDroneDeliveryOrderRequest request) {
        Long boxId = request.preferredBoxId();
        if (boxId == null) {
            boxId = findAvailableCell(request.destinationLockerId(), null, "DRONE");
        } else {
            CellDto cell = lockerCellClient.getCell(boxId).data();
            if (cell == null) {
                throw new BusinessException("BOX_NOT_FOUND", "Box not found");
            }
            if (!"DRONE".equalsIgnoreCase(cell.cellType())) {
                throw new BusinessException("DRONE_CELL_REQUIRED", "Selected box must be a DRONE cell");
            }
            if (!"AVAILABLE".equalsIgnoreCase(cell.status())) {
                throw new BusinessException("BOX_NOT_AVAILABLE", "Selected drone cell is not available");
            }
        }
        try {
            lockerClient.reserveBox(boxId, "DRONE");
            return boxId;
        } catch (Exception ex) {
            throw unwrapDownstreamError(ex, "BOX_RESERVE_FAILED", "Could not reserve drone box " + boxId);
        }
    }

    private void notifyParcelReadyForReceiver(LockerOrder order) {
        String message =
                "Parcel " + order.getOrderCode() + " is waiting in locker " + order.getLockerId()
                        + ". Pickup PIN: " + order.getPinCode()
                        + ". Deadline: " + order.getPickupDeadline();
        try {
            var receiver = userClient.getUserByPhone(order.getReceiverPhone()).data();
            if (receiver != null && receiver.id() != null) {
                order.setReceiverId(receiver.id());
                notificationClient.requestNotification(
                        new NotificationRequest(receiver.id(), "Parcel waiting for you", message, "ORDER_PARCEL_READY", order.getId(), "ORDER"));
            }
        } catch (Exception ex) {
            // Receiver has no account (or user-service is down): the sender keeps the
            // PIN in their order detail and shares it out-of-band (SMS gateway is a
            // production integration point).
            log.info("Receiver {} not notified in-app for order {}: {}", order.getReceiverPhone(), order.getId(), ex.getMessage());
        }
        notifyQuietly(order.getUserId(), "Parcel stored",
                "Parcel " + order.getOrderCode() + " stored. Receiver " + order.getReceiverPhone()
                        + " can pick up with PIN " + order.getPinCode() + " before " + order.getPickupDeadline(),
                "ORDER_PARCEL_STORED", order.getId());
    }

    private void notifyQuietly(Long userId, String title, String message, String type, Long orderId) {
        try {
            notificationClient.requestNotification(new NotificationRequest(userId, title, message, type, orderId, "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify user {} for order {}: {}", userId, orderId, ex.getMessage());
        }
    }

    private void notifyMaintenanceDroneOrderCreated(LockerOrder order) {
        try {
            ApiResponse<List<com.huynqb.laundrylocker.common.dto.UserSummary>> response =
                    userClient.getUsersByRole("MAINTENANCE");
            if (response == null || response.data() == null) {
                return;
            }
            for (var user : response.data()) {
                notificationClient.requestNotification(
                        new NotificationRequest(
                                user.id(),
                                "Có đơn drone mới",
                                "Đơn " + order.getOrderCode() + " đang chờ đội bay tiếp nhận.",
                                "DRONE_ORDER_CREATED",
                                order.getId(),
                                "ORDER"));
            }
        } catch (Exception ex) {
            log.warn("Could not notify maintenance users for drone order {}: {}", order.getId(), ex.getMessage());
        }
    }

    // Laundry lifecycle (collect/updateWeight/process/ready/returnOrder) đã gỡ
    // 2026-07-03 — dự án chỉ còn SEND/RENTAL. Trạng thái cũ COLLECTED/PROCESSING/
    // READY/RETURNED vẫn được chấp nhận ở complete()/checkout() cho dữ liệu lịch sử.

    // EXPIRED: đồ đã dời vào kho (G3) — nhân viên trao trả tại quầy và chốt đơn
    // qua checkout; ô đã được nhả từ lúc expire nên releaseBoxes dưới đây no-op.
    @Transactional
    public OrderResponse checkout(Long id, Long staffId, String note) {
        LockerOrder order = find(id);
        validateStatus(order, Set.of("READY", "RETURNED", "STORING", "EXPIRED"));
        releaseBoxes(order);
        order.setCompletedAt(LocalDateTime.now());
        order.setPinCode(null);
        return transition(order, "COMPLETED", staffId, order.getReceiveBoxId(),
                StringUtils.hasText(note) ? note : "Order checked out by staff");
    }

    @Transactional
    public OrderResponse complete(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwnerOrReceiver(order, userId);
        validateStatus(order, Set.of("STORING", "RETURNED"));
        assertPaidBeforePickup(order);
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            order.setExtraFee(order.getExtraFee().add(overtime));
            order.setTotalPrice(order.getTotalPrice().add(overtime));
        }
        releaseBoxes(order);
        order.setCompletedAt(LocalDateTime.now());
        order.setPinCode(null);
        return transition(order, "COMPLETED", userId, order.getReceiveBoxId(), "Customer completed pickup");
    }

    @Transactional
    public OrderResponse cancel(Long id, Integer reason, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        if (!CANCELABLE.contains(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Order cannot be canceled at this status");
        }
        assertDroneCancelable(order);
        order.setCancelReason(reason);
        order.setPinCode(null);
        releaseBoxes(order);
        refundPromotionUsages(order);
        return transition(order, "CANCELED", userId, null, "Order canceled");
    }

    @Transactional
    public OrderResponse resetPin(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        order.setPinCode(generatePinCode());
        order.setPinCodeIssuedAt(LocalDateTime.now());
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse delegate(Long id, Long userId, DelegateOrderRequest request) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        // Chỉ ủy quyền khi đồ đang nằm trong tủ chờ lấy
        validateStatus(order, Set.of("STORING", "RETURNED"));
        order.setPinCode(generatePinCode());
        order.setPinCodeIssuedAt(LocalDateTime.now());
        order.setReceiverPhone(request.phone());
        if (StringUtils.hasText(request.name())) {
            order.setReceiverName(request.name());
        }
        LockerOrder saved = orderRepository.save(order);
        addHistory(
                saved.getId(),
                saved.getStatus(),
                saved.getStatus(),
                userId,
                "Delegated pickup to " + request.phone()
                        + (StringUtils.hasText(request.note()) ? " - " + request.note() : ""));
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            saved.getUserId(),
                            "Order delegated",
                            "Order " + saved.getOrderCode() + " pickup delegated to " + request.phone()
                                    + ". New PIN issued.",
                            "ORDER_DELEGATED",
                            saved.getId(),
                            "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify delegation for order {}: {}", saved.getId(), ex.getMessage());
        }
        return toResponse(saved);
    }


    @Transactional
    public OrderResponse pickupStorage(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        boolean storageLike =
                "STORAGE".equalsIgnoreCase(order.getType())
                        || "STORAGE".equalsIgnoreCase(order.getServiceCategory())
                        || "RENTAL".equalsIgnoreCase(order.getType())
                        || "RENTAL".equalsIgnoreCase(order.getServiceCategory());
        if (!storageLike) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Only storage/rental orders can use pickup-storage");
        }
        validateStatus(order,
                "RENTAL".equalsIgnoreCase(order.getType()) || "RENTAL".equalsIgnoreCase(order.getServiceCategory())
                        ? Set.of("STORING", "RETURNED")
                        : Set.of("STORING", "INITIALIZED", "RETURNED"));
        assertPaidBeforeStorageCompletion(order);
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            order.setExtraFee(order.getExtraFee().add(overtime));
            order.setTotalPrice(order.getTotalPrice().add(overtime));
        }
        releaseBoxes(order);
        order.setCompletedAt(LocalDateTime.now());
        order.setPinCode(null);
        return transition(order, "COMPLETED", userId, order.getReceiveBoxId(),
                "RENTAL".equalsIgnoreCase(order.getType()) ? "Rental ended, cell released" : "Storage order picked up");
    }

    @Transactional
    public OrderResponse reportBoxFault(Long id, Long userId, String reason) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        validateStatus(order, Set.of("INITIALIZED", "STORING", "RETURNED"));
        Long boxId = activeBoxId(order);
        if (boxId == null) {
            throw new BusinessException("BOX_NOT_FOUND", "Order has no active box to report");
        }
        try {
            Map<String, String> body = reason == null || reason.isBlank()
                    ? Map.of()
                    : Map.of("reason", reason);
            lockerClient.reportFault(boxId, body, userId);
        } catch (Exception ex) {
            throw unwrapDownstreamError(ex, "BOX_FAULT_REPORT_FAILED", "Could not report fault for box " + boxId);
        }
        if (shouldAutoCancelAfterFault(order)) {
            order.setCancelReason(null);
            order.setPinCode(null);
            releaseBoxes(order);
            refundPromotionUsages(order);
            return transition(order, "CANCELED", userId, null, "Order auto-canceled after reporting reserved box fault");
        }
        addHistory(order.getId(), order.getStatus(), order.getStatus(), userId, "Customer reported a fault on the assigned box");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse reorder(Long originalOrderId, Long userId) {
        LockerOrder original = find(originalOrderId);
        assertOwner(original, userId);
        if (!Set.of("COMPLETED", "CANCELED").contains(original.getStatus())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Only completed or canceled orders can be reordered");
        }
        String type = original.getType() == null ? "" : original.getType().toUpperCase();
        // SEND/RENTAL carry box reservation + pricing rules that the generic create()
        // path below cannot reproduce (no cellType/hours field on CreateOrderRequest),
        // so they reorder through the same entry points a fresh booking would use.
        if ("SEND".equals(type)) {
            return createSend(
                    new SendOrderRequest(
                            original.getLockerId(),
                            null,
                            null,
                            original.getReceiverPhone(),
                            original.getReceiverName(),
                            original.getCustomerNote(),
                            null,
                            null),
                    userId);
        }
        if ("RENTAL".equals(type)) {
            return createRental(
                    new RentalOrderRequest(
                            original.getLockerId(),
                            null,
                            cellTypeOfRental(original),
                            resolveRentalDurationHours(original),
                            original.getCustomerNote(),
                            null),
                    userId);
        }
        List<OrderItemRequest> items =
                detailRepository.findByOrderId(originalOrderId).stream()
                        .map(d -> new OrderItemRequest(d.getServiceId(), d.getQuantity(), d.getDescription()))
                        .toList();
        return create(
                new CreateOrderRequest(
                        original.getUserId(),
                        original.getLockerId(),
                        null,
                        null,
                        original.getStoreId(),
                        original.getType(),
                        original.getServiceCategory(),
                        original.getReceiverId(),
                        original.getReceiverPhone(),
                        original.getReceiverName(),
                        original.getIntendedReceiveAt(),
                        original.getActualWeight(),
                        original.getCustomerNote(),
                        original.getDeliveryAddress(),
                        null,
                        items,
                        null,
                        null,
                        null));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public OrderSummary getSummary(Long id) {
        return toSummary(find(id));
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse status(Long id) {
        LockerOrder order = find(id);
        return new OrderStatusResponse(
                order.getId(),
                order.getStatus(),
                statusDescription(order.getStatus()),
                order.getPinCode(),
                order.getLockerId(),
                order.getReceiveBoxId() == null ? order.getSendBoxId() : order.getReceiveBoxId(),
                order.getIntendedReceiveAt(),
                "COMPLETED".equals(order.getStatus()),
                nextAction(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByCode(String orderCode) {
        return toResponse(
                orderRepository
                        .findByOrderCode(orderCode)
                        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found: " + orderCode)));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByPin(String pinCode) {
        LockerOrder order =
                orderRepository
                        .findByPinCode(pinCode)
                        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for PIN"));
        assertAccessCredentialActive(order);
        assertPaidBeforePickup(order);
        return toResponse(order);
    }

    // Single entry point for the cabinet screen: a 6-digit PIN or a signed QR
    // token are interchangeable access credentials.
    @Transactional(readOnly = true)
    public OrderResponse getByAccess(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("INVALID_ACCESS_CODE", "Access code is required");
        }
        String trimmed = code.trim();
        if (trimmed.startsWith(QrTokenService.PREFIX)) {
            Long orderId = qrTokenService.parseOrderId(trimmed);
            if (orderId == null) {
                throw new BusinessException("INVALID_ACCESS_CODE", "Malformed QR token");
            }
            LockerOrder order = find(orderId);
            if (!qrTokenService.matches(trimmed, order.getId(), order.getPinCode())) {
                throw new BusinessException("INVALID_ACCESS_CODE", "QR token expired or revoked");
            }
            assertAccessCredentialActive(order);
            assertPaidBeforePickup(order);
            return toResponse(order);
        }
        return getByPin(trimmed);
    }

    @Transactional
    public int sendPickupReminders() {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        List<LockerOrder> candidates = new java.util.ArrayList<>();
        candidates.addAll(orderRepository.findByStatusOrderByCreatedAtDesc("RETURNED"));
        candidates.addAll(orderRepository.findByStatusOrderByCreatedAtDesc("STORING"));
        for (LockerOrder order : candidates) {
            if (order.getPickupDeadline() == null || now.isBefore(order.getPickupDeadline())) {
                continue;
            }
            if (order.getLastReminderAt() != null
                    && order.getLastReminderAt().isAfter(now.minusMinutes(reminderCooldownMinutes))) {
                continue;
            }
            boolean rental = "RENTAL".equalsIgnoreCase(order.getType());
            notifyQuietly(order.getUserId(),
                    rental ? "Rental expired" : "Pickup overdue",
                    (rental
                            ? "Rental " + order.getOrderCode() + " expired at " + order.getPickupDeadline()
                            : "Order " + order.getOrderCode() + " passed its pickup deadline " + order.getPickupDeadline())
                            + ". Overtime fee applies.",
                    "ORDER_PICKUP_OVERDUE", order.getId());
            if (order.getReceiverId() != null) {
                notifyQuietly(order.getReceiverId(), "Pickup overdue",
                        "Parcel " + order.getOrderCode() + " is still waiting. Overtime fee applies.",
                        "ORDER_PICKUP_OVERDUE", order.getId());
            }
            order.setLastReminderAt(now);
            orderRepository.save(order);
            sent++;
        }
        if (sent > 0) {
            log.info("Pickup reminders sent: {}", sent);
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String status, Long staffId) {
        return list(status, null, staffId);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String status, String type, Long staffId) {
        if (staffId != null) {
            return orderRepository.findByStaffIdOrderByCreatedAtDesc(staffId).stream().map(this::toResponse).toList();
        }
        return orderRepository.findAll().stream()
                .filter(o -> !StringUtils.hasText(status) || status.equalsIgnoreCase(o.getStatus()))
                .filter(o -> !StringUtils.hasText(type) || type.equalsIgnoreCase(o.getType()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        List<LockerOrder> orders = orderRepository.findAll();
        var startOfToday = LocalDate.now().atStartOfDay();

        result.put("totalOrders", orders.size());
        result.put(
                "byStatus",
                orders.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        LockerOrder::getStatus, java.util.stream.Collectors.counting())));

        // Order-owned dashboard metrics (computed from this service's data only).
        long ordersToday =
                orders.stream()
                        .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(startOfToday))
                        .count();
        result.put("ordersToday", ordersToday);

        long pendingOrders =
                orders.stream()
                        .filter(
                                o -> {
                                    String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase();
                                    return !s.equals("COMPLETED") && !s.equals("CANCELED") && !s.equals("CANCELLED");
                                })
                        .count();
        result.put("pendingOrders", pendingOrders);

        BigDecimal totalRevenue =
                orders.stream()
                        .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                        .map(o -> o.getTotalPrice() == null ? BigDecimal.ZERO : o.getTotalPrice())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("totalRevenue", totalRevenue);

        BigDecimal revenueToday =
                orders.stream()
                        .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                        .filter(
                                o -> {
                                    var when = o.getCompletedAt() != null ? o.getCompletedAt() : o.getCreatedAt();
                                    return when != null && !when.isBefore(startOfToday);
                                })
                        .map(o -> o.getTotalPrice() == null ? BigDecimal.ZERO : o.getTotalPrice())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("revenueToday", revenueToday);

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> revenue() {
        BigDecimal total =
                orderRepository.findAll().stream()
                        .filter(order -> "COMPLETED".equalsIgnoreCase(order.getStatus()))
                        .map(LockerOrder::getTotalPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("totalRevenue", total);
    }

    @Transactional
    public OrderRatingResponse rate(Long orderId, OrderRatingRequest request, Long userId) {
        LockerOrder order = find(orderId);
        assertOwner(order, userId);
        validateStatus(order, Set.of("COMPLETED"));
        OrderRating rating = ratingRepository.findByOrderId(orderId).orElseGet(OrderRating::new);
        rating.setOrderId(orderId);
        rating.setUserId(userId);
        rating.setRating(request.rating());
        rating.setComment(request.comment());
        return toRating(ratingRepository.save(rating));
    }

    @Transactional(readOnly = true)
    public List<OrderRatingResponse> myRatings(Long userId) {
        return ratingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toRating).toList();
    }

    @Transactional(readOnly = true)
    public OrderRatingResponse rating(Long orderId) {
        return ratingRepository.findByOrderId(orderId).map(this::toRating).orElseThrow(() -> new NotFoundException("OrderRating", orderId));
    }

    @Transactional
    public OrderComplaintResponse complain(Long orderId, OrderComplaintRequest request, Long userId) {
        LockerOrder order = find(orderId);
        assertOwner(order, userId);
        OrderComplaint complaint = new OrderComplaint();
        complaint.setOrderId(orderId);
        complaint.setUserId(userId);
        complaint.setType(StringUtils.hasText(request.type()) ? request.type().toUpperCase() : "OTHER");
        complaint.setDescription(request.description());
        return toComplaint(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<OrderComplaintResponse> complaints(Long orderId) {
        return complaintRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(this::toComplaint).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderComplaintResponse> myComplaints(Long userId) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toComplaint).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderTimelineEvent> timeline(Long orderId) {
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(h -> new OrderTimelineEvent(h.getOldStatus(), h.getNewStatus(), h.getChangedByUserId(), h.getNote(), h.getCreatedAt()))
                .toList();
    }

    @Transactional
    public Promotion createPromotion(PromotionRequest request, Long createdByUserId) {
        Promotion promotion = new Promotion();
        promotion.setCode(request.code().toUpperCase());
        promotion.setName(request.name());
        promotion.setDiscountType(StringUtils.hasText(request.discountType()) ? request.discountType().toUpperCase() : "FIXED_AMOUNT");
        promotion.setDiscountValue(request.discountValue() == null ? BigDecimal.ZERO : request.discountValue());
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setMinOrderAmount(request.minOrderAmount());
        promotion.setStackable(Boolean.TRUE.equals(request.stackable()));
        promotion.setStatus(StringUtils.hasText(request.status()) ? request.status().toUpperCase() : "ACTIVE");
        promotion.setStartAt(request.startAt());
        promotion.setEndAt(request.endAt());
        promotion.setLockerId(request.lockerId());
        promotion.setTotalUsageLimit(request.totalUsageLimit());
        promotion.setPerUserLimit(request.perUserLimit());
        promotion.setCreatedByUserId(createdByUserId);
        return promotionRepository.save(promotion);
    }

    @Transactional(readOnly = true)
    public List<Promotion> promotions() {
        return promotionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Promotion> promotions(String code, String status) {
        String q = code == null ? null : code.toLowerCase();
        return promotionRepository.findAll().stream()
                .filter(p -> !StringUtils.hasText(status) || status.equalsIgnoreCase(p.getStatus()))
                .filter(p -> q == null || p.getCode().toLowerCase().contains(q)
                        || (p.getName() != null && p.getName().toLowerCase().contains(q)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Promotion promotion(Long id) {
        return promotionRepository.findById(id).orElseThrow(() -> new NotFoundException("Promotion", id));
    }

    @Transactional
    public Promotion updatePromotion(Long id, PromotionRequest request) {
        Promotion promotion = promotion(id);
        promotion.setCode(request.code().toUpperCase());
        promotion.setName(request.name());
        promotion.setDiscountType(StringUtils.hasText(request.discountType()) ? request.discountType().toUpperCase() : promotion.getDiscountType());
        promotion.setDiscountValue(request.discountValue() == null ? BigDecimal.ZERO : request.discountValue());
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setMinOrderAmount(request.minOrderAmount());
        promotion.setStackable(Boolean.TRUE.equals(request.stackable()));
        promotion.setStatus(StringUtils.hasText(request.status()) ? request.status().toUpperCase() : promotion.getStatus());
        promotion.setStartAt(request.startAt());
        promotion.setEndAt(request.endAt());
        promotion.setLockerId(request.lockerId());
        promotion.setTotalUsageLimit(request.totalUsageLimit());
        promotion.setPerUserLimit(request.perUserLimit());
        return promotionRepository.save(promotion);
    }

    /// Xóa cứng một mã đã có lượt dùng/đã được lưu vào ví sẽ mất dữ liệu lịch
    /// sử (audit khuyến mãi, số tiền đã giảm cho từng đơn). Chặn và yêu cầu
    /// chuyển INACTIVE thay vì xóa.
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = promotion(id);
        if (promotionUsageRepository.existsByPromotionId(id)
                || promotionClaimRepository.existsByPromotionId(id)) {
            throw new BusinessException(
                    "PROMOTION_HAS_HISTORY",
                    "Mã đã có lượt sử dụng hoặc đã được lưu vào ví — chuyển trạng thái INACTIVE thay vì xóa");
        }
        promotionRepository.delete(promotion);
    }

    @Transactional(readOnly = true)
    public List<Promotion> promotionsByStatus(String status) {
        return promotionRepository.findByStatus(status.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<Promotion> searchPromotions(String keyword) {
        String lower = keyword == null ? "" : keyword.toLowerCase();
        return promotionRepository.findAll().stream()
                .filter(p -> p.getCode().toLowerCase().contains(lower) || p.getName().toLowerCase().contains(lower))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validatePromotion(String code) {
        return validatePromotion(code, null, null);
    }

    /// Validate mã cho client: check hiệu lực + scope theo [lockerId] + lượt
    /// dùng của [userId] (2 tham số null thì bỏ qua check tương ứng). Trả DTO
    /// đã lọc thay vì entity thô (không lộ createdByUserId...).
    public Map<String, Object> validatePromotion(String code, Long lockerId, Long userId) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code).orElse(null);
        String reason = promotionIneligibleReason(promotion, lockerId, userId, null);
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("valid", reason == null);
        if (reason != null) {
            result.put("reason", "Mã " + code.toUpperCase() + ": " + reason);
        }
        result.put("promotion", promotion == null ? null : promotionSummary(promotion));
        return result;
    }

    /// Các field promotion an toàn để trả cho client (mobile PromoCodeField đọc
    /// discountType/discountValue/maxDiscountAmount/minOrderAmount).
    private Map<String, Object> promotionSummary(Promotion promotion) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", promotion.getId());
        summary.put("code", promotion.getCode());
        summary.put("name", promotion.getName());
        summary.put("description", promotion.getDescription());
        summary.put("imageUrl", promotion.getImageUrl());
        summary.put("discountType", promotion.getDiscountType());
        summary.put("discountValue", promotion.getDiscountValue());
        summary.put("maxDiscountAmount", promotion.getMaxDiscountAmount());
        summary.put("minOrderAmount", promotion.getMinOrderAmount());
        summary.put("stackable", promotion.getStackable());
        summary.put("status", promotion.getStatus());
        summary.put("startAt", promotion.getStartAt());
        summary.put("endAt", promotion.getEndAt());
        summary.put("lockerId", promotion.getLockerId());
        summary.put("totalUsageLimit", promotion.getTotalUsageLimit());
        summary.put("perUserLimit", promotion.getPerUserLimit());
        summary.put("usageCount", promotion.getUsageCount());
        return summary;
    }

    /// User lưu một mã vào "ví voucher". Idempotent: đã lưu rồi thì trả claim cũ.
    @Transactional
    public Map<String, Object> claimPromotion(Long promotionId, Long userId) {
        Promotion promotion = promotion(promotionId);
        if (!promotion.activeNow()) {
            throw new BusinessException("PROMOTION_INVALID", "Khuyến mãi không còn hiệu lực");
        }
        PromotionClaim claim =
                promotionClaimRepository
                        .findByPromotionIdAndUserId(promotionId, userId)
                        .orElseGet(() -> {
                            PromotionClaim created = new PromotionClaim();
                            created.setPromotionId(promotionId);
                            created.setUserId(userId);
                            return promotionClaimRepository.save(created);
                        });
        return voucherView(claim, promotion);
    }

    /// "Ví voucher" của user: các mã đã lưu kèm trạng thái SAVED/USED/EXPIRED.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myVouchers(Long userId, String status) {
        return promotionClaimRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(claim -> {
                    Promotion promotion =
                            promotionRepository.findById(claim.getPromotionId()).orElse(null);
                    return promotion == null ? null : voucherView(claim, promotion);
                })
                .filter(java.util.Objects::nonNull)
                .filter(v -> !StringUtils.hasText(status) || status.equalsIgnoreCase((String) v.get("status")))
                .toList();
    }

    private Map<String, Object> voucherView(PromotionClaim claim, Promotion promotion) {
        // SAVED nhưng khuyến mãi đã hết hiệu lực -> hiển thị EXPIRED.
        String status = claim.getStatus();
        if ("SAVED".equals(status) && !promotion.activeNow()) {
            status = "EXPIRED";
        }
        Map<String, Object> view = new HashMap<>(promotionSummary(promotion));
        view.put("id", claim.getId());
        view.put("promotionId", promotion.getId());
        view.put("status", status);
        view.put("savedAt", claim.getCreatedAt());
        view.put("usedAt", claim.getUsedAt());
        return view;
    }

    @Transactional(readOnly = true)
    public List<Promotion> activePromotions() {
        return promotionRepository.findByStatus("ACTIVE").stream().filter(Promotion::activeNow).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> storeRatings(Long storeId) {
        return ratingRepository.findAll().stream()
                .filter(rating -> find(rating.getOrderId()).getStoreId().equals(storeId))
                .map(
                        rating -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("id", rating.getId());
                            result.put("orderId", rating.getOrderId());
                            result.put("userId", rating.getUserId());
                            result.put("rating", rating.getRating());
                            result.put("comment", rating.getComment());
                            result.put("createdAt", rating.getCreatedAt());
                            return result;
                        })
                .toList();
    }

    // Reservation TTL: an unconfirmed (INITIALIZED) order keeps its send cell in
    // RESERVED. If the customer never drops their items within the hold window,
    // sweep the order to CANCELED *and release the cell* — otherwise the cell
    // stays stuck RESERVED forever (the old version only flipped the status).
    @Transactional
    public Map<String, Object> autoCancelUnconfirmedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(autoCancelHours);
        int canceled = 0;
        for (LockerOrder order : orderRepository.findByStatusOrderByCreatedAtDesc("INITIALIZED")) {
            if (order.getCreatedAt() == null || order.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            order.setPinCode(null);
            releaseBoxes(order);
            transition(
                    order,
                    "CANCELED",
                    null,
                    null,
                    "Tự hủy: không xác nhận bỏ đồ trong " + autoCancelHours + " giờ; đã nhả ô");
            canceled++;
        }
        if (canceled > 0) {
            log.info("Auto-canceled unconfirmed orders and released their cells: {}", canceled);
        }
        return Map.of("canceledOrders", canceled);
    }

    @Transactional
    public Map<String, Object> releaseBoxesAfterCompletion() {
        orderRepository.findByStatusOrderByCreatedAtDesc("COMPLETED").forEach(this::releaseBoxes);
        return Map.of("status", "completed");
    }

    // G3: nhắc + tính phí quá hạn là chưa đủ — ô bị chiếm vô thời hạn tới khi có
    // lệnh complete. Sau `overdue-release-hours` giờ kể từ pickupDeadline, chốt
    // phí quá hạn, chuyển đơn sang EXPIRED (đồ dời vào kho, nhận lại qua nhân
    // viên bằng checkout) và nhả ô. PIN bị thu hồi vì ô có thể cấp cho đơn khác.
    @Transactional
    public Map<String, Object> releaseOverdueOrders() {
        if (overdueReleaseHours <= 0) {
            return Map.of("expiredOrders", 0);
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(overdueReleaseHours);
        int expired = 0;
        List<LockerOrder> candidates = new ArrayList<>();
        candidates.addAll(orderRepository.findByStatusOrderByCreatedAtDesc("STORING"));
        candidates.addAll(orderRepository.findByStatusOrderByCreatedAtDesc("RETURNED"));
        for (LockerOrder order : candidates) {
            if (order.getPickupDeadline() == null || order.getPickupDeadline().isAfter(cutoff)) {
                continue;
            }
            BigDecimal overtime = calculatePickupOvertimeFee(order);
            if (overtime.compareTo(BigDecimal.ZERO) > 0) {
                order.setExtraFee(order.getExtraFee().add(overtime));
                order.setTotalPrice(order.getTotalPrice().add(overtime));
            }
            releaseBoxes(order);
            // Ô đã trả về pool nên tham chiếu box phải cắt — tránh double-release
            // giải phóng nhầm ô đã được cấp cho đơn khác ở các lệnh sau này.
            order.setSendBoxId(null);
            order.setReceiveBoxId(null);
            order.setPinCode(null);
            transition(order, "EXPIRED", null, null,
                    "Quá hạn lấy hàng quá " + overdueReleaseHours
                            + " giờ: đồ chuyển vào kho lưu trữ, ô đã giải phóng");
            notifyQuietly(order.getUserId(), "Đồ đã chuyển vào kho",
                    "Đơn " + order.getOrderCode()
                            + " quá hạn lấy hàng. Đồ đã được chuyển vào kho lưu trữ và ô tủ được giải phóng."
                            + " Vui lòng liên hệ nhân viên để nhận lại đồ.",
                    "ORDER_EXPIRED", order.getId());
            if (order.getReceiverId() != null) {
                notifyQuietly(order.getReceiverId(), "Đồ đã chuyển vào kho",
                        "Kiện hàng " + order.getOrderCode()
                                + " quá hạn lấy. Đồ đã chuyển vào kho lưu trữ; liên hệ nhân viên để nhận lại.",
                        "ORDER_EXPIRED", order.getId());
            }
            expired++;
        }
        if (expired > 0) {
            log.info("Overdue orders moved to storage (EXPIRED) and cells released: {}", expired);
        }
        return Map.of("expiredOrders", expired);
    }

    // G4: trạng thái ô bên locker-service là bản sao best-effort của đơn
    // (occupy/release nuốt lỗi) nên có thể lệch. Đối soát hai chiều:
    //  - Ô RESERVED/OCCUPIED mà không đơn hoạt động nào tham chiếu → nhả về
    //    AVAILABLE. Riêng RESERVED còn hạn giữ chỗ thì bỏ qua (có thể là đơn
    //    vừa tạo chưa kịp thấy trong snapshot; sweep TTL của locker-service
    //    sẽ xử lý nếu thật sự mồ côi).
    //  - Đơn hoạt động mà ô lại AVAILABLE → giữ/chiếm lại cho khớp (đọc lại
    //    đơn ngay trước khi sửa để tránh đơn vừa hoàn tất).
    @Transactional
    public Map<String, Object> reconcileBoxStates() {
        List<Map<String, Object>> boxes;
        try {
            boxes = lockerClient.listBoxes().data();
        } catch (Exception ex) {
            log.warn("Box reconcile skipped, locker-service unreachable: {}", ex.getMessage());
            return Map.of("skipped", true);
        }
        if (boxes == null) {
            return Map.of("skipped", true);
        }

        List<LockerOrder> activeOrders = new ArrayList<>();
        for (String status : List.of("INITIALIZED", "STORING", "RETURNED")) {
            activeOrders.addAll(orderRepository.findByStatusOrderByCreatedAtDesc(status));
        }
        java.util.Set<Long> heldBoxIds = new java.util.HashSet<>();
        for (LockerOrder order : activeOrders) {
            if (order.getSendBoxId() != null) {
                heldBoxIds.add(order.getSendBoxId());
            }
            if (order.getReceiveBoxId() != null) {
                heldBoxIds.add(order.getReceiveBoxId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> boxStatusById = new HashMap<>();
        int released = 0;
        for (Map<String, Object> box : boxes) {
            Long boxId = toLong(box.get("id"));
            String status = box.get("status") == null ? "" : String.valueOf(box.get("status"));
            if (boxId == null) {
                continue;
            }
            boxStatusById.put(boxId, status);
            if (heldBoxIds.contains(boxId)) {
                continue;
            }
            boolean orphanOccupied = "OCCUPIED".equalsIgnoreCase(status);
            boolean orphanReserved =
                    "RESERVED".equalsIgnoreCase(status) && reservedHoldExpired(box.get("reservedUntil"), now);
            if (!orphanOccupied && !orphanReserved) {
                continue;
            }
            try {
                lockerClient.releaseBox(boxId);
                released++;
                log.warn("Reconcile: released orphan {} box {}", status, boxId);
            } catch (Exception ex) {
                log.warn("Reconcile: could not release box {}: {}", boxId, ex.getMessage());
            }
        }

        int reclaimed = 0;
        for (LockerOrder order : activeOrders) {
            Long boxId = order.getReceiveBoxId() != null ? order.getReceiveBoxId() : order.getSendBoxId();
            if (boxId == null || !"AVAILABLE".equalsIgnoreCase(boxStatusById.get(boxId))) {
                continue;
            }
            String freshStatus =
                    orderRepository.findById(order.getId()).map(LockerOrder::getStatus).orElse("");
            try {
                if ("INITIALIZED".equals(freshStatus)) {
                    lockerClient.reserveBox(boxId, null);
                } else if ("STORING".equals(freshStatus) || "RETURNED".equals(freshStatus)) {
                    lockerClient.occupyBox(boxId);
                } else {
                    continue;
                }
                reclaimed++;
                log.warn("Reconcile: re-held AVAILABLE box {} for active order {} ({})",
                        boxId, order.getId(), freshStatus);
            } catch (Exception ex) {
                log.warn("Reconcile: could not re-hold box {} for order {}: {}",
                        boxId, order.getId(), ex.getMessage());
            }
        }

        if (released > 0 || reclaimed > 0) {
            log.info("Box reconcile done: released={}, reclaimed={}", released, reclaimed);
        }
        return Map.of("releasedBoxes", released, "reclaimedBoxes", reclaimed);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // Hạn giữ chỗ đã qua (hoặc không có — dữ liệu cũ) thì RESERVED mồ côi mới
    // được nhả; định dạng lạ coi như còn hạn để không release nhầm.
    private boolean reservedHoldExpired(Object reservedUntil, LocalDateTime now) {
        if (reservedUntil == null) {
            return true;
        }
        try {
            return LocalDateTime.parse(String.valueOf(reservedUntil)).isBefore(now);
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pickupReminders() {
        long count =
                orderRepository.findByStatusOrderByCreatedAtDesc("RETURNED").stream()
                        .filter(order -> order.getPickupDeadline() != null)
                        .count();
        return Map.of("reminders", count);
    }

    private OrderResponse transition(
            LockerOrder order, String newStatus, Long actorId, Long receiveBoxId, String note) {
        String oldStatus = order.getStatus();
        order.setStatus(newStatus.toUpperCase());
        if (actorId != null && !"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
            order.setStaffId(actorId);
        }
        if (receiveBoxId != null) {
            order.setReceiveBoxId(receiveBoxId);
        }
        LockerOrder saved = orderRepository.save(order);
        addHistory(saved.getId(), oldStatus, saved.getStatus(), actorId, note);
        publishStatusChanged(saved, oldStatus);
        notifyStatus(saved, oldStatus);
        return toResponse(saved);
    }

    private int resolveRentalDurationHours(LockerOrder order) {
        if (order.getRentalDurationHours() != null && order.getRentalDurationHours() > 0) {
            return clampRentalDurationHours(order.getRentalDurationHours());
        }
        if (order.getCreatedAt() != null && order.getPickupDeadline() != null) {
            long inferred = ChronoUnit.HOURS.between(order.getCreatedAt(), order.getPickupDeadline());
            if (inferred > 0) {
                return clampRentalDurationHours(Math.toIntExact(inferred));
            }
        }
        return 1;
    }

    private int clampRentalDurationHours(int hours) {
        return Math.max(1, Math.min(720, hours));
    }

    private void assertAccessCredentialActive(LockerOrder order) {
        if (!Set.of("INITIALIZED", "STORING", "RETURNED").contains(order.getStatus())) {
            throw new BusinessException("INVALID_ACCESS_CODE", "Access code is no longer active");
        }
    }

    private Long resolveAndReserveSendBox(CreateOrderRequest request) {
        Long boxId = request.sendBoxId();
        if ((boxId == null) && request.boxIds() != null && !request.boxIds().isEmpty()) {
            boxId = request.boxIds().iterator().next();
        }
        if (boxId != null) {
            try {
                lockerClient.reserveBox(boxId, null);
            } catch (Exception ex) {
                throw unwrapDownstreamError(ex, "BOX_RESERVE_FAILED", "Could not reserve box " + boxId);
            }
        }
        return boxId;
    }

    /**
     * Locker-service business errors (e.g. DRONE_CELL_RESTRICTED) arrive here as a
     * FeignException wrapped in the circuit breaker's NoFallbackAvailableException,
     * which the global handler would surface as a blank 500. Unwrap the original
     * 4xx code/message so the client gets the real reason.
     */
    private RuntimeException unwrapDownstreamError(Exception ex, String fallbackCode, String fallbackMessage) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof feign.FeignException feignEx
                    && feignEx.status() >= 400
                    && feignEx.status() < 500) {
                try {
                    com.fasterxml.jackson.databind.JsonNode body =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(feignEx.contentUTF8());
                    return new BusinessException(
                            body.path("code").asText(fallbackCode),
                            body.path("message").asText(fallbackMessage));
                } catch (Exception parseFailure) {
                    return new BusinessException(fallbackCode, fallbackMessage);
                }
            }
            current = current.getCause();
        }
        return ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
    }

    private BigDecimal saveDetailsAndCalculate(Long orderId, CreateOrderRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return saveItemDetails(orderId, request.items(), request.estimatedWeight());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal saveItemDetails(Long orderId, List<OrderItemRequest> items, BigDecimal defaultQuantity) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            BigDecimal quantity = item.quantity() == null ? (defaultQuantity == null ? BigDecimal.ONE : defaultQuantity) : item.quantity();
            BigDecimal price = BigDecimal.valueOf(5000).multiply(quantity); // Base storage fee
            saveDetail(orderId, item.serviceId(), quantity, price, item.description());
            total = total.add(price);
        }
        return total;
    }

    private void saveDetail(Long orderId, Long serviceId, BigDecimal quantity, BigDecimal price, String description) {
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(orderId);
        detail.setServiceId(serviceId);
        detail.setQuantity(quantity);
        detail.setPrice(price == null ? BigDecimal.ZERO : price);
        detail.setDescription(description);
        detailRepository.save(detail);
    }

    /// Áp mã vào đơn — mã không hợp lệ thì THROW (400 PROMOTION_INVALID) thay vì
    /// bỏ qua im lặng để khách không bị mất giảm giá mà không hay biết.
    /// Enforce: hiệu lực, scope theo tủ, đơn tối thiểu, trần tổng lượt và trần
    /// lượt mỗi user; ghi promotion_usages (hoàn khi hủy đơn) và đánh dấu
    /// voucher đã lưu (claim) thành USED.
    private void applyPromotion(LockerOrder order, String promotionCode, List<String> promotionCodes) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.hasText(promotionCode)) {
            codes.add(promotionCode);
        }
        if (promotionCodes != null) {
            promotionCodes.stream().filter(StringUtils::hasText).forEach(codes::add);
        }
        if (codes.isEmpty()) {
            return;
        }
        BigDecimal discount = BigDecimal.ZERO;
        List<String> applied = new ArrayList<>();
        for (String code : codes) {
            Promotion promotion = promotionRepository.findByCodeIgnoreCase(code).orElse(null);
            String reason = promotionIneligibleReason(
                    promotion, order.getLockerId(), order.getUserId(), order.getTotalPrice());
            if (reason != null) {
                throw new BusinessException("PROMOTION_INVALID", "Mã " + code.toUpperCase() + ": " + reason);
            }
            if (!applied.isEmpty() && !Boolean.TRUE.equals(promotion.getStackable())) {
                throw new BusinessException(
                        "PROMOTION_INVALID", "Mã " + code.toUpperCase() + ": không thể dùng kèm mã khác");
            }
            BigDecimal amount = discountAmount(promotion, order.getTotalPrice());
            discount = discount.add(amount);
            applied.add(promotion.getCode());
            promotion.setUsageCount(promotion.getUsageCount() + 1);

            PromotionUsage usage = new PromotionUsage();
            usage.setPromotionId(promotion.getId());
            usage.setUserId(order.getUserId());
            usage.setOrderId(order.getId());
            usage.setDiscountApplied(amount);
            promotionUsageRepository.save(usage);

            promotionClaimRepository
                    .findByPromotionIdAndUserId(promotion.getId(), order.getUserId())
                    .filter(claim -> "SAVED".equals(claim.getStatus()))
                    .ifPresent(claim -> {
                        claim.setStatus("USED");
                        claim.setUsedAt(LocalDateTime.now());
                        promotionClaimRepository.save(claim);
                    });
        }
        if (!applied.isEmpty()) {
            order.setPromotionCode(applied.get(0));
            order.setAppliedPromotionCodes(String.join(",", applied));
            order.setDiscount(discount);
            order.setTotalPrice(order.getTotalPrice().subtract(discount).max(BigDecimal.ZERO));
        }
    }

    /// Lý do mã không dùng được (null = hợp lệ). [lockerId]/[userId] null thì
    /// bỏ qua check tương ứng (validate ẩn danh vẫn check được hiệu lực).
    private String promotionIneligibleReason(
            Promotion promotion, Long lockerId, Long userId, BigDecimal orderTotal) {
        if (promotion == null || !promotion.activeNow()) {
            return "không hợp lệ hoặc đã hết hạn";
        }
        if (promotion.getLockerId() != null && lockerId != null
                && !promotion.getLockerId().equals(lockerId)) {
            return "chỉ áp dụng tại một tủ/kiosk khác";
        }
        if (orderTotal != null && promotion.getMinOrderAmount() != null
                && orderTotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return "đơn chưa đạt giá trị tối thiểu";
        }
        if (promotion.getTotalUsageLimit() != null
                && promotion.getUsageCount() >= promotion.getTotalUsageLimit()) {
            return "đã hết lượt sử dụng";
        }
        if (promotion.getPerUserLimit() != null && userId != null
                && promotionUsageRepository.countByPromotionIdAndUserId(promotion.getId(), userId)
                >= promotion.getPerUserLimit()) {
            return "bạn đã dùng hết số lần cho phép";
        }
        return null;
    }

    /// Hủy đơn thì hoàn lượt sử dụng mã: xóa promotion_usages của đơn, giảm
    /// usage_count và trả voucher đã lưu về SAVED để khách dùng lại.
    private void refundPromotionUsages(LockerOrder order) {
        List<PromotionUsage> usages = promotionUsageRepository.findByOrderId(order.getId());
        for (PromotionUsage usage : usages) {
            promotionRepository.findById(usage.getPromotionId()).ifPresent(promotion -> {
                promotion.setUsageCount(Math.max(0, promotion.getUsageCount() - 1));
                promotionRepository.save(promotion);
            });
            promotionClaimRepository
                    .findByPromotionIdAndUserId(usage.getPromotionId(), usage.getUserId())
                    .filter(claim -> "USED".equals(claim.getStatus()))
                    .ifPresent(claim -> {
                        claim.setStatus("SAVED");
                        claim.setUsedAt(null);
                        promotionClaimRepository.save(claim);
                    });
            promotionUsageRepository.delete(usage);
        }
    }

    private BigDecimal discountAmount(Promotion promotion, BigDecimal orderTotal) {
        BigDecimal discount =
                "PERCENTAGE".equalsIgnoreCase(promotion.getDiscountType())
                        ? orderTotal.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        : promotion.getDiscountValue();
        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        return discount.min(orderTotal);
    }

    private void releaseBoxes(LockerOrder order) {
        Set<Long> boxIds = new LinkedHashSet<>();
        boxIds.add(order.getSendBoxId());
        boxIds.add(order.getReceiveBoxId());
        boxIds.add(order.getReservedBoxId());
        for (Long boxId : boxIds) {
            if (boxId != null) {
                lockerClient.releaseBox(boxId);
            }
        }
    }

    // Cell state is a best-effort mirror of the physical cabinet: an occupy
    // failure (e.g. legacy box already OCCUPIED) must not abort the order flow.
    private void occupyBoxQuietly(Long boxId) {
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.occupyBox(boxId);
        } catch (Exception ex) {
            log.warn("Could not occupy box {}: {}", boxId, ex.getMessage());
        }
    }

    private BigDecimal calculatePickupOvertimeFee(LockerOrder order) {
        if (order.getPickupDeadline() == null || !LocalDateTime.now().isAfter(order.getPickupDeadline())) {
            return BigDecimal.ZERO;
        }
        long hours = ChronoUnit.HOURS.between(order.getPickupDeadline(), LocalDateTime.now());
        BigDecimal raw = BigDecimal.valueOf(Math.max(0, hours) * overtimeFeePerHour);
        BigDecimal percentageCap =
                order.getTotalPrice().multiply(BigDecimal.valueOf(maxOvertimePercent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return raw.min(BigDecimal.valueOf(maxOvertimeFee)).min(percentageCap);
    }

    private void validateStatus(LockerOrder order, Set<String> statuses) {
        if (!statuses.contains(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Order status does not allow this action");
        }
    }

    /**
     * Chặn khách bỏ hàng / bắt đầu thuê khi đơn có phí (&gt;0) mà chưa thanh toán.
     * Đơn miễn phí (totalPrice = 0/null) luôn được phép. Có thể tắt bằng cấu hình
     * {@code app.order.require-payment-before-drop=false}.
     */
    private void assertPaidBeforeDrop(LockerOrder order) {
        if (!requirePaymentBeforeDrop) {
            return;
        }
        BigDecimal total = order.getTotalPrice();
        boolean hasFee = total != null && total.compareTo(BigDecimal.ZERO) > 0;
        boolean paid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
        if (hasFee && !paid) {
            throw new BusinessException(
                    "ORDER_UNPAID",
                    "Vui lòng thanh toán đơn trước khi bỏ hàng vào tủ.");
        }
    }

    private void assertPaidBeforeStorageCompletion(LockerOrder order) {
        if (!requirePaymentBeforeDrop) {
            return;
        }
        BigDecimal total = order.getTotalPrice();
        boolean hasFee = total != null && total.compareTo(BigDecimal.ZERO) > 0;
        boolean paid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
        if (hasFee && !paid) {
            throw new BusinessException(
                    "ORDER_UNPAID",
                    "Vui lòng thanh toán đơn trước khi kết thúc thuê/trả ô.");
        }
    }

    private void assertPaidBeforePickup(LockerOrder order) {
        if (!"DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return;
        }
        if (!"READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage())) {
            return;
        }
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new BusinessException(
                    "DRONE_PAYMENT_REQUIRED_BEFORE_PICKUP",
                    "Vui lòng thanh toán đơn drone trước khi mở tủ nhận hàng.");
        }
    }

    private void assertDroneCancelable(LockerOrder order) {
        if (!"DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return;
        }
        if (!"AWAITING_DISPATCH".equalsIgnoreCase(order.getDeliveryStage())) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID",
                    "Drone order can only be canceled before maintenance accepts it.");
        }
    }

    private void assertOwner(LockerOrder order, Long userId) {
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
        }
    }

    private Long activeBoxId(LockerOrder order) {
        return firstNonNull(firstNonNull(order.getSendBoxId(), order.getReceiveBoxId()), order.getReservedBoxId());
    }

    private boolean shouldAutoCancelAfterFault(LockerOrder order) {
        return "INITIALIZED".equalsIgnoreCase(order.getStatus())
                && "UNPAID".equalsIgnoreCase(order.getPaymentStatus());
    }

    private void assertOwnerOrReceiver(LockerOrder order, Long userId) {
        if (userId == null
                || userId.equals(order.getUserId())
                || userId.equals(order.getReceiverId())) {
            return;
        }
        throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
    }

    private LockerOrder find(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    private OrderSummary toSummary(LockerOrder order) {
        return new OrderSummary(order.getId(), order.getUserId(), order.getStatus(), order.getTotalPrice());
    }

    private OrderResponse toResponse(LockerOrder order) {
        List<OrderDetailResponse> details =
                detailRepository.findByOrderId(order.getId()).stream()
                        .map(d -> new OrderDetailResponse(d.getServiceId(), d.getQuantity(), d.getPrice(), d.getDescription()))
                        .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getUserId(),
                order.getReceiverId(),
                order.getLockerId(),
                order.getSendBoxId(),
                order.getReceiveBoxId(),
                order.getStoreId(),
                order.getStaffId(),
                order.getType(),
                order.getServiceCategory(),
                order.getStatus(),
                order.getDeliveryStage(),
                order.getPinCode(),
                qrTokenService.issue(order.getId(), order.getPinCode()),
                order.getActualWeight(),
                order.getWeightUnit(),
                order.getExtraFee(),
                order.getDiscount(),
                order.getTotalPrice(),
                order.getOriginalPrice(),
                order.getPromotionCode(),
                order.getAppliedPromotionCodes(),
                nextAction(order),
                nextActionMessage(order),
                paymentRequired(order),
                order.getPaymentStatus(),
                order.getPickupDeadline() != null && LocalDateTime.now().isAfter(order.getPickupDeadline()),
                order.getPickupDeadline(),
                order.getReturnedAt(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                details);
    }

    private DroneDeliveryOrderResponse toDroneDeliveryResponse(LockerOrder order) {
        return new DroneDeliveryOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getUserId(),
                firstNonNull(order.getReceiverUserId(), order.getReceiverId()),
                firstNonNull(order.getDestinationLockerId(), order.getLockerId()),
                firstNonNull(order.getReservedBoxId(), order.getSendBoxId()),
                order.getType(),
                order.getStatus(),
                order.getDeliveryStage(),
                order.getPaymentStatus(),
                order.getParcelWeightGrams(),
                order.getDescription(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getFulfillmentMode(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private OrderRatingResponse toRating(OrderRating rating) {
        return new OrderRatingResponse(rating.getId(), rating.getOrderId(), rating.getUserId(), rating.getRating(), rating.getComment(), rating.getCreatedAt());
    }

    private OrderComplaintResponse toComplaint(OrderComplaint complaint) {
        return new OrderComplaintResponse(
                complaint.getId(), complaint.getOrderId(), complaint.getUserId(), complaint.getType(), complaint.getDescription(), complaint.getStatus(), complaint.getCreatedAt());
    }

    private String nextAction(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            String deliveryStage = order.getDeliveryStage() == null ? "" : order.getDeliveryStage().toUpperCase();
            if ("READY_FOR_PICKUP".equals(deliveryStage)) {
                return "PAID".equalsIgnoreCase(order.getPaymentStatus()) ? "PICKUP" : "PAY_BEFORE_PICKUP";
            }
            return switch (order.getStatus()) {
                case "AWAITING_DISPATCH" -> "WAIT_FOR_DRONE";
                case "COMPLETED" -> "DONE";
                case "CANCELED" -> "CANCELED";
                default -> "UNKNOWN";
            };
        }
        return switch (order.getStatus()) {
            case "INITIALIZED" -> "PAY_AND_DROP";
            case "STORING" -> "PICKUP";
            case "COMPLETED" -> "DONE";
            case "CANCELED" -> "CANCELED";
            case "EXPIRED" -> "CONTACT_STAFF";
            default -> "UNKNOWN";
        };
    }

    private String nextActionMessage(LockerOrder order) {
        return switch (nextAction(order)) {
            case "PAY_AND_DROP" -> "Pay and place storage items in locker.";
            case "WAIT_FOR_DRONE" -> "Maintenance will accept and launch the drone delivery.";
            case "PAY_BEFORE_PICKUP" -> "Pay for the drone delivery before opening the locker.";
            case "PICKUP" -> "Pick up items from locker.";
            case "CONTACT_STAFF" -> "Items moved to storage; contact staff to retrieve them.";
            default -> order.getStatus();
        };
    }

    private boolean paymentRequired(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return "READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage())
                    && !"PAID".equalsIgnoreCase(order.getPaymentStatus());
        }
        return "INITIALIZED".equals(order.getStatus());
    }

    private String statusDescription(String status) {
        return switch (status) {
            case "INITIALIZED" -> "Order created";
            case "AWAITING_DISPATCH" -> "Awaiting drone dispatch";
            case "STORING" -> "Items stored in locker";
            case "COMPLETED" -> "Completed";
            case "CANCELED" -> "Canceled";
            case "EXPIRED" -> "Pickup overdue, items moved to storage";
            default -> status;
        };
    }

    private void addHistory(Long orderId, String oldStatus, String newStatus, Long actorId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedByUserId(actorId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private void publishStatusChanged(LockerOrder order, String oldStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("oldStatus", oldStatus);
        payload.put("newStatus", order.getStatus());
        publish(DomainEventNames.ORDER_STATUS_CHANGED, order, payload);
    }

    private void notifyStatus(LockerOrder order, String oldStatus) {
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(),
                            "Order " + order.getStatus().toLowerCase(),
                            "Order " + order.getOrderCode() + " changed from " + oldStatus + " to " + order.getStatus(),
                            "ORDER_STATUS",
                            order.getId(),
                            "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not call notification-service for order {}: {}", order.getId(), ex.getMessage());
        }
    }

    private void publish(String eventName, LockerOrder order, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    eventName,
                    DomainEvent.of(eventName, "order-service", payload));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for order {}: {}", eventName, order.getId(), ex.getMessage());
        }
    }

    private String generatePinCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generateOrderCode() {
        // 36^6 vượt quá Integer.MAX_VALUE nên phải sinh bằng long
        String randomPart = Long.toString(RANDOM.nextLong(2_176_782_336L), 36).toUpperCase();
        return "ORD-" + LocalDate.now().toString().replace("-", "") + "-" + randomPart;
    }

    private Long firstNonNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }
}
