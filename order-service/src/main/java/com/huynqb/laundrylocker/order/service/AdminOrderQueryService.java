package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.dto.OrderDetailResponse;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.DroneInfo;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.Fees;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.LockerRef;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.PaymentInfo;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.Person;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.Receiver;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.StoreRef;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse.TimelineEvent;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderBriefResponse;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderDetail;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderDetailRepository;
import com.huynqb.laundrylocker.order.repository.OrderSpecifications;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Tìm kiếm/chi tiết đơn cho trang admin. Mỗi trang kết quả: 1 truy vấn đơn + 2 truy
/// vấn nạp theo lô (chi tiết, drone) + tối đa 5 lời gọi Feign theo lô.
@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "totalPrice", "paidAt", "completedAt", "id");

    private final LockerOrderRepository orderRepository;
    private final OrderDetailRepository detailRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DroneMissionRepository missionRepository;
    private final AdminReferenceResolver resolver;
    private final QrTokenService qrTokenService;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    public record SearchCriteria(
            int page,
            int size,
            List<String> statuses,
            List<String> types,
            List<String> paymentStatuses,
            String from,
            String to,
            Long userId,
            Long lockerId,
            Long storeId,
            String q,
            String sort) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> search(SearchCriteria criteria) {
        if (criteria.page() < 0) {
            throw new BusinessException("INVALID_PAGE", "page must be >= 0");
        }
        if (criteria.size() < 1 || criteria.size() > MAX_PAGE_SIZE) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        LocalDateTime from = time.parseFrom(criteria.from());
        LocalDateTime to = time.parseToExclusive(criteria.to());

        Collection<Long> lockerIdsOfStore = List.of();
        if (criteria.storeId() != null) {
            lockerIdsOfStore = resolver.allLockers().values().values().stream()
                    .filter(l -> criteria.storeId().equals(l.storeId()))
                    .map(LockerInfo::id)
                    .toList();
        }
        Long matchedUserId = looksLikePhone(criteria.q()) ? resolver.userIdByPhone(criteria.q().trim()) : null;

        Page<LockerOrder> page = orderRepository.findAll(
                OrderSpecifications.all(
                        OrderSpecifications.statusIn(criteria.statuses()),
                        OrderSpecifications.typeIn(criteria.types()),
                        OrderSpecifications.paymentStatusIn(criteria.paymentStatuses()),
                        OrderSpecifications.createdBetween(from, to),
                        OrderSpecifications.user(criteria.userId()),
                        criteria.lockerId() == null ? null : OrderSpecifications.lockerIn(List.of(criteria.lockerId())),
                        OrderSpecifications.store(criteria.storeId(), lockerIdsOfStore),
                        OrderSpecifications.keyword(criteria.q(), matchedUserId)),
                PageRequest.of(criteria.page(), criteria.size(), sort(criteria.sort())));

        List<AdminOrderResponse> content = enrich(page.getContent(), false);
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse detail(Long id) {
        LockerOrder order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
        return enrich(List.of(order), true).get(0);
    }

    @Transactional(readOnly = true)
    public List<OrderBriefResponse> briefs(Collection<Long> ids) {
        List<Long> distinct = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return orderRepository.findAllById(distinct).stream()
                .map(o -> new OrderBriefResponse(
                        o.getId(), o.getOrderCode(), o.getUserId(), o.getType(), o.getServiceCategory(), o.getStatus(),
                        o.getPaymentStatus(), o.getDeliveryStage(), o.getLockerId(), o.getDestinationLockerId(),
                        o.getStoreId(), o.getTotalPrice(), o.getExtraFee(), o.getCreatedAt(), o.getCompletedAt(),
                        o.getPaidAt()))
                .toList();
    }

    Sort sort(String sort) {
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String requested = parts[0].trim();
            if (!SORTABLE.contains(requested)) {
                throw new BusinessException("INVALID_SORT", "sort must be one of " + SORTABLE);
            }
            field = requested;
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
                direction = Sort.Direction.ASC;
            }
        }
        Sort result = Sort.by(direction, field);
        return "id".equals(field) ? result : result.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private List<AdminOrderResponse> enrich(List<LockerOrder> orders, boolean withTimeline) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(LockerOrder::getId).toList();

        Map<Long, List<OrderDetailResponse>> details = detailRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId, Collectors.mapping(
                        d -> new OrderDetailResponse(d.getServiceId(), d.getQuantity(), d.getPrice(), d.getDescription()),
                        Collectors.toList())));

        List<Long> droneOrderIds = orders.stream()
                .filter(o -> "DRONE_DELIVERY".equalsIgnoreCase(o.getType()))
                .map(LockerOrder::getId)
                .toList();
        Map<Long, DroneMission> missions = droneOrderIds.isEmpty()
                ? Map.of()
                : missionRepository.findByOrderIdIn(droneOrderIds).stream()
                        .collect(Collectors.toMap(DroneMission::getOrderId, m -> m, (a, b) -> a));

        Map<Long, List<OrderStatusHistory>> histories = withTimeline
                ? orderIds.stream().collect(Collectors.toMap(id -> id, historyRepository::findByOrderIdOrderByCreatedAtAsc))
                : Map.of();

        Set<Long> userIds = new HashSet<>();
        Set<Long> lockerIds = new HashSet<>();
        Set<Long> boxIds = new HashSet<>();
        for (LockerOrder order : orders) {
            userIds.add(order.getUserId());
            userIds.add(receiverAccountId(order));
            lockerIds.add(order.getLockerId());
            lockerIds.add(order.getDestinationLockerId());
            boxIds.add(order.getSendBoxId());
            boxIds.add(order.getReceiveBoxId());
            boxIds.add(order.getReservedBoxId());
        }
        missions.values().forEach(m -> lockerIds.add(m.getSourceLockerId()));
        histories.values().forEach(list -> list.forEach(h -> userIds.add(h.getChangedByUserId())));

        Lookup<UserSummary> users = resolver.users(userIds);
        Lookup<LockerInfo> lockers = resolver.lockers(lockerIds);
        Lookup<BoxInfo> boxes = resolver.boxes(boxIds);
        Set<Long> storeIds = Stream.concat(
                        orders.stream().map(LockerOrder::getStoreId),
                        lockers.values().values().stream().map(LockerInfo::storeId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Lookup<StoreInfo> stores = resolver.stores(storeIds);
        Lookup<OrderPaymentSummary> payments = resolver.paymentSummaries(orderIds);

        LocalDateTime now = time.now();
        return orders.stream()
                .map(order -> toResponse(order, now, details.getOrDefault(order.getId(), List.of()),
                        missions.get(order.getId()), withTimeline ? histories.get(order.getId()) : null,
                        users, lockers, boxes, stores, payments))
                .toList();
    }

    private AdminOrderResponse toResponse(
            LockerOrder o,
            LocalDateTime now,
            List<OrderDetailResponse> details,
            DroneMission mission,
            List<OrderStatusHistory> history,
            Lookup<UserSummary> users,
            Lookup<LockerInfo> lockers,
            Lookup<BoxInfo> boxes,
            Lookup<StoreInfo> stores,
            Lookup<OrderPaymentSummary> payments) {
        LockerInfo locker = lockers.get(o.getLockerId());
        Long effectiveStoreId = o.getStoreId() != null ? o.getStoreId() : locker == null ? null : locker.storeId();
        UserSummary receiverAccount = users.get(receiverAccountId(o));
        return new AdminOrderResponse(
                o.getId(),
                o.getOrderCode(),
                o.getUserId(),
                o.getReceiverId(),
                o.getLockerId(),
                o.getSendBoxId(),
                o.getReceiveBoxId(),
                o.getStoreId(),
                o.getStaffId(),
                o.getType(),
                o.getServiceCategory(),
                o.getStatus(),
                o.getDeliveryStage(),
                o.getPinCode(),
                qrTokenService.issue(o.getId(), o.getPinCode()),
                o.getActualWeight(),
                o.getWeightUnit(),
                o.getExtraFee(),
                o.getDiscount(),
                o.getTotalPrice(),
                o.getOriginalPrice(),
                o.getPromotionCode(),
                o.getAppliedPromotionCodes(),
                OrderNextActions.nextAction(o),
                OrderNextActions.nextActionMessage(o),
                OrderNextActions.paymentRequired(o),
                o.getPaymentStatus(),
                o.getPickupDeadline() != null && now.isAfter(o.getPickupDeadline()),
                o.getPickupDeadline(),
                o.getReturnedAt(),
                o.getCompletedAt(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                details,
                o.getReceiverUserId(),
                o.getReceiverPhone(),
                o.getReceiverName(),
                o.getDestinationLockerId(),
                o.getReservedBoxId(),
                o.getFulfillmentMode(),
                o.getPaidAt(),
                o.getParcelWeightGrams(),
                o.getReservationFee(),
                o.getStoragePrice(),
                o.getShippingFee(),
                o.getPinCodeIssuedAt(),
                o.getReceiveAt(),
                o.getIntendedReceiveAt(),
                o.getRentalDurationHours(),
                o.getLastReminderAt(),
                o.getDescription(),
                o.getCustomerNote(),
                o.getStaffNote(),
                o.getCancelReason(),
                o.getDeliveryAddress(),
                person(users.get(o.getUserId())),
                receiver(o, receiverAccount),
                lockerRef(locker),
                lockerRef(lockers.get(o.getDestinationLockerId())),
                storeRef(stores.get(effectiveStoreId)),
                boxNumber(boxes, o.getSendBoxId()),
                boxNumber(boxes, o.getReceiveBoxId()),
                boxNumber(boxes, o.getReservedBoxId()),
                fees(o),
                payment(o, payments),
                drone(o, mission, lockers),
                history == null ? null : timeline(history, users));
    }

    static Fees fees(LockerOrder o) {
        BigDecimal extra = nz(o.getExtraFee());
        BigDecimal total = nz(o.getTotalPrice());
        return new Fees(
                nz(o.getOriginalPrice()),
                nz(o.getReservationFee()),
                nz(o.getStoragePrice()),
                nz(o.getShippingFee()),
                extra,
                extra,
                nz(o.getDiscount()),
                total.subtract(extra).max(BigDecimal.ZERO),
                total);
    }

    private PaymentInfo payment(LockerOrder o, Lookup<OrderPaymentSummary> payments) {
        if (!payments.available()) {
            return null;
        }
        OrderPaymentSummary s = payments.get(o.getId());
        BigDecimal paid = s == null ? BigDecimal.ZERO : nz(s.paidAmount());
        BigDecimal outstanding = nz(o.getTotalPrice()).subtract(paid).max(BigDecimal.ZERO);
        if (s == null) {
            return new PaymentInfo(0, null, null, null, null, null, BigDecimal.ZERO, null, null, BigDecimal.ZERO, outstanding);
        }
        return new PaymentInfo(
                s.paymentCount(), s.latestPaymentId(), s.latestMethod(), s.latestStatus(), s.latestAmount(),
                s.latestCreatedAt(), paid, s.lastPaidMethod(), s.lastPaidAt(), nz(s.refundedAmount()), outstanding);
    }

    private DroneInfo drone(LockerOrder o, DroneMission m, Lookup<LockerInfo> lockers) {
        if (!"DRONE_DELIVERY".equalsIgnoreCase(o.getType())) {
            return null;
        }
        if (m == null) {
            return new DroneInfo(null, null, o.getDeliveryStage(), null, null, null, null,
                    o.getDestinationLockerId(), null, null, null, null, null);
        }
        return new DroneInfo(
                m.getId(), m.getStatus(), o.getDeliveryStage(), m.getDroneUnitId(), m.getDroneCode(),
                m.getSourceLockerId(), lockerRef(lockers.get(m.getSourceLockerId())), m.getDestinationLockerId(),
                m.getAssignedByUserId(), m.getReadyToLaunchAt(), m.getLaunchingAt(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private List<TimelineEvent> timeline(List<OrderStatusHistory> history, Lookup<UserSummary> users) {
        return history.stream()
                .map(h -> {
                    UserSummary actor = users.get(h.getChangedByUserId());
                    return new TimelineEvent(h.getOldStatus(), h.getNewStatus(), h.getChangedByUserId(),
                            actor == null ? null : actor.fullName(), h.getNote(), h.getCreatedAt());
                })
                .toList();
    }

    private static Long receiverAccountId(LockerOrder o) {
        return o.getReceiverUserId() != null ? o.getReceiverUserId() : o.getReceiverId();
    }

    private static Person person(UserSummary u) {
        return u == null ? null : new Person(u.id(), u.fullName(), u.phoneNumber(), u.email(), u.status());
    }

    private static Receiver receiver(LockerOrder o, UserSummary account) {
        Long accountId = receiverAccountId(o);
        if (accountId == null
                && o.getReceiverName() == null
                && o.getReceiverPhone() == null
                && o.getReceiverEmail() == null) {
            return null;
        }
        return new Receiver(
                accountId,
                o.getReceiverName(),
                o.getReceiverPhone(),
                o.getReceiverEmail(),
                account == null ? null : account.fullName(),
                account == null ? null : account.phoneNumber(),
                account == null ? null : account.email());
    }

    static LockerRef lockerRef(LockerInfo l) {
        return l == null ? null : new LockerRef(l.id(), l.code(), l.name(), l.address(), l.storeId(), l.status());
    }

    static StoreRef storeRef(StoreInfo s) {
        return s == null ? null : new StoreRef(s.id(), s.name(), s.address(), s.contactPhone());
    }

    private static Integer boxNumber(Lookup<BoxInfo> boxes, Long boxId) {
        BoxInfo box = boxes.get(boxId);
        return box == null ? null : box.boxNumber();
    }

    private static boolean looksLikePhone(String q) {
        return q != null && q.trim().matches("\\+?\\d{8,15}");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
