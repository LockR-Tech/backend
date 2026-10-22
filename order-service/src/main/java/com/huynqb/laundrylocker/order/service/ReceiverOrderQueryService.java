package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderDetailResponse;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/// Đơn nhìn từ phía người nhận: "Hàng đến" (đơn người khác gửi cho tôi) và "Được ủy quyền" (đơn
/// người khác nhờ tôi lấy hộ). Khớp theo tài khoản người nhận hoặc số điện thoại của tài khoản.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiverOrderQueryService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private static final List<String> ACTIVE_INCOMING_STATUSES =
            List.of("INITIALIZED", "AWAITING_DISPATCH", "STORING", "RETURNED");
    private static final List<String> ACTIVE_DELEGATED_STATUSES = List.of("STORING", "RETURNED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELED", "EXPIRED");
    public static final String VIEWER_RECEIVER = "RECEIVER";
    public static final String VIEWER_DELEGATE = "DELEGATE";
    /// Giá trị không thể trùng số điện thoại nào — tránh `in ()` khi tài khoản chưa có số.
    private static final Set<String> NO_PHONE = Set.of("#");

    private final LockerOrderRepository orderRepository;
    private final UserClient userClient;
    private final AdminReferenceResolver references;
    private final QrTokenService qrTokenService;
    private final OrderStatusHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public List<ReceiverOrderResponse> incoming(Long userId, boolean activeOnly, Integer limit) {
        requireUser(userId);
        String phone = callerPhone(userId);
        Set<String> phones = phoneVariants(phone);
        List<LockerOrder> orders = orderRepository.findIncoming(
                userId, phones, !activeOnly, ACTIVE_INCOMING_STATUSES, PageRequest.of(0, clamp(limit)));
        List<LockerOrder> visible = orders.stream()
                // Đơn tới tay tôi chỉ vì được ủy quyền thì nằm ở danh sách "Được ủy quyền".
                .filter(order -> isReceiverAccount(order, userId) || !delegatedTo(order, phone))
                .toList();
        return build(visible, order -> {
            // Đơn đã ủy quyền cho người khác: mã mới thuộc về người lấy hộ.
            boolean pinVisible = order.getDelegatePhone() == null || delegatedTo(order, phone);
            return new View(pinVisible, false);
        });
    }

    @Transactional(readOnly = true)
    public List<ReceiverOrderResponse> delegatedToMe(Long userId, boolean activeOnly, Integer limit) {
        requireUser(userId);
        String phone = callerPhone(userId);
        if (PhoneNumbers.normalize(phone) == null) {
            return List.of();
        }
        List<LockerOrder> orders = orderRepository.findDelegatedTo(
                phoneVariants(phone), !activeOnly, ACTIVE_DELEGATED_STATUSES, PageRequest.of(0, clamp(limit)));
        return build(orders, order -> new View(true, true));
    }

    /// Chi tiết một đơn cho người nhận hoặc người lấy hộ. Quyền xem khớp đúng hai danh sách:
    /// đơn hiện ở `incoming` (người nhận theo tài khoản hoặc SĐT, trừ đơn tới tay tôi chỉ vì được
    /// ủy quyền) xem với vai RECEIVER; đơn hiện ở `delegated-to-me` (SĐT người lấy hộ là số của
    /// tôi) xem với vai DELEGATE. Mọi trường hợp khác — kể cả đơn không tồn tại — trả 404 như nhau
    /// để không lộ đơn của người khác.
    @Transactional(readOnly = true)
    public ReceiverOrderDetailResponse detail(Long userId, Long orderId) {
        requireUser(userId);
        String phone = callerPhone(userId);
        LockerOrder order = orderId == null ? null : orderRepository.findById(orderId).orElse(null);
        Viewer viewer = order == null ? null : viewerOf(order, userId, phone);
        if (viewer == null) {
            throw new NotFoundException("Order", orderId);
        }
        Long lockerId = lockerId(order);
        Long boxId = boxId(order);
        boolean drone = isDrone(order);
        Long sourceLockerId = drone ? order.getSourceLockerId() : null;
        Lookup<LockerInfo> lockers = references.lockers(Arrays.asList(lockerId, sourceLockerId));
        Lookup<BoxInfo> boxes = references.boxes(Collections.singletonList(boxId));
        Lookup<UserSummary> users = references.users(Collections.singletonList(order.getUserId()));
        LockerInfo locker = lockers.get(lockerId);
        LockerInfo source = lockers.get(sourceLockerId);
        BoxInfo box = boxes.get(boxId);
        UserSummary sender = users.get(order.getUserId());
        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());

        View view = viewer.view();
        boolean receiver = VIEWER_RECEIVER.equals(viewer.role());
        boolean ready = readyForPickup(order) && StringUtils.hasText(order.getPinCode());
        boolean showPin = ready && view.pinVisible();
        boolean active = !TERMINAL_STATUSES.contains(upper(order.getStatus()));
        return new ReceiverOrderDetailResponse(
                order.getId(),
                order.getOrderCode(),
                order.getType(),
                order.getStatus(),
                order.getDeliveryStage(),
                viewer.role(),
                order.getUserId(),
                firstText(order.getSenderName(), sender == null ? null : sender.fullName()),
                firstText(order.getSenderPhone(), sender == null ? null : sender.phoneNumber()),
                receiver ? textOrNull(order.getSenderEmail()) : null,
                order.getReceiverName(),
                order.getReceiverPhone(),
                receiver ? textOrNull(order.getReceiverEmail()) : null,
                lockerId,
                locker == null ? null : locker.code(),
                locker == null ? null : locker.name(),
                locker == null ? null : locker.address(),
                locker == null ? null : locker.latitude(),
                locker == null ? null : locker.longitude(),
                boxId,
                box == null ? null : box.boxNumber(),
                box == null ? null : box.size(),
                box == null ? null : box.cellType(),
                sourceLockerId,
                source == null ? null : source.code(),
                source == null ? null : source.name(),
                source == null ? null : source.address(),
                drone ? order.getRouteDistanceMeters() : null,
                order.getPickupDeadline(),
                order.getPickupDeadline() != null && LocalDateTime.now().isAfter(order.getPickupDeadline()),
                ready,
                showPin ? order.getPinCode() : null,
                showPin ? qrTokenService.issue(order.getId(), order.getPinCode()) : null,
                order.getIntendedReceiveAt(),
                order.getRentalDurationHours(),
                textOrNull(order.getDeliveryAddress()),
                order.getPaymentStatus(),
                order.getDelegatePhone() != null,
                view.delegateDetails() ? order.getDelegateName() : null,
                view.delegateDetails() ? order.getDelegatePhone() : null,
                view.delegateDetails() ? order.getDelegateNote() : null,
                // Lúc ủy quyền cho người nhận biết vì sao mã không còn hiện ở chỗ họ.
                order.getDelegatedAt(),
                textOrNull(order.getCustomerNote()),
                OrderDetailViews.parcel(order),
                order.getDeliveryBlockReason(),
                DroneFailureReasons.label(order.getDeliveryBlockReason()),
                order.getDeliveryBlockReason() == null ? null : order.getDeliveryBlockedAt(),
                order.getFailureReason(),
                DroneFailureReasons.label(order.getFailureReason()),
                order.getFailureReason() == null ? null : order.getFailedAt(),
                order.getParcelReturnedAt(),
                drone && active ? DroneDeliveryQueryService.etaMinutes(order.getDeliveryStage()) : null,
                // Màn theo dõi drone chỉ mở cho người gửi và tài khoản người nhận của đơn.
                drone && active && userId.equals(order.getReceiverUserId()),
                drone ? DroneDeliveryQueryService.stageChanges(reversed(history)) : List.of(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                OrderDetailViews.milestones(order, history));
    }

    /// Hàng đã nằm trong ô và mở được bằng PIN/QR của đơn.
    static boolean readyForPickup(LockerOrder order) {
        String status = order.getStatus();
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return "STORING".equalsIgnoreCase(status) && "READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage());
        }
        return "STORING".equalsIgnoreCase(status) || "RETURNED".equalsIgnoreCase(status);
    }

    private List<ReceiverOrderResponse> build(List<LockerOrder> orders, Function<LockerOrder, View> views) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> lockerIds = new HashSet<>();
        Set<Long> boxIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (LockerOrder order : orders) {
            lockerIds.add(lockerId(order));
            boxIds.add(boxId(order));
            userIds.add(order.getUserId());
        }
        Lookup<LockerInfo> lockers = references.lockers(lockerIds);
        Lookup<BoxInfo> boxes = references.boxes(boxIds);
        Lookup<UserSummary> users = references.users(userIds);
        LocalDateTime now = LocalDateTime.now();
        List<ReceiverOrderResponse> result = new ArrayList<>(orders.size());
        for (LockerOrder order : orders) {
            result.add(toResponse(order, views.apply(order), lockers, boxes, users, now));
        }
        return result;
    }

    private ReceiverOrderResponse toResponse(
            LockerOrder order, View view, Lookup<LockerInfo> lockers, Lookup<BoxInfo> boxes,
            Lookup<UserSummary> users, LocalDateTime now) {
        Long lockerId = lockerId(order);
        Long boxId = boxId(order);
        LockerInfo locker = lockers.get(lockerId);
        BoxInfo box = boxes.get(boxId);
        UserSummary sender = users.get(order.getUserId());
        boolean ready = readyForPickup(order) && StringUtils.hasText(order.getPinCode());
        boolean showPin = ready && view.pinVisible();
        return new ReceiverOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getType(),
                order.getStatus(),
                order.getDeliveryStage(),
                order.getUserId(),
                sender == null || !StringUtils.hasText(sender.fullName()) ? null : sender.fullName(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                lockerId,
                locker == null ? null : locker.code(),
                locker == null ? null : locker.name(),
                locker == null ? null : locker.address(),
                locker == null ? null : locker.latitude(),
                locker == null ? null : locker.longitude(),
                boxId,
                box == null ? null : box.boxNumber(),
                order.getPickupDeadline(),
                order.getPickupDeadline() != null && now.isAfter(order.getPickupDeadline()),
                ready,
                showPin ? order.getPinCode() : null,
                showPin ? qrTokenService.issue(order.getId(), order.getPinCode()) : null,
                order.getPaymentStatus(),
                order.getDelegatePhone() != null,
                view.delegateDetails() ? order.getDelegateName() : null,
                view.delegateDetails() ? order.getDelegateNote() : null,
                view.delegateDetails() ? order.getDelegatedAt() : null,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt());
    }

    /// Số điện thoại của tài khoản gọi; null khi tài khoản chưa có số. Không tra được user-service
    /// thì báo lỗi thay vì trả danh sách rỗng gây hiểu nhầm "không có đơn".
    /// Người gọi là người nhận hoặc người lấy hộ của đơn — cùng quy tắc với [#detail].
    @Transactional(readOnly = true)
    public boolean isReceiverOrDelegate(LockerOrder order, Long userId) {
        return userId != null && viewerOf(order, userId, callerPhone(userId)) != null;
    }

    private String callerPhone(Long userId) {
        try {
            ApiResponse<UserSummary> response = userClient.getUser(userId);
            UserSummary user = response == null ? null : response.data();
            return user == null ? null : user.phoneNumber();
        } catch (Exception ex) {
            log.warn("Could not load phone number of user {}: {}", userId, ex.getMessage());
            throw new BusinessException(
                    "USER_LOOKUP_UNAVAILABLE",
                    "Không tra được thông tin tài khoản, vui lòng thử lại sau.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static Set<String> phoneVariants(String phone) {
        Set<String> variants = PhoneNumbers.variants(phone);
        return variants.isEmpty() ? NO_PHONE : variants;
    }

    /// Vai của người xem với đơn, hoặc null khi đơn không nằm trong danh sách nào của họ. So số
    /// điện thoại đúng như hai truy vấn danh sách (`findIncoming`, `findDelegatedTo`).
    static Viewer viewerOf(LockerOrder order, Long userId, String phone) {
        Set<String> phones = phoneVariants(phone);
        boolean receiverAccount = isReceiverAccount(order, userId);
        boolean receiverMatch = !userId.equals(order.getUserId())
                && (receiverAccount || storedPhoneIn(order.getReceiverPhone(), phones));
        if (receiverMatch && (receiverAccount || !delegatedTo(order, phone))) {
            // Đơn đã ủy quyền cho người khác: mã mới thuộc về người lấy hộ.
            boolean pinVisible = order.getDelegatePhone() == null || delegatedTo(order, phone);
            return new Viewer(VIEWER_RECEIVER, new View(pinVisible, false));
        }
        if (PhoneNumbers.normalize(phone) != null && storedPhoneIn(order.getDelegatePhone(), phones)) {
            return new Viewer(VIEWER_DELEGATE, new View(true, true));
        }
        return null;
    }

    private static boolean storedPhoneIn(String stored, Set<String> phones) {
        String compact = PhoneNumbers.stripSeparators(stored);
        return compact != null && phones.contains(compact);
    }

    private static boolean isDrone(LockerOrder order) {
        return "DRONE_DELIVERY".equalsIgnoreCase(order.getType());
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    private static String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(String preferred, String fallback) {
        String first = textOrNull(preferred);
        return first != null ? first : textOrNull(fallback);
    }

    private static boolean isReceiverAccount(LockerOrder order, Long userId) {
        return userId.equals(order.getReceiverUserId()) || userId.equals(order.getReceiverId());
    }

    private static boolean delegatedTo(LockerOrder order, String phone) {
        return order.getDelegatePhone() != null && PhoneNumbers.sameNumber(order.getDelegatePhone(), phone);
    }

    private static Long lockerId(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType()) && order.getDestinationLockerId() != null) {
            return order.getDestinationLockerId();
        }
        return order.getLockerId();
    }

    private static Long boxId(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId();
        }
        return order.getReceiveBoxId() != null ? order.getReceiveBoxId() : order.getSendBoxId();
    }

    private static int clamp(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("AUTH_REQUIRED", "Vui lòng đăng nhập.", HttpStatus.UNAUTHORIZED);
        }
    }

    private static <T> List<T> reversed(List<T> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    record View(boolean pinVisible, boolean delegateDetails) {
    }

    record Viewer(String role, View view) {
    }
}
