package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.*;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> CANCELABLE = Set.of("INITIALIZED", "RESERVED", "WAITING", "AWAITING_DISPATCH");
    private static final Set<String> PIN_RESETTABLE = Set.of("INITIALIZED", "STORING", "RETURNED");

    /// Bước admin được đổi tay (trạng thái hiện tại → đích). Đơn đã COMPLETED/CANCELED là đóng hẳn.
    /// Không có bước nào về INITIALIZED/STORING: bỏ hàng và cấp PIN người nhận chỉ đi qua confirm().
    private static final Map<String, Set<String>> STAFF_TRANSITIONS = Map.of(
            "INITIALIZED", Set.of("CANCELED"),
            "RESERVED", Set.of("CANCELED"),
            "WAITING", Set.of("CANCELED"),
            "AWAITING_DISPATCH", Set.of("CANCELED"),
            "STORING", Set.of("COMPLETED", "EXPIRED"),
            "RETURNED", Set.of("COMPLETED", "EXPIRED"),
            "READY", Set.of("COMPLETED"),
            "EXPIRED", Set.of("COMPLETED"));

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
    private final LockerLookupClient lockerLookupClient;
    private final NotificationClient notificationClient;
    private final QrTokenService qrTokenService;
    /// Giá, phí, thời hạn… admin cấu hình trên web (ADR-0005), không còn @Value cứng.
    private final OrderRules rules;
    /// Mã giảm giá + điểm thưởng của đơn: áp khi tạo, trả lại khi huỷ/hoàn tiền.
    private final OrderBenefitService benefits;
    /// Tủ áp dụng của mã giảm giá (mã/tên tủ) cho các API khuyến mãi.
    private final PromotionViews promotionViews;
    /// Đơn 0đ sau giảm giá: ghi PAID và chạy các bước "đã thanh toán" như payment.completed.
    private final PaidOrderEffects paidEffects;

    /// Tạo đơn từ API công khai: giá luôn do server tính, bỏ qua `totalPrice` client gửi.
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return create(request, null);
    }

    /// `serverPrice` = giá server đã tính theo cấu hình (gửi hàng, thuê tủ); null ⇒ tính theo danh sách món.
    @Transactional
    public OrderResponse create(CreateOrderRequest request, BigDecimal serverPrice) {
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
        saved.setTotalPrice(serverPrice == null ? calculatedTotal : serverPrice);
        saved.setOriginalPrice(saved.getTotalPrice());
        // Giá gốc → mã giảm giá (kiểm theo tủ của đơn) → điểm thưởng trên phần còn lại.
        benefits.applyVouchers(saved,
                OrderBenefitService.normalizeCodes(request.promotionCode(), request.promotionCodes()),
                saved.getLockerId());
        benefits.redeemPoints(saved, request.pointsToUse());
        saved = orderRepository.save(saved);
        addHistory(saved.getId(), null, saved.getStatus(), saved.getUserId(), "Order created");
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", saved.getId());
        payload.put("orderCode", saved.getOrderCode());
        payload.put("userId", saved.getUserId());
        payload.put("status", saved.getStatus());
        payload.put("type", saved.getType());
        publish(DomainEventNames.ORDER_CREATED, saved, payload);
        notifyQuietly(saved.getUserId(), "Đơn hàng mới", "Đơn hàng " + saved.getOrderCode() + " đã được tạo thành công", "ORDER_CREATED", saved.getId());
        // Mã giảm giá + điểm phủ hết tiền đơn ⇒ đã thanh toán ngay, không chờ cổng thanh toán.
        paidEffects.settleIfFree(saved);
        return toResponse(saved);
    }

    /// Đổi trạng thái thủ công (admin). Chỉ cho các bước chuyển trong [STAFF_TRANSITIONS] và đi
    /// qua đúng đường nghiệp vụ của bước đó (huỷ / chốt đơn / dời kho), để không mở lại đơn đã
    /// đóng hay bỏ qua bước cấp PIN, giữ ô, hoàn ưu đãi.
    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        LockerOrder order = orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Order", id));
        String current = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        String target = request.status() == null ? "" : request.status().trim().toUpperCase();
        Set<String> allowed = STAFF_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    "ORDER_STATUS_TRANSITION_INVALID",
                    allowed.isEmpty()
                            ? "Đơn ở trạng thái " + current + " không thể đổi trạng thái thủ công"
                            : "Từ " + current + " chỉ được chuyển sang " + String.join(", ", new TreeSet<>(allowed)));
        }
        return switch (target) {
            case "CANCELED" -> cancelOrder(order, request.staffId(), null, "Order canceled by admin");
            case "COMPLETED" -> checkout(order.getId(), request.staffId(), "Order closed by admin");
            case "EXPIRED" -> moveToStorage(order, request.staffId(), "Admin chuyển đồ vào kho lưu trữ, ô đã giải phóng");
            default -> throw new IllegalStateException("Unhandled transition " + current + " -> " + target);
        };
    }

    @Transactional
    public OrderResponse confirm(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        validateStatus(order, Set.of("INITIALIZED"));
        assertPaidBeforeDrop(order);
        if ("RENTAL".equalsIgnoreCase(order.getType())) {
            occupyBoxQuietly(order.getSendBoxId());
            order.setPickupDeadline(LocalDateTime.now().plusHours(resolveRentalDurationHours(order)));
            return transition(order, "STORING", userId, null, "Renter placed items; multi-use PIN active until deadline");
        }
        if ("SEND".equalsIgnoreCase(order.getType())
                && rules.sendConfirmRequiresOpen()
                && order.getDropOpenedAt() == null) {
            throw new BusinessException(
                    "ORDER_DROP_NOT_OPENED",
                    "Vui lòng mở ô bằng mã gửi hàng và bỏ hàng vào trước khi xác nhận.");
        }
        occupyBoxQuietly(order.getSendBoxId());
        if ("SEND".equalsIgnoreCase(order.getType())) {
            // Stage 2 of the SEND flow: the drop PIN dies here, a fresh pickup PIN
            // goes to the receiver — the sender can no longer open the cell.
            order.setPinCode(generatePinCode());
            order.setPinCodeIssuedAt(LocalDateTime.now());
            order.setPickupDeadline(LocalDateTime.now().plusHours(rules.sendPickupHours()));
            notifyParcelReadyForReceiver(order);
            return transition(order, "STORING", userId, null,
                    "Sender dropped parcel; pickup PIN issued to receiver " + order.getReceiverPhone());
        }
        return transition(order, "STORING", userId, null, "Customer confirmed items dropped in locker");
    }

    /// iot-service báo ô vừa được mở bằng mã khi đơn còn INITIALIZED (người gửi đang bỏ
    /// hàng). Chỉ ghi lần đầu; đơn đã qua bước bỏ hàng thì bỏ qua.
    @Transactional
    public void markDropOpened(Long id) {
        LockerOrder order = find(id);
        if (!"INITIALIZED".equalsIgnoreCase(order.getStatus()) || order.getDropOpenedAt() != null) {
            return;
        }
        order.setDropOpenedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse createSend(SendOrderRequest request, Long userId) {
        Long boxId = request.boxId();
        if (boxId == null) {
            boxId = findAvailableCell(request.lockerId(), request.size(), "STANDARD");
        }
        // Giá gửi hàng luôn theo cấu hình admin — không tin totalPrice client gửi lên.
        OrderResponse created = create(
                new CreateOrderRequest(
                        userId, request.lockerId(), boxId, null, null,
                        "SEND", "PARCEL",
                        null, request.receiverPhone(), request.receiverName(),
                        null, null, request.note(), null, null, null, request.promotionCode(), null, null,
                        request.pointsToUse()),
                rules.sendBaseFee());

        // Email người nhận ghi sau khi tạo, giống cách createRental đặt số giờ thuê:
        // CreateOrderRequest là record vị trí dùng chung cho mọi loại đơn, thêm trường
        // vào đó sẽ phải sửa mọi nơi gọi chỉ vì một loại đơn cần.
        if (!StringUtils.hasText(request.receiverEmail())) {
            return created;
        }
        LockerOrder order = find(created.id());
        order.setReceiverEmail(request.receiverEmail().trim());
        Long receiverUserId = receiverAccount(order.getReceiverEmail(), order.getReceiverPhone());
        if (receiverUserId != null) {
            order.setReceiverId(receiverUserId);
            order.setReceiverUserId(receiverUserId);
        }
        LockerOrder saved = orderRepository.save(order);
        if (receiverUserId != null && !receiverUserId.equals(saved.getUserId())) {
            notifyQuietly(receiverUserId, "Bạn sắp nhận được một đơn hàng",
                    "Đơn " + saved.getOrderCode() + " đã được tạo. Bạn có thể theo dõi tại mục Hàng chờ nhận.",
                    "ORDER_CREATED", saved.getId());
        }
        return toResponse(saved);
    }

    private Long receiverAccount(String email, String phone) {
        if (StringUtils.hasText(email)) {
            try {
                var response = userClient.getUserByEmail(email.trim());
                if (response != null && response.data() != null) {
                    return response.data().id();
                }
            } catch (Exception ex) {
                log.debug("Receiver email did not match an active Lock.R account");
            }
        }
        if (StringUtils.hasText(phone)) {
            try {
                var response = userClient.getUserByPhone(phone);
                return response == null || response.data() == null ? null : response.data().id();
            } catch (Exception ex) {
                log.debug("Receiver phone did not match a Lock.R account");
            }
        }
        return null;
    }

    @Transactional
    public OrderResponse createRental(RentalOrderRequest request, Long userId) {
        String cellType = StringUtils.hasText(request.cellType()) ? request.cellType().toUpperCase() : "STANDARD";
        if ("DRONE".equals(cellType)) {
            throw new BusinessException("DRONE_CELL_RESTRICTED", "Drone cells cannot be rented");
        }
        assertRentalHours(request.hours(), rules.rentalMinHours(), rules.rentalMaxHours());
        Long boxId = request.boxId();
        if (boxId == null) {
            boxId = findAvailableCell(request.lockerId(), null, cellType);
        } else {
            // Khách chọn ô cụ thể: giá theo loại ô thật, không theo loại app khai.
            cellType = actualCellType(boxId);
            if ("DRONE".equals(cellType)) {
                throw new BusinessException("DRONE_CELL_RESTRICTED", "Drone cells cannot be rented");
            }
        }
        BigDecimal price = rules.rentalRate(cellType).multiply(BigDecimal.valueOf(request.hours()));
        OrderResponse created =
                create(
                        new CreateOrderRequest(
                                userId, request.lockerId(), boxId, null, null,
                                "RENTAL", "RENTAL",
                                null, null, null, null, null, request.note(), null, null, null, request.promotionCode(), null, null,
                                request.pointsToUse()),
                        price);
        LockerOrder order = find(created.id());
        order.setPickupDeadline(null);
        order.setRentalDurationHours(request.hours());
        order.setRentalCellType(cellType);
        return toResponse(orderRepository.save(order));
    }

    /// Gia hạn lúc đang ở quá giờ: chốt phí các giờ đã quá (tính như thuê thêm, phần chưa thu) vào giá đơn
    /// rồi mới cộng giờ gia hạn tính từ hết các giờ đó. Không thì gia hạn tính lại từ "bây giờ" và xoá
    /// mất phần quá giờ. Phí quá giờ đã chốt thành giờ thuê nên `extraFee` về 0 cho lần quá giờ sau.
    /// Trả về mốc mới để cộng giờ gia hạn.
    private LocalDateTime settleRentalOvertime(LockerOrder order, LocalDateTime deadline, LocalDateTime now, Long userId) {
        long minutes = ChronoUnit.MINUTES.between(deadline, now);
        long overHours = (minutes + 59) / 60;
        BigDecimal charged = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
        BigDecimal diff = rentalOvertimeFee(order, minutes).subtract(charged).max(BigDecimal.ZERO);
        if (diff.signum() > 0) {
            order.setTotalPrice(order.getTotalPrice().add(diff));
            if (order.getOriginalPrice() != null) {
                order.setOriginalPrice(order.getOriginalPrice().add(diff));
            }
            addHistory(order.getId(), order.getStatus(), order.getStatus(), userId,
                    "Chốt phí ở quá giờ " + overHours + " giờ (" + diff.toBigInteger() + " đ) trước khi gia hạn");
        }
        order.setExtraFee(BigDecimal.ZERO);
        return deadline.plusHours(overHours);
    }

    /// Loại ô thật theo locker-service. Không tra được thì không tạo đơn (không đoán giá).
    private String actualCellType(Long boxId) {
        CellDto cell;
        try {
            cell = lockerCellClient.getCell(boxId).data();
        } catch (Exception ex) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "Không đọc được thông tin ô " + boxId);
        }
        if (cell == null) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "Không tìm thấy ô " + boxId);
        }
        return StringUtils.hasText(cell.cellType()) ? cell.cellType().trim().toUpperCase() : "STANDARD";
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
        assertRentalHours(hours, 1, rules.extendMaxHours());
        order.setRentalDurationHours(resolveRentalDurationHours(order) + hours);
        if (!"INITIALIZED".equalsIgnoreCase(order.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime base = order.getPickupDeadline() == null ? now : order.getPickupDeadline();
            if (base.isBefore(now)) {
                base = settleRentalOvertime(order, base, now, userId);
            }
            order.setPickupDeadline(base.plusHours(hours));
        }
        BigDecimal extra = rules.rentalRate(cellTypeOfRental(order)).multiply(BigDecimal.valueOf(hours));
        order.setTotalPrice(order.getTotalPrice().add(extra));
        order.setOriginalPrice(order.getOriginalPrice().add(extra));
        if (extra.compareTo(BigDecimal.ZERO) > 0) {
            // `totalPrice` là tổng luỹ kế của cả hợp đồng thuê, còn `paidAmount`
            // giữ nguyên phần khách đã trả — client thu `amountDue`, tức chỉ
            // phần giờ vừa gia hạn.
            order.setPaymentStatus("UNPAID");
            order.setPaidAt(null);
        }
        LockerOrder saved = orderRepository.save(order);
        addHistory(saved.getId(), saved.getStatus(), saved.getStatus(), userId,
                "Rental extended by " + hours + "h until " + saved.getPickupDeadline());
        notifyQuietly(saved.getUserId(), "Đã gia hạn thuê ô",
                "Đơn thuê " + saved.getOrderCode() + " được gia hạn thêm " + hours + " giờ, tới "
                        + formatDeadline(saved.getPickupDeadline()) + ".",
                "ORDER_RENTAL_EXTENDED", saved.getId());
        publishStatusChanged(saved, saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse assessOvertime(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentExtra = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
            if (currentExtra.compareTo(overtime) < 0) {
                BigDecimal diff = overtime.subtract(currentExtra);
                order.setExtraFee(overtime);
                order.setTotalPrice(order.getTotalPrice().add(diff));
                if (order.getOriginalPrice() != null) {
                    order.setOriginalPrice(order.getOriginalPrice().add(diff));
                }
                order.setPaymentStatus("UNPAID");
                order.setPaidAt(null);
                orderRepository.save(order);
                addHistory(order.getId(), order.getStatus(), order.getStatus(), userId,
                        "Assessed pickup overtime fee: " + overtime + " (added " + diff + ")");
            }
        }
        return toResponse(order);
    }

    private String cellTypeOfRental(LockerOrder order) {
        if (StringUtils.hasText(order.getRentalCellType())) {
            return order.getRentalCellType();
        }
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

    private static void assertRentalHours(Integer hours, int min, int max) {
        if (hours == null || hours < min || hours > max) {
            throw new BusinessException(
                    "RENTAL_HOURS_OUT_OF_RANGE", "hours must be between " + min + " and " + max);
        }
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

    private void releaseBoxQuietly(Long boxId) {
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.releaseBox(boxId);
        } catch (Exception ex) {
            log.warn("Could not release box {}: {}", boxId, ex.getMessage());
        }
    }

    /// Báo cho người nhận rằng hàng đã nằm trong tủ, kèm mã mở tủ.
    ///
    /// Ba kênh, cố ý thử CẢ BA chứ không dừng ở kênh đầu thành công — mã không tới nơi
    /// là hỏng cả lượt nhận hàng:
    /// 1. thông báo trong app, nếu số điện thoại trùng một tài khoản Lock.R;
    /// 2. SMS tới đúng số người gửi đã nhập;
    /// 3. email, nếu người gửi có nhập.
    ///
    /// Cuối cùng báo lại cho NGƯỜI GỬI đã gửi được qua kênh nào: khi không kênh nào tới
    /// nơi thì họ phải tự chuyển mã, và họ chỉ làm vậy nếu biết.
    ///
    /// `type` giữ nguyên (`ORDER_PARCEL_READY`, `ORDER_PARCEL_STORED`) vì app dựa vào nó
    /// để mở đúng màn hình; tiêu đề/nội dung viết thẳng tiếng Việt.
    private void notifyParcelReadyForReceiver(LockerOrder order) {
        PickupPlace place = pickupPlace(order);
        String deadline = formatDeadline(order.getPickupDeadline());

        boolean inAppSent = false;
        try {
            var receiver = order.getReceiverUserId() == null
                    ? null
                    : userClient.getUser(order.getReceiverUserId()).data();
            if (receiver == null && StringUtils.hasText(order.getReceiverEmail())) {
                try {
                    var byEmail = userClient.getUserByEmail(order.getReceiverEmail().trim());
                    receiver = byEmail == null ? null : byEmail.data();
                } catch (Exception ignored) {
                    // Continue with the legacy phone lookup for recipients without a matching email account.
                }
            }
            if (receiver == null && StringUtils.hasText(order.getReceiverPhone())) {
                var byPhone = userClient.getUserByPhone(order.getReceiverPhone());
                receiver = byPhone == null ? null : byPhone.data();
            }
            if (receiver != null && receiver.id() != null) {
                order.setReceiverId(receiver.id());
                order.setReceiverUserId(receiver.id());
                notificationClient.requestNotification(new NotificationRequest(
                        receiver.id(), RECEIVER_READY_TITLE, receiverReadyMessage(order, place, deadline),
                        "ORDER_PARCEL_READY", order.getId(), "ORDER"));
                inAppSent = true;
            }
        } catch (Exception ex) {
            log.info("Receiver of order {} not notified in-app: {}", order.getId(), ex.getMessage());
        }

        GuestNotification.Result guest = notifyReceiverOutOfBand(order, place, deadline);

        notifyQuietly(order.getUserId(), SENDER_STORED_TITLE,
                senderSummary(order, place, deadline, inAppSent, guest.smsSent(), guest.emailSent()),
                "ORDER_PARCEL_STORED", order.getId());
    }

    static final String RECEIVER_READY_TITLE = "Có kiện hàng đang chờ bạn";
    static final String SENDER_STORED_TITLE = "Đã gửi hàng vào tủ";
    /// Một tin SMS GSM-7 tối đa 160 ký tự; dài hơn là tách thành nhiều tin (tốn tiền hơn).
    static final int SMS_SINGLE_SEGMENT = 160;

    static String receiverReadyMessage(LockerOrder order, PickupPlace place, String deadline) {
        return "Kiện hàng " + order.getOrderCode() + " đang chờ bạn tại " + place.label() + ". "
                + "Mã mở tủ: " + order.getPinCode() + ". "
                + "Hạn lấy hàng: " + deadline + ". "
                + "Nhập mã trên màn hình tủ để mở ô và lấy hàng.";
    }

    /// Gửi mã qua SMS/email tới số và email người gửi đã nhập trên đơn.
    /// Không bao giờ ném lỗi ra ngoài: hỏng kênh nhắn tin không được làm hỏng việc bỏ hàng.
    private GuestNotification.Result notifyReceiverOutOfBand(LockerOrder order, PickupPlace place, String deadline) {
        String phone = rules.receiverNotifySms() ? order.getReceiverPhone() : null;
        String email = rules.receiverNotifyEmail() ? order.getReceiverEmail() : null;
        if (!StringUtils.hasText(phone) && !StringUtils.hasText(email)) {
            return new GuestNotification.Result(false, false, false, false);
        }

        String sms = pickupSms(order.getOrderCode(), place, order.getPinCode(), deadline);
        String emailBody = "Xin chào" + (StringUtils.hasText(order.getReceiverName())
                ? " " + order.getReceiverName() : "") + ",\n\n"
                + "Bạn có một kiện hàng đang chờ trong tủ Lock.R.\n\n"
                + "Mã đơn: " + order.getOrderCode() + "\n"
                + "Nơi lấy: " + place.label() + "\n"
                + "Mã mở tủ: " + order.getPinCode() + "\n"
                + "Hạn lấy hàng: " + deadline + "\n\n"
                + "Nhập mã này trên màn hình tủ để mở ô và lấy hàng. "
                + "Không chia sẻ mã cho người khác.\n";

        try {
            var response = notificationClient.notifyGuest(
                    new GuestNotification.Request(phone, email, "Mã mở tủ Lock.R cho đơn "
                            + order.getOrderCode(), sms, emailBody));
            var result = response == null ? null : response.data();
            return result == null
                    ? new GuestNotification.Result(false, false, false, false)
                    : result;
        } catch (Exception ex) {
            log.warn("Could not send pickup code out-of-band for order {}: {}", order.getId(), ex.getMessage());
            return new GuestNotification.Result(false, false, false, false);
        }
    }

    /// Tin SMS cho người nhận: tiếng Việt KHÔNG dấu (GSM-7 — 160 ký tự/tin thay vì 70),
    /// chỉ gồm mã đơn, nơi lấy, mã mở tủ, hạn lấy; không có tên/số của ai.
    ///
    /// Khuôn cố định "Lock.R: Ban co kien hang {ma don} tai {noi lay}. Ma mo tu: {PIN}.
    /// Han lay: {HH:mm dd/MM/yyyy}." — trùng template phải đăng ký nếu gửi bằng brandname.
    /// Quá 160 ký tự thì bỏ địa chỉ tủ trước, rồi mới cắt bớt tên tủ.
    static String pickupSms(String orderCode, PickupPlace place, String pin, String deadline) {
        String name = SmsText.ascii(place.lockerName());
        String address = SmsText.ascii(place.lockerAddress());
        String box = place.boxNumber() == null ? "" : ", o so " + place.boxNumber();

        String full = pickupSmsText(orderCode, name + (address.isEmpty() ? "" : " (" + address + ")") + box, pin, deadline);
        if (full.length() <= SMS_SINGLE_SEGMENT) return full;

        String noAddress = pickupSmsText(orderCode, name + box, pin, deadline);
        int overflow = noAddress.length() - SMS_SINGLE_SEGMENT;
        if (overflow <= 0) return noAddress;

        // Tên tủ quá dài: cắt bớt nhưng vẫn giữ ít nhất vài chữ đầu để người nhận nhận ra.
        int keep = Math.max(name.length() - overflow, Math.min(name.length(), 12));
        return pickupSmsText(orderCode, name.substring(0, keep).trim() + box, pin, deadline);
    }

    private static String pickupSmsText(String orderCode, String where, String pin, String deadline) {
        return "Lock.R: Ban co kien hang " + orderCode + " tai " + where
                + ". Ma mo tu: " + pin + ". Han lay: " + SmsText.ascii(deadline) + ".";
    }

    /// Câu báo cho người gửi, nói thẳng người nhận đã nhận được mã qua kênh nào chưa.
    static String senderSummary(LockerOrder order, PickupPlace place, String deadline,
                                boolean inAppSent, boolean smsSent, boolean emailSent) {
        String base = "Kiện hàng " + order.getOrderCode() + " đã được cất tại " + place.label() + ". "
                + "Người nhận " + order.getReceiverPhone() + " lấy hàng bằng mã " + order.getPinCode()
                + " trước " + deadline + ". ";

        List<String> channels = new ArrayList<>();
        if (inAppSent) channels.add("thông báo trong ứng dụng Lock.R");
        if (smsSent) channels.add("SMS");
        if (emailSent) channels.add("email");

        return base + (channels.isEmpty()
                ? "Lock.R CHƯA gửi được mã tới người nhận (qua ứng dụng, SMS hay email). "
                        + "Hãy mở đơn và bấm \"Chia sẻ mã cho người nhận\" để tự gửi mã cho họ."
                : "Mã mở tủ đã được gửi tới người nhận qua " + String.join(", ", channels) + ".");
    }

    /// Nơi lấy hàng: tên + địa chỉ tủ và số ô — tra MỘT lần cho cả thông báo trong app,
    /// SMS và email. Tra không được thì rơi về `tủ #<id>`; "locker 7" không giúp ai tìm tủ.
    record PickupPlace(String lockerName, String lockerAddress, Integer boxNumber) {

        /// `Tủ Thư viện (Toà Alpha), ô số 4` — có dấu, dùng cho app và email.
        String label() {
            StringBuilder label = new StringBuilder(lockerName);
            if (StringUtils.hasText(lockerAddress)) label.append(" (").append(lockerAddress).append(')');
            if (boxNumber != null) label.append(", ô số ").append(boxNumber);
            return label.toString();
        }
    }

    private PickupPlace pickupPlace(LockerOrder order) {
        LockerInfo locker = lockerInfo(order.getLockerId());
        String name = locker != null && StringUtils.hasText(locker.name())
                ? locker.name().trim()
                : order.getLockerId() == null ? "tủ Lock.R" : "tủ #" + order.getLockerId();
        String address = locker != null && StringUtils.hasText(locker.address()) ? locker.address().trim() : null;
        return new PickupPlace(name, address, boxNumber(activeBoxId(order)));
    }

    private Integer boxNumber(Long boxId) {
        if (boxId == null) return null;
        try {
            var response = lockerClient.getBox(boxId);
            var box = response == null ? null : response.data();
            return box == null ? null : box.boxNumber();
        } catch (Exception ex) {
            log.debug("Box {} lookup failed while composing pickup message: {}", boxId, ex.getMessage());
            return null;
        }
    }

    private LockerInfo lockerInfo(Long lockerId) {
        if (lockerId == null) return null;
        try {
            var response = lockerLookupClient.getLockers(List.of(lockerId));
            var lockers = response == null ? null : response.data();
            return lockers == null || lockers.isEmpty() ? null : lockers.get(0);
        } catch (Exception ex) {
            log.debug("Locker {} lookup failed while composing pickup message: {}", lockerId, ex.getMessage());
            return null;
        }
    }

    /// Tên + địa chỉ tủ nếu tra cứu được, ngược lại rơi về mã tủ. Người nhận cần biết
    /// đi đâu, "locker 7" thì không giúp được gì.
    private String lockerLabel(Long lockerId) {
        if (lockerId == null) return "tủ Lock.R";
        LockerInfo locker = lockerInfo(lockerId);
        if (locker == null) return "tủ #" + lockerId;
        String name = StringUtils.hasText(locker.name()) ? locker.name() : "tủ #" + lockerId;
        return StringUtils.hasText(locker.address()) ? name + " (" + locker.address() + ")" : name;
    }

    /// Cột thời gian trong DB ghi theo múi giờ JVM (UTC trên container) nhưng người nhận
    /// đọc giờ Việt Nam — đổi qua {@link BusinessTime} như các báo cáo admin.
    private static final DateTimeFormatter PICKUP_DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    String formatDeadline(LocalDateTime deadline) {
        if (deadline == null) return "chưa xác định";
        return PICKUP_DEADLINE_FORMAT.format(
                deadline.atZone(time.storageZone()).withZoneSameInstant(time.businessZone()));
    }

    private void notifyQuietly(Long userId, String title, String message, String type, Long orderId) {
        try {
            notificationClient.requestNotification(new NotificationRequest(userId, title, message, type, orderId, "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify user {} for order {}: {}", userId, orderId, ex.getMessage());
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
        validateStatus(order, Set.of("STORING", "RETURNED", "EXPIRED"));
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentExtra = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
            if (currentExtra.compareTo(overtime) < 0) {
                BigDecimal diff = overtime.subtract(currentExtra);
                order.setExtraFee(overtime);
                order.setTotalPrice(order.getTotalPrice().add(diff));
                if (order.getOriginalPrice() != null) {
                    order.setOriginalPrice(order.getOriginalPrice().add(diff));
                }
                order.setPaymentStatus("UNPAID");
                order.setPaidAt(null);
            }
        }
        assertPaidBeforePickup(order);
        releaseBoxes(order);
        order.setCompletedAt(LocalDateTime.now());
        order.setPinCode(null);
        return transition(order, "COMPLETED", userId, order.getReceiveBoxId(), "Customer completed pickup");
    }

    @Transactional
    public OrderResponse cancel(Long id, Integer reason, Long userId) {
        // Khoá đơn: huỷ và đội bay tiếp nhận (accept cũng khoá) không được chạy xen, nếu
        // không đơn thành CANCELED trong khi nhiệm vụ vừa tạo vẫn giữ drone.
        LockerOrder order = orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Order", id));
        assertOwner(order, userId);
        if (!CANCELABLE.contains(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "Order cannot be canceled at this status");
        }
        return cancelOrder(order, userId, reason, "Order canceled");
    }

    /// Đường huỷ chung cho khách và admin: chặn đơn drone kiện đã rời tay khách, xoá PIN,
    /// giữ ô gửi đang chứa kiện drone, hoàn ưu đãi (trong [transition]).
    private OrderResponse cancelOrder(LockerOrder order, Long actorId, Integer reason, String note) {
        assertDroneCancelable(order);
        order.setCancelReason(reason);
        order.setPinCode(null);
        // Đơn drone đã bỏ kiện vào ô gửi: giữ ô đó tới khi đội bay xác nhận đã trả kiện
        // (DroneParcelService.confirmReturn), nếu không ô được cấp lại khi kiện còn bên trong.
        Long heldSourceBoxId = order.getParcelDroppedAt() != null ? order.getSourceBoxId() : null;
        order.setSourceBoxId(heldSourceBoxId == null ? order.getSourceBoxId() : null);
        releaseBoxes(order);
        if (heldSourceBoxId != null) {
            order.setSourceBoxId(heldSourceBoxId);
        }
        return transition(order, "CANCELED", actorId, null, note);
    }

    @Transactional
    public OrderResponse resetPin(Long id, Long userId) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        // Chỉ đổi khi mã còn dùng được: đơn chưa đóng và đang có PIN. Đơn drone cấp PIN lúc kiện
        // vào ô nhận, không đổi qua đây (khớp điều kiện nút "Đổi mã PIN" trên app).
        validateStatus(order, PIN_RESETTABLE);
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType()) || !StringUtils.hasText(order.getPinCode())) {
            throw new BusinessException("ORDER_PIN_RESET_INVALID", "Đơn này không có mã PIN để đổi");
        }
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
        // Lưu riêng người lấy hộ để họ thấy đơn ở GET /api/orders/delegated-to-me.
        order.setDelegatePhone(request.phone().trim());
        order.setDelegateName(StringUtils.hasText(request.name()) ? request.name().trim() : null);
        order.setDelegateNote(StringUtils.hasText(request.note()) ? truncate(request.note().trim(), 500) : null);
        order.setDelegatedAt(LocalDateTime.now());
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
                            "Đã uỷ quyền nhận hàng",
                            "Đơn " + saved.getOrderCode() + " đã uỷ quyền cho " + request.phone()
                                    + " lấy hộ. Mã lấy hàng đã đổi, mã cũ không còn dùng được.",
                            "ORDER_DELEGATED",
                            saved.getId(),
                            "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify delegation for order {}: {}", saved.getId(), ex.getMessage());
        }
        notifyDelegateQuietly(saved);
        return toResponse(saved);
    }

    /// Người lấy hộ có tài khoản Lock.R ⇒ báo trong app; mã mở ô xem ở mục "Được ủy quyền".
    private void notifyDelegateQuietly(LockerOrder order) {
        try {
            var delegate = userClient.getUserByPhone(order.getDelegatePhone()).data();
            if (delegate == null || delegate.id() == null || delegate.id().equals(order.getUserId())) {
                return;
            }
            notificationClient.requestNotification(new NotificationRequest(
                    delegate.id(),
                    "Bạn được ủy quyền lấy hộ",
                    "Bạn được ủy quyền lấy hộ đơn " + order.getOrderCode()
                            + ". Mở mục \"Được ủy quyền\" trong app để xem mã mở tủ.",
                    "ORDER_DELEGATED_TO_YOU",
                    order.getId(),
                    "ORDER"));
        } catch (Exception ex) {
            log.info("Delegate of order {} not notified in-app: {}", order.getId(), ex.getMessage());
        }
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
                        ? Set.of("STORING", "RETURNED", "EXPIRED")
                        : Set.of("STORING", "INITIALIZED", "RETURNED", "EXPIRED"));
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentExtra = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
            if (currentExtra.compareTo(overtime) < 0) {
                BigDecimal diff = overtime.subtract(currentExtra);
                order.setExtraFee(overtime);
                order.setTotalPrice(order.getTotalPrice().add(diff));
                if (order.getOriginalPrice() != null) {
                    order.setOriginalPrice(order.getOriginalPrice().add(diff));
                }
                order.setPaymentStatus("UNPAID");
                order.setPaidAt(null);
            }
        }
        assertPaidBeforeStorageCompletion(order);
        releaseBoxes(order);
        order.setCompletedAt(LocalDateTime.now());
        order.setPinCode(null);
        return transition(order, "COMPLETED", userId, order.getReceiveBoxId(),
                "RENTAL".equalsIgnoreCase(order.getType()) ? "Rental ended, cell released" : "Storage order picked up");
    }

    @Transactional
    public OrderResponse reportBoxFault(Long id, Long userId, String reason) {
        return reportBoxFault(id, userId, reason, null);
    }

    /// `attachments`: ảnh hiện trường khách đã upload lên Cloudinary; locker-service tự xác minh.
    @Transactional
    public OrderResponse reportBoxFault(Long id, Long userId, String reason, List<Object> attachments) {
        LockerOrder order = find(id);
        assertOwner(order, userId);
        validateStatus(order, Set.of("INITIALIZED", "STORING", "RETURNED"));
        Long boxId = activeBoxId(order);
        if (boxId == null) {
            throw new BusinessException("BOX_NOT_FOUND", "Order has no active box to report");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            if (reason != null && !reason.isBlank()) {
                body.put("reason", reason);
            }
            if (attachments != null && !attachments.isEmpty()) {
                body.put("attachments", attachments);
            }
            if (order.getId() != null) {
                body.put("orderId", order.getId());
            }
            if (order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
                body.put("orderCode", order.getOrderCode());
            }
            lockerClient.reportFault(boxId, body, userId);
        } catch (Exception ex) {
            throw unwrapDownstreamError(ex, "BOX_FAULT_REPORT_FAILED", "Could not report fault for box " + boxId);
        }
        if (shouldAutoCancelAfterFault(order)) {
            order.setCancelReason(null);
            order.setPinCode(null);
            releaseBoxes(order);
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
                            original.getReceiverEmail(),
                            original.getCustomerNote(),
                            null,
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
                            null,
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
    public OrderParticipantsResponse participants(Long id) {
        LockerOrder order = find(id);
        return new OrderParticipantsResponse(
                order.getId(), order.getUserId(), firstNonNull(order.getReceiverUserId(), order.getReceiverId()));
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
                isPaidOrFree(order),
                nextAction(order),
                order.getPaymentStatus());
    }

    private static boolean isPaidOrFree(LockerOrder order) {
        BigDecimal total = order.getTotalPrice();
        return "PAID".equalsIgnoreCase(order.getPaymentStatus())
                || total == null
                || total.compareTo(BigDecimal.ZERO) <= 0;
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
                    && order.getLastReminderAt().isAfter(now.minusMinutes(rules.reminderCooldownMinutes()))) {
                continue;
            }
            boolean rental = "RENTAL".equalsIgnoreCase(order.getType());
            String deadline = formatDeadline(order.getPickupDeadline());
            notifyQuietly(order.getUserId(),
                    rental ? "Đã hết giờ thuê ô" : "Đơn hàng quá hạn lấy",
                    (rental
                            ? "Đơn thuê " + order.getOrderCode() + " đã hết giờ lúc " + deadline
                                    + ". Vui lòng lấy đồ hoặc gia hạn; phí quá giờ đang được tính."
                            : "Đơn " + order.getOrderCode() + " đã quá hạn lấy hàng (" + deadline
                                    + "). Người nhận lấy càng sớm càng tốt; phí quá giờ đang được tính."),
                    "ORDER_PICKUP_OVERDUE", order.getId());
            if (order.getReceiverId() != null) {
                notifyQuietly(order.getReceiverId(), "Kiện hàng đang chờ bạn quá hạn",
                        "Kiện hàng của đơn " + order.getOrderCode() + " vẫn đang chờ trong tủ, đã quá hạn lấy "
                                + deadline + ". Phí quá giờ đang được tính.",
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

    @Transactional
    public List<OrderResponse> listByUser(Long userId) {
        int autoCancelUnpaidMinutes = rules.autoCancelUnpaidMinutes();
        LocalDateTime cutoffUnpaid = LocalDateTime.now().minusMinutes(autoCancelUnpaidMinutes);
        List<LockerOrder> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        boolean dirty = false;
        for (LockerOrder order : orders) {
            if ("INITIALIZED".equalsIgnoreCase(order.getStatus())
                    && !isOrderPaid(order)
                    && order.getCreatedAt() != null
                    && order.getCreatedAt().isBefore(cutoffUnpaid)) {
                order.setPinCode(null);
                releaseBoxes(order);
                transition(
                        order,
                        "CANCELED",
                        null,
                        null,
                        "Tự hủy: chưa thanh toán trong " + autoCancelUnpaidMinutes + " phút; đã nhả ô");
                dirty = true;
            }
        }
        if (dirty) {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        // Lịch sử của cả danh sách trong một truy vấn (mốc thời gian của từng đơn).
        Map<Long, List<OrderStatusHistory>> histories = new HashMap<>();
        if (!orders.isEmpty()) {
            historyRepository.findByOrderIdInOrderByCreatedAtAscIdAsc(
                            orders.stream().map(LockerOrder::getId).toList())
                    .forEach(h -> histories.computeIfAbsent(h.getOrderId(), k -> new ArrayList<>()).add(h));
        }
        return orders.stream()
                .map(order -> toResponse(order, histories.getOrDefault(order.getId(), List.of())))
                .toList();
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

    /// `lockerId` null (hoặc ≤ 0) = toàn hệ thống; có tủ thì tủ phải tồn tại.
    @Transactional
    public PromotionView createPromotion(PromotionRequest request, Long createdByUserId) {
        Long lockerId = normalizeLockerId(request.lockerId());
        promotionViews.assertLockerExists(lockerId);
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
        promotion.setLockerId(lockerId);
        promotion.setTotalUsageLimit(request.totalUsageLimit());
        promotion.setPerUserLimit(request.perUserLimit());
        promotion.setDescription(request.description());
        promotion.setCreatedByUserId(createdByUserId);
        return promotionViews.view(promotionRepository.save(promotion), true);
    }

    /// Danh sách admin. `lockerId` > 0 ⇒ chỉ mã của tủ đó; `lockerId` = 0 hoặc `scope=GLOBAL` ⇒ chỉ mã toàn
    /// hệ thống; `scope=LOCKER` ⇒ mọi mã theo tủ.
    @Transactional(readOnly = true)
    public List<PromotionView> promotions(String code, String status, Long lockerId, String scope) {
        String q = code == null ? null : code.toLowerCase();
        boolean globalOnly = (lockerId != null && lockerId <= 0) || "GLOBAL".equalsIgnoreCase(scope);
        boolean lockerOnly = "LOCKER".equalsIgnoreCase(scope);
        return promotionViews.views(promotionRepository.findAll().stream()
                .filter(p -> !StringUtils.hasText(status) || status.equalsIgnoreCase(p.getStatus()))
                .filter(p -> q == null || p.getCode().toLowerCase().contains(q)
                        || (p.getName() != null && p.getName().toLowerCase().contains(q)))
                .filter(p -> !globalOnly || p.getLockerId() == null)
                .filter(p -> !lockerOnly || p.getLockerId() != null)
                .filter(p -> globalOnly || lockerId == null || lockerId <= 0 || lockerId.equals(p.getLockerId()))
                .toList(), true);
    }

    @Transactional(readOnly = true)
    public Promotion promotion(Long id) {
        return promotionRepository.findById(id).orElseThrow(() -> new NotFoundException("Promotion", id));
    }

    @Transactional(readOnly = true)
    public PromotionView promotionView(Long id) {
        return promotionViews.view(promotion(id), true);
    }

    /// Bản admin của một mã vừa đổi ở nơi khác (ảnh khuyến mãi).
    public PromotionView adminView(Promotion promotion) {
        return promotionViews.view(promotion, true);
    }

    /// Ghi đúng giá trị client gửi, kể cả `lockerId` null (= chuyển về toàn hệ thống). Khoá hàng mã để không
    /// ghi đè lượt dùng vừa được cộng bởi đơn đang tạo.
    @Transactional
    public PromotionView updatePromotion(Long id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Promotion", id));
        Long lockerId = normalizeLockerId(request.lockerId());
        if (lockerId != null && !lockerId.equals(promotion.getLockerId())) {
            promotionViews.assertLockerExists(lockerId);
        }
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
        promotion.setLockerId(lockerId);
        promotion.setTotalUsageLimit(request.totalUsageLimit());
        promotion.setPerUserLimit(request.perUserLimit());
        promotion.setDescription(request.description());
        return promotionViews.view(promotionRepository.save(promotion), true);
    }

    private static Long normalizeLockerId(Long lockerId) {
        return lockerId == null || lockerId <= 0 ? null : lockerId;
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
    public List<PromotionView> promotionsByStatus(String status) {
        return promotionViews.views(promotionRepository.findByStatus(status.toUpperCase()), true);
    }

    @Transactional(readOnly = true)
    public List<PromotionView> searchPromotions(String keyword) {
        String lower = keyword == null ? "" : keyword.toLowerCase();
        return promotionViews.views(promotionRepository.findAll().stream()
                .filter(p -> p.getCode().toLowerCase().contains(lower)
                        || (p.getName() != null && p.getName().toLowerCase().contains(lower)))
                .toList(), true);
    }

    /// Admin tra mã: chỉ kiểm hiệu lực/lượt, không kiểm tủ.
    @Transactional(readOnly = true)
    public Map<String, Object> validatePromotion(String code) {
        return validatePromotion(code, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validatePromotion(String code, Long lockerId, Long userId) {
        return validatePromotion(code, lockerId, userId, null, true);
    }

    /// Validate mã cho client: hiệu lực, tủ áp dụng (mã theo tủ mà `lockerId` thiếu hoặc khác ⇒
    /// `valid:false`, lý do nêu tên tủ), đơn tối thiểu khi có `orderTotal`, lượt dùng còn lại của `userId`.
    /// Trả `{code, valid, reason?, promotion}` — promotion là DTO đã lọc (không lộ createdByUserId…).
    @Transactional(readOnly = true)
    public Map<String, Object> validatePromotion(
            String code, Long lockerId, Long userId, BigDecimal orderTotal, boolean checkLocker) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code).orElse(null);
        String reason = checkLocker && rules.maxVouchersPerOrder() <= 0
                ? "hệ thống đang tạm ngưng áp dụng mã giảm giá"
                : benefits.ineligibleReason(promotion, lockerId, userId, orderTotal, checkLocker);
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("valid", reason == null);
        if (reason != null) {
            result.put("reason", "Mã " + code.toUpperCase() + ": " + reason);
        }
        result.put("promotion", promotion == null ? null : promotionSummary(promotion,
                promotionViews.lockers(java.util.Collections.singletonList(promotion.getLockerId()))));
        return result;
    }

    /// Các field promotion an toàn để trả cho client (mobile PromoCodeField đọc
    /// discountType/discountValue/maxDiscountAmount/minOrderAmount) + tủ áp dụng (null = toàn hệ thống).
    private Map<String, Object> promotionSummary(Promotion promotion, Map<Long, LockerInfo> lockers) {
        LockerInfo locker = promotion.getLockerId() == null ? null : lockers.get(promotion.getLockerId());
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
        summary.put("lockerCode", locker == null ? null : locker.code());
        summary.put("lockerName", locker == null ? null : locker.name());
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
        return voucherView(claim, promotion,
                promotionViews.lockers(java.util.Collections.singletonList(promotion.getLockerId())));
    }

    /// "Ví voucher" của user: các mã đã lưu kèm trạng thái SAVED/USED/EXPIRED. Có `lockerId` ⇒ chỉ mã toàn
    /// hệ thống + mã của đúng tủ đó.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myVouchers(Long userId, String status, Long lockerId) {
        List<PromotionClaim> claims = promotionClaimRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Promotion> promotions = new HashMap<>();
        promotionRepository.findAllById(claims.stream().map(PromotionClaim::getPromotionId).distinct().toList())
                .forEach(p -> promotions.put(p.getId(), p));
        List<Promotion> visible = promotions.values().stream()
                .filter(p -> availableAt(p, lockerId))
                .toList();
        Map<Long, LockerInfo> lockers = promotionViews.lockers(visible.stream().map(Promotion::getLockerId).toList());
        return claims.stream()
                .map(claim -> {
                    Promotion promotion = promotions.get(claim.getPromotionId());
                    return promotion == null || !availableAt(promotion, lockerId)
                            ? null : voucherView(claim, promotion, lockers);
                })
                .filter(java.util.Objects::nonNull)
                .filter(v -> !StringUtils.hasText(status) || status.equalsIgnoreCase((String) v.get("status")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myVouchers(Long userId, String status) {
        return myVouchers(userId, status, null);
    }

    /// `lockerId` null ⇒ mọi mã; có ⇒ mã toàn hệ thống hoặc mã của đúng tủ đó.
    private static boolean availableAt(Promotion promotion, Long lockerId) {
        return lockerId == null || promotion.getLockerId() == null || promotion.getLockerId().equals(lockerId);
    }

    private Map<String, Object> voucherView(PromotionClaim claim, Promotion promotion, Map<Long, LockerInfo> lockers) {
        // SAVED nhưng khuyến mãi đã hết hiệu lực -> hiển thị EXPIRED.
        String status = claim.getStatus();
        if ("SAVED".equals(status) && !promotion.activeNow()) {
            status = "EXPIRED";
        }
        Map<String, Object> view = new HashMap<>(promotionSummary(promotion, lockers));
        view.put("id", claim.getId());
        view.put("promotionId", promotion.getId());
        view.put("status", status);
        view.put("savedAt", claim.getCreatedAt());
        view.put("usedAt", claim.getUsedAt());
        return view;
    }

    /// Mã đang chạy cho app. Có `lockerId` ⇒ mã toàn hệ thống + mã của đúng tủ đó; không có ⇒ tất cả.
    @Transactional(readOnly = true)
    public List<PromotionView> activePromotions(Long lockerId) {
        return promotionViews.views(promotionRepository.findByStatus("ACTIVE").stream()
                .filter(Promotion::activeNow)
                .filter(p -> availableAt(p, lockerId))
                .toList(), false);
    }

    @Transactional(readOnly = true)
    public List<PromotionView> activePromotions() {
        return activePromotions(null);
    }

    // Reservation TTL: an unconfirmed (INITIALIZED) order keeps its send cell in
    // RESERVED. If the customer never drops their items within the hold window,
    // sweep the order to CANCELED *and release the cell* — otherwise the cell
    // stays stuck RESERVED forever (the old version only flipped the status).
    @Transactional
    public Map<String, Object> autoCancelUnconfirmedOrders() {
        int autoCancelHours = rules.autoCancelHours();
        int autoCancelUnpaidMinutes = rules.autoCancelUnpaidMinutes();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffPaid = now.minusHours(autoCancelHours);
        LocalDateTime cutoffUnpaid = now.minusMinutes(autoCancelUnpaidMinutes);
        int canceled = 0;
        // Đơn đã trả tiền bị tự huỷ: nơi gọi tạo yêu cầu hoàn tiền SAU khi giao dịch này commit.
        List<Long> paidCanceled = new ArrayList<>();
        for (LockerOrder order : orderRepository.findByStatusOrderByCreatedAtDesc("INITIALIZED")) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            boolean paid = isOrderPaid(order);
            LocalDateTime cutoff = paid ? cutoffPaid : cutoffUnpaid;
            if (order.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            order.setPinCode(null);
            releaseBoxes(order);
            String reason = paid
                    ? "Tự hủy: không xác nhận bỏ đồ trong " + autoCancelHours + " giờ; đã nhả ô"
                    : "Tự hủy: chưa thanh toán trong " + autoCancelUnpaidMinutes + " phút; đã nhả ô";
            transition(
                    order,
                    "CANCELED",
                    null,
                    null,
                    reason);
            if (paid) {
                paidCanceled.add(order.getId());
            }
            canceled++;
        }
        if (canceled > 0) {
            log.info("Auto-canceled unconfirmed orders and released their cells: {}", canceled);
        }
        return Map.of("canceledOrders", canceled, "paidCanceledOrderIds", paidCanceled);
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
        int overdueReleaseHours = rules.overdueReleaseHours();
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
            boolean isRental = "RENTAL".equalsIgnoreCase(order.getType())
                    || "RENTAL".equalsIgnoreCase(order.getServiceCategory());
            if (isRental && !rules.rentalAutoReleaseEnabled()) {
                // Đơn thuê tủ không tự động dời về kho theo cấu hình admin — tiếp tục giữ tại tủ
                continue;
            }
            moveToStorage(order, null,
                    "Quá hạn lấy hàng quá " + overdueReleaseHours
                            + " giờ: đồ chuyển vào kho lưu trữ, ô đã giải phóng");
            expired++;
        }
        if (expired > 0) {
            log.info("Overdue orders moved to storage (EXPIRED) and cells released: {}", expired);
        }
        return Map.of("expiredOrders", expired);
    }

    /// Dời đồ về kho (EXPIRED): chốt phí quá hạn, nhả ô, cắt tham chiếu ô và PIN, báo khách.
    /// Dùng chung cho job quá hạn và admin đổi tay.
    private OrderResponse moveToStorage(LockerOrder order, Long actorId, String note) {
        BigDecimal overtime = calculatePickupOvertimeFee(order);
        if (overtime.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentExtra = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
            if (currentExtra.compareTo(overtime) < 0) {
                BigDecimal diff = overtime.subtract(currentExtra);
                order.setExtraFee(overtime);
                order.setTotalPrice(order.getTotalPrice().add(diff));
                if (order.getOriginalPrice() != null) {
                    order.setOriginalPrice(order.getOriginalPrice().add(diff));
                }
                order.setPaymentStatus("UNPAID");
                order.setPaidAt(null);
            }
        }
        releaseBoxes(order);
        // Ô đã trả về pool nên tham chiếu box phải cắt — tránh double-release
        // giải phóng nhầm ô đã được cấp cho đơn khác ở các lệnh sau này.
        order.setSendBoxId(null);
        order.setReceiveBoxId(null);
        order.setPinCode(null);
        OrderResponse response = transition(order, "EXPIRED", actorId, null, note);
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
        return response;
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
        // AWAITING_DISPATCH: đơn drone giữ ô ở cả tủ gửi lẫn tủ nhận suốt thời gian chờ
        // đội bay; thiếu trạng thái này thì job coi các ô đó là mồ côi và nhả mất.
        for (String status : List.of("INITIALIZED", "AWAITING_DISPATCH", "STORING", "RETURNED")) {
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
            if (order.getReservedBoxId() != null) {
                heldBoxIds.add(order.getReservedBoxId());
            }
            if (order.getSourceBoxId() != null) {
                heldBoxIds.add(order.getSourceBoxId());
            }
        }

        // Đơn drone đã huỷ nhưng kiện còn nằm trong ô gửi chờ trả cho người gửi.
        for (LockerOrder order : orderRepository
                .findByTypeAndStatusAndParcelDroppedAtIsNotNullAndParcelReturnedAtIsNull("DRONE_DELIVERY", "CANCELED")) {
            if (order.getSourceBoxId() != null) {
                heldBoxIds.add(order.getSourceBoxId());
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

    /// Nút "Pickup Reminders" ở /admin/scheduler: chạy đúng job cron (gửi nhắc thật), trả số nhắc đã gửi.
    @Transactional
    public Map<String, Object> pickupReminders() {
        return Map.of("reminders", sendPickupReminders());
    }

    private OrderResponse transition(
            LockerOrder order, String newStatus, Long actorId, Long receiveBoxId, String note) {
        String oldStatus = order.getStatus();
        order.setStatus(newStatus.toUpperCase());
        // Mọi đường huỷ trong OrderService (khách huỷ, admin/PATCH đổi trạng thái, tự huỷ, báo lỗi ô) đi qua
        // đây: trả lượt mã giảm giá + hoàn điểm thưởng (idempotent).
        if ("CANCELED".equals(order.getStatus()) && !"CANCELED".equalsIgnoreCase(oldStatus)) {
            benefits.releaseBenefits(order);
        }
        // Đơn drone kết thúc thì chặng giao kết thúc theo. Client hiển thị đơn drone
        // bằng deliveryStage: để nguyên READY_FOR_PICKUP thì đơn khách đã lấy hàng vẫn
        // hiện "Chờ nhận hàng", đơn đã huỷ vẫn hiện "Chờ điều phối".
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())
                && Set.of("COMPLETED", "CANCELED", "EXPIRED").contains(order.getStatus())) {
            order.setDeliveryStage(order.getStatus());
        }
        if (actorId != null && !"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
            order.setStaffId(actorId);
        }
        if (receiveBoxId != null) {
            order.setReceiveBoxId(receiveBoxId);
        }
        boolean justCompleted = "COMPLETED".equals(order.getStatus()) && !"COMPLETED".equalsIgnoreCase(oldStatus);
        if (justCompleted && order.getCompletedAt() == null) {
            order.setCompletedAt(LocalDateTime.now());
        }
        LockerOrder saved = orderRepository.save(order);
        addHistory(saved.getId(), oldStatus, saved.getStatus(), actorId, note);
        publishStatusChanged(saved, oldStatus);
        if (justCompleted) {
            publishCompletedAfterCommit(saved);
        }
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
        return Math.max(1, Math.min(rules.rentalMaxHours(), hours));
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
            BigDecimal price = rules.storageItemFee().multiply(quantity); // phí lưu trữ mỗi món (admin cấu hình)
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

    private void releaseBoxes(LockerOrder order) {
        Set<Long> boxIds = new LinkedHashSet<>();
        boxIds.add(order.getSendBoxId());
        boxIds.add(order.getReceiveBoxId());
        boxIds.add(order.getReservedBoxId());
        boxIds.add(order.getSourceBoxId());
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
        // Đơn đã kết thúc: phí quá hạn đã chốt, không tính tiếp theo đồng hồ.
        if ("COMPLETED".equals(order.getStatus()) || "CANCELED".equals(order.getStatus())) {
            return order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
        }
        // Nếu đơn đã thanh toán xong và đang trong thời gian ân hạn (grace period) để lấy đồ:
        // đóng băng phí quá giờ tại mức hiện tại, không lũy kế thêm tiền trong lúc khách mở ô.
        int graceMinutes = rules.overtimeGracePeriodMinutes();
        if (graceMinutes > 0 && order.getPaidAt() != null && "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            if (LocalDateTime.now().isBefore(order.getPaidAt().plusMinutes(graceMinutes))) {
                return order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
            }
        }
        long minutes = Math.max(0, ChronoUnit.MINUTES.between(order.getPickupDeadline(), LocalDateTime.now()));
        if (isRentalOrder(order)) {
            return rentalOvertimeFee(order, minutes);
        }
        long hours = minutes / 60;
        BigDecimal raw = BigDecimal.valueOf(hours * rules.overtimeFeePerHour());
        // Trần % tính trên giá CHƯA giảm: mã giảm giá/điểm thưởng không làm giảm (hay xoá hẳn, với đơn 0đ)
        // phí quá hạn. Phí quá hạn đã cộng vào giá trước đó không tính vào gốc, không thì trần tự tăng sau
        // mỗi lần tính.
        BigDecimal currentExtra = order.getExtraFee() == null ? BigDecimal.ZERO : order.getExtraFee();
        BigDecimal priceBase = (order.getOriginalPrice() != null && order.getOriginalPrice().signum() > 0
                ? order.getOriginalPrice()
                : order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice())
                .subtract(currentExtra)
                .max(BigDecimal.ZERO);
        BigDecimal percentageCap =
                priceBase.multiply(BigDecimal.valueOf(rules.maxOvertimePercent())).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return raw.min(rules.maxOvertimeFee()).min(percentageCap);
    }

    /// Ở quá hạn thuê tính như thuê thêm: mỗi giờ đã bắt đầu tính trọn giờ, theo giá thuê của loại ô ×
    /// tỉ lệ admin đặt (≥ 100%) — không bao giờ rẻ hơn gia hạn. Không áp trần phí quá hạn của đơn gửi hàng.
    private BigDecimal rentalOvertimeFee(LockerOrder order, long minutesOver) {
        long hours = (minutesOver + 59) / 60;
        if (hours <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourly = rules.rentalRate(cellTypeOfRental(order))
                .multiply(BigDecimal.valueOf(rules.rentalOvertimeRatePercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .max(BigDecimal.valueOf(rules.overtimeFeePerHour()));
        return hourly.multiply(BigDecimal.valueOf(hours));
    }

    private static boolean isRentalOrder(LockerOrder order) {
        return "RENTAL".equalsIgnoreCase(order.getType()) || "RENTAL".equalsIgnoreCase(order.getServiceCategory());
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
        if (!rules.requirePaymentBeforeDrop()) {
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

    private boolean isOrderPaid(LockerOrder order) {
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return true;
        }
        BigDecimal total = order.getTotalPrice();
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal pa = order.getPaidAmount();
        return pa != null && pa.compareTo(total) >= 0;
    }

    private void assertPaidBeforeStorageCompletion(LockerOrder order) {
        if (!rules.requirePaymentBeforeDrop()) {
            return;
        }
        if (!isOrderPaid(order)) {
            throw new BusinessException(
                    "ORDER_UNPAID",
                    "Vui lòng thanh toán đơn trước khi kết thúc thuê/trả ô.");
        }
    }

    private void assertPaidBeforePickup(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            if (!"READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage())) {
                return;
            }
            if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                throw new BusinessException(
                        "DRONE_PAYMENT_REQUIRED_BEFORE_PICKUP",
                        "Vui lòng thanh toán đơn drone trước khi mở tủ nhận hàng.");
            }
            return;
        }
        if (!isOrderPaid(order)) {
            throw new BusinessException(
                    "ORDER_UNPAID",
                    "Vui lòng thanh toán phụ phí quá hạn trước khi nhận hàng.");
        }
    }

    private void assertDroneCancelable(LockerOrder order) {
        if (!"DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return;
        }
        // Người gửi huỷ được tới khi KTV drone đã cầm kiện (PICKED_UP); sau đó chỉ đội bay
        // hoặc admin đóng đơn.
        String stage = order.getDeliveryStage() == null ? "" : order.getDeliveryStage().toUpperCase();
        if (!"AWAITING_DISPATCH".equals(stage) && !"PICKUP_ASSIGNED".equals(stage)) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID",
                    "Drone order can only be canceled before the technician has picked up the parcel.");
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
        return new OrderSummary(order.getId(), order.getUserId(), order.getStatus(), order.getTotalPrice(), order.getType(), order.getOrderCode());
    }

    private OrderResponse toResponse(LockerOrder order) {
        return toResponse(order, historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }

    /// `historyOldestFirst`: lịch sử trạng thái của đơn, cũ → mới — để dựng mốc thời gian.
    private OrderResponse toResponse(LockerOrder order, List<OrderStatusHistory> historyOldestFirst) {
        List<OrderDetailResponse> details =
                detailRepository.findByOrderId(order.getId()).stream()
                        .map(d -> new OrderDetailResponse(d.getServiceId(), d.getQuantity(), d.getPrice(), d.getDescription()))
                        .toList();
        BigDecimal overtimeFee = calculatePickupOvertimeFee(order);
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
                details,
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverEmail(),
                order.getCustomerNote(),
                order.getDeliveryAddress(),
                order.getRentalDurationHours(),
                OrderAccessPolicy.blockReason(order, rules, LocalDateTime.now(), overtimeFee),
                overtimeFee,
                paidAmountOf(order),
                amountDueOf(order),
                OrderDetailViews.milestones(order, historyOldestFirst),
                OrderDetailViews.parcel(order),
                order.getPaidAt(),
                order.getDropOpenedAt(),
                order.getIntendedReceiveAt(),
                order.getDelegateName(),
                order.getDelegatePhone(),
                order.getDelegateNote(),
                order.getDelegatedAt(),
                order.getSourceLockerId(),
                order.getDestinationLockerId(),
                order.getRouteDistanceMeters(),
                order.getParcelDroppedAt(),
                order.getParcelReturnedAt(),
                order.getDeliveryBlockReason(),
                DroneFailureReasons.label(order.getDeliveryBlockReason()),
                order.getDeliveryBlockReason() == null ? null : order.getDeliveryBlockedAt(),
                order.getFailureReason(),
                DroneFailureReasons.label(order.getFailureReason()),
                order.getFailureReason() == null ? null : order.getFailedAt(),
                order.getPointsUsed() == null ? 0 : order.getPointsUsed(),
                order.getPointsDiscount() == null ? BigDecimal.ZERO : order.getPointsDiscount(),
                overtimeRatePerHour(order),
                !isRentalOrder(order));
    }

    /// Đơn giá mỗi giờ quá hạn hiển thị cho khách. Đơn thuê chỉ tra loại ô khi đơn có hạn (đã bắt đầu thuê).
    private BigDecimal overtimeRatePerHour(LockerOrder order) {
        if (!isRentalOrder(order) || order.getPickupDeadline() == null
                || "COMPLETED".equals(order.getStatus()) || "CANCELED".equals(order.getStatus())) {
            return BigDecimal.valueOf(rules.overtimeFeePerHour());
        }
        return rentalOvertimeFee(order, 60);
    }

    private static BigDecimal paidAmountOf(LockerOrder order) {
        return order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
    }

    /// Phần còn phải trả = tổng hiện tại trừ phần đã trả, không âm.
    private static BigDecimal amountDueOf(LockerOrder order) {
        BigDecimal total = order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice();
        BigDecimal due = total.subtract(paidAmountOf(order));
        return due.compareTo(BigDecimal.ZERO) > 0 ? due : BigDecimal.ZERO;
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
                order.getSourceLockerId(),
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
        return OrderNextActions.nextAction(order);
    }

    private String nextActionMessage(LockerOrder order) {
        return OrderNextActions.nextActionMessage(order);
    }

    private boolean paymentRequired(LockerOrder order) {
        return OrderNextActions.paymentRequired(order);
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
        payload.put("orderCode", order.getOrderCode());
        payload.put("userId", order.getUserId());
        payload.put("oldStatus", oldStatus);
        payload.put("newStatus", order.getStatus());
        payload.put("status", order.getStatus());
        payload.put("type", order.getType());
        payload.put("paymentStatus", order.getPaymentStatus());
        publish(DomainEventNames.ORDER_STATUS_CHANGED, order, payload);
    }

    /// `order.completed` — mọi đường hoàn tất đơn đều đi qua transition() nên chỉ phát ở đây. Bên
    /// nhận (loyalty cộng điểm) chống trùng theo orderId. Phát sau khi giao dịch commit để đơn bị
    /// rollback không sinh sự kiện. Số tiền chỉ nằm ở sự kiện này, không thêm vào
    /// order.status.changed (sự kiện đó dùng cho cập nhật realtime trên app/web).
    private void publishCompletedAfterCommit(LockerOrder order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderCode", order.getOrderCode());
        payload.put("userId", order.getUserId());
        payload.put("type", order.getType());
        payload.put("paidAmount", paidAmountOf(order));
        payload.put("totalPrice", order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice());
        payload.put("paymentStatus", order.getPaymentStatus());
        payload.put("completedAt", order.getCompletedAt() == null ? null : order.getCompletedAt().toString());
        Runnable send = () -> publish(DomainEventNames.ORDER_COMPLETED, order, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    private void notifyStatus(LockerOrder order, String oldStatus) {
        String[] text = statusNotification(order);
        if (text == null) {
            return;
        }
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(order.getUserId(), text[0], text[1], "ORDER_STATUS", order.getId(), "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not call notification-service for order {}: {}", order.getId(), ex.getMessage());
        }
    }

    /// {tiêu đề, nội dung} báo cho chủ đơn khi đổi trạng thái; null khi bước đó đã có thông báo riêng
    /// (gửi hàng vào ô → "Đã gửi hàng vào tủ", quá hạn → "Đồ đã chuyển vào kho").
    static String[] statusNotification(LockerOrder order) {
        String code = order.getOrderCode();
        boolean rental = "RENTAL".equalsIgnoreCase(order.getType());
        return switch (String.valueOf(order.getStatus())) {
            case "STORING" -> rental
                    ? new String[] {"Bắt đầu thuê ô",
                            "Đơn thuê " + code + " đã bắt đầu. Dùng mã của đơn để mở ô nhiều lần tới khi hết giờ."}
                    : "SEND".equalsIgnoreCase(order.getType()) ? null
                    : new String[] {"Đã cất đồ vào tủ", "Đồ của đơn " + code + " đã được cất vào ô."};
            case "COMPLETED" -> new String[] {"Đơn hàng hoàn tất",
                    "Đơn " + code + " đã hoàn tất. Cảm ơn bạn đã dùng Lock.R!"};
            case "CANCELED" -> new String[] {"Đơn hàng đã huỷ", "Đơn " + code + " đã được huỷ."
                    + ("PAID".equalsIgnoreCase(order.getPaymentStatus())
                            || "REFUND_PENDING".equalsIgnoreCase(order.getPaymentStatus())
                            ? " Khoản đã thanh toán sẽ được hoàn theo chính sách hoàn tiền." : "")};
            case "EXPIRED" -> null;
            default -> new String[] {"Cập nhật đơn hàng", "Đơn " + code + " đã được cập nhật trạng thái."};
        };
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

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Long firstNonNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }

    @Transactional(readOnly = true)
    public ActiveBoxOrderResponse getActiveOrderByBox(Long boxId) {
        List<LockerOrder> list = orderRepository.findActiveByBoxId(boxId);
        if (list.isEmpty()) {
            return null;
        }
        LockerOrder order = list.get(0);
        return new ActiveBoxOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getUserId(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getType(),
                order.getStatus(),
                order.getPinCode(),
                boxId
        );
    }

    @Transactional
    public OrderResponse relocateBox(Long orderId, Long newBoxId, Integer newBoxNumber) {
        LockerOrder order = find(orderId);
        Long oldBoxId = activeBoxId(order);
        if (oldBoxId != null && oldBoxId.equals(order.getSendBoxId())) {
            order.setSendBoxId(newBoxId);
        } else if (oldBoxId != null && oldBoxId.equals(order.getReceiveBoxId())) {
            order.setReceiveBoxId(newBoxId);
        } else {
            order.setReservedBoxId(newBoxId);
        }
        String newPin = generatePinCode();
        order.setPinCode(newPin);
        order.setPinCodeIssuedAt(LocalDateTime.now());
        String boxLabel = newBoxNumber != null ? ("#" + newBoxNumber) : ("#" + newBoxId);
        addHistory(order.getId(), order.getStatus(), order.getStatus(), null,
                "KTV điều chuyển hàng sang ô " + boxLabel + " do sự cố ô cũ");
        LockerOrder saved = orderRepository.save(order);

        String where = lockerLabel(saved.getLockerId());
        String msg = "Đơn hàng " + saved.getOrderCode() + " tại " + where + " đã được KTV chuyển sang ô " + boxLabel + ". Mã mở tủ mới của bạn là: " + newPin;
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    saved.getUserId(),
                    "Đơn hàng được chuyển sang ô mới",
                    msg,
                    "ORDER_RELOCATED",
                    saved.getId(),
                    "ORDER"
            ));
        } catch (Exception ex) {
            log.warn("Could not notify customer about relocated order {}: {}", saved.getId(), ex.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", saved.getId());
        payload.put("userId", saved.getUserId());
        payload.put("oldBoxId", oldBoxId);
        payload.put("newBoxId", newBoxId);
        payload.put("newBoxNumber", newBoxNumber);
        payload.put("pinCode", newPin);
        publish(DomainEventNames.ORDER_BOX_RELOCATED, saved, payload);

        return toResponse(saved);
    }

    @Transactional
    public OrderResponse directHandover(Long orderId, String otp) {
        LockerOrder order = find(orderId);
        assertHandoverVerified(order, otp);
        order.setPinCode(null);
        addHistory(order.getId(), order.getStatus(), "COMPLETED", null,
                "KTV đã bàn giao hàng trực tiếp cho khách hàng tại Kiosk do sự cố");
        return transition(order, "COMPLETED", null, order.getReceiveBoxId(), "KTV bàn giao hàng trực tiếp cho khách tại Kiosk");
    }

    /// KTV trao hàng tận tay thay cho việc khách tự mở ô, nên phải chắc đó là người nhận: luôn đòi
    /// mã mở ô hiện hành của đơn (PIN 6 số, hoặc mã QR trong app) do khách đọc/đưa cho KTV.
    private void assertHandoverVerified(LockerOrder order, String otp) {
        if (!StringUtils.hasText(otp)) {
            throw new BusinessException(
                    "HANDOVER_OTP_REQUIRED", "Cần mã mở tủ của khách (PIN hoặc QR) để bàn giao trực tiếp.");
        }
        if (!StringUtils.hasText(order.getPinCode())) {
            throw new BusinessException(
                    "HANDOVER_OTP_UNAVAILABLE",
                    "Đơn không còn mã mở tủ hiệu lực nên không xác minh được người nhận.",
                    HttpStatus.CONFLICT);
        }
        String code = otp.trim();
        boolean matches = code.startsWith(QrTokenService.PREFIX)
                ? qrTokenService.matches(code, order.getId(), order.getPinCode())
                : MessageDigest.isEqual(
                        code.getBytes(StandardCharsets.UTF_8),
                        order.getPinCode().trim().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new BusinessException("INVALID_OTP", "Mã xác nhận bàn giao không chính xác");
        }
    }

    @Transactional
    public OrderResponse hubEscrow(Long orderId, String sealNumber) {
        LockerOrder order = find(orderId);
        order.setPinCode(null);
        addHistory(order.getId(), order.getStatus(), order.getStatus(), null,
                "Hàng đã được niêm phong (Mã Seal: " + sealNumber + ") chuyển về Hub lưu trữ do Kiosk hết ô trống");
        LockerOrder saved = orderRepository.save(order);

        String msg = "Đơn hàng " + saved.getOrderCode() + " đã được niêm phong an toàn (Seal: " + sealNumber + ") chuyển về Hub. CSKH sẽ liên hệ hỗ trợ giao hàng tận nơi.";
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    saved.getUserId(),
                    "Đơn hàng được niêm phong về Hub",
                    msg,
                    "ORDER_ESCROWED",
                    saved.getId(),
                    "ORDER"
            ));
        } catch (Exception ex) {
            log.warn("Could not notify customer about hub escrow order {}: {}", saved.getId(), ex.getMessage());
        }
        return toResponse(saved);
    }

    @Transactional
    public void addIncidentHistory(Long orderId, String note) {
        LockerOrder order = find(orderId);
        addHistory(order.getId(), order.getStatus(), order.getStatus(), null, note);
    }

    @Transactional
    public void updatePaymentStatus(Long orderId, String paymentStatus, String note, Long actorUserId) {
        LockerOrder order = find(orderId);
        boolean refunded = "REFUNDED".equalsIgnoreCase(paymentStatus)
                && !"REFUNDED".equalsIgnoreCase(order.getPaymentStatus());
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
        if (refunded) {
            // Tiền đã hoàn cho khách ⇒ trả lượt mã giảm giá + hoàn điểm đã dùng (idempotent).
            benefits.releaseBenefits(order);
        }
        if (StringUtils.hasText(note)) {
            addHistory(order.getId(), order.getStatus(), order.getStatus(), actorUserId, note);
        }
    }
}
