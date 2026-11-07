package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.order.dto.OrderResponse;
import com.huynqb.laundrylocker.order.dto.StorageHandoverRequest;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.StorageHandover;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.StorageHandoverRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Admin trao đồ lưu kho (EXPIRED) cho người tới quầy rồi chốt đơn COMPLETED. Khác `PUT /status`:
/// bắt buộc đối chiếu SĐT người nhận, ảnh xác minh và — khi khách còn nợ — khai đã thu đủ.
/// Biên bản lưu ở `storage_handovers`, tóm tắt ghi vào ghi chú lịch sử trạng thái.
@Service
@RequiredArgsConstructor
public class StorageHandoverService {

    static final Set<String> RECIPIENT_TYPES = Set.of("SENDER", "RECEIVER", "DELEGATE", "OTHER");
    static final Set<String> COLLECTION_METHODS = Set.of("CASH", "TRANSFER");

    private final LockerOrderRepository orderRepository;
    private final StorageHandoverRepository handoverRepository;
    private final AdminReferenceResolver resolver;
    private final CloudinaryMediaStorage mediaStorage;
    private final OrderService orderService;

    @Transactional
    public OrderResponse handOver(Long orderId, StorageHandoverRequest request, Long adminId) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!AdminOrderQueryService.STORED_STATUS.equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException("HANDOVER_ORDER_NOT_IN_STORAGE", "Đơn không còn nằm trong kho");
        }
        String type = request.recipientType() == null ? "" : request.recipientType().trim().toUpperCase(Locale.ROOT);
        if (!RECIPIENT_TYPES.contains(type)) {
            throw new BusinessException("HANDOVER_RECIPIENT_TYPE_INVALID", "recipientType không hợp lệ: " + request.recipientType());
        }

        Recipient recipient = "OTHER".equals(type) ? otherRecipient(request) : knownRecipient(order, type, request.phoneLast4());
        Collected collection = collection(order, request);
        VerifiedMedia photo = mediaStorage.verify(request.photo(), MediaPurpose.STORAGE_HANDOVER, adminId);
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        OrderResponse closed = orderService.checkout(order.getId(), adminId, historyNote(type, recipient, collection, note));

        StorageHandover handover = new StorageHandover();
        handover.setOrderId(order.getId());
        handover.setRecipientType(type);
        handover.setRecipientName(recipient.name());
        handover.setRecipientPhone(recipient.phone());
        handover.setPhotoPublicId(photo.publicId());
        handover.setPhotoUrl(photo.secureUrl());
        handover.setCollectedAmount(collection == null ? null : collection.amount());
        handover.setCollectionMethod(collection == null ? null : collection.method());
        handover.setNote(note);
        handover.setHandedOverByUserId(adminId);
        handover.setHandedOverAt(LocalDateTime.now());
        handoverRepository.save(handover);
        return closed;
    }

    private record Recipient(String name, String phone) {
    }

    private record Collected(BigDecimal amount, String method) {
    }

    private static Recipient otherRecipient(StorageHandoverRequest request) {
        String phone = digits(request.recipientPhone());
        if (!StringUtils.hasText(request.recipientName()) || phone.length() < 9 || !StringUtils.hasText(request.note())) {
            throw new BusinessException(
                    "HANDOVER_OTHER_RECIPIENT_INCOMPLETE",
                    "Người nhận không có trên đơn: cần họ tên, số điện thoại và lý do trao");
        }
        return new Recipient(request.recipientName().trim(), phone);
    }

    /// Người có trên đơn: 4 số cuối người tới nhận đọc phải khớp một SĐT của bên đó.
    private Recipient knownRecipient(LockerOrder order, String type, String phoneLast4) {
        String last4 = digits(phoneLast4);
        if (last4.length() != 4) {
            throw new BusinessException("HANDOVER_PHONE_LAST4_REQUIRED", "Nhập đủ 4 số cuối điện thoại của người tới nhận");
        }
        List<String> phones = new ArrayList<>();
        String name;
        switch (type) {
            case "SENDER" -> {
                UserSummary account = account(order.getUserId());
                if (account != null) {
                    phones.add(account.phoneNumber());
                }
                phones.add(order.getSenderPhone());
                name = firstText(account == null ? null : account.fullName(), order.getSenderName(), "Khách gửi");
            }
            case "RECEIVER" -> {
                Long accountId = order.getReceiverUserId() != null ? order.getReceiverUserId() : order.getReceiverId();
                UserSummary account = account(accountId);
                phones.add(order.getReceiverPhone());
                if (account != null) {
                    phones.add(account.phoneNumber());
                }
                name = firstText(order.getReceiverName(), account == null ? null : account.fullName(), "Người nhận");
            }
            default -> {
                phones.add(order.getDelegatePhone());
                name = firstText(order.getDelegateName(), "Người được ủy quyền");
            }
        }
        List<String> known = phones.stream().map(StorageHandoverService::digits).filter(p -> p.length() >= 4).distinct().toList();
        if (known.isEmpty()) {
            throw new BusinessException(
                    "HANDOVER_PHONE_UNKNOWN",
                    "Đơn không có số điện thoại của bên này — chọn \"Người khác\" và ghi rõ lý do");
        }
        String matched = known.stream().filter(p -> p.endsWith(last4)).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "HANDOVER_PHONE_MISMATCH", "4 số cuối không khớp số điện thoại trên đơn"));
        return new Recipient(name, matched);
    }

    private UserSummary account(Long userId) {
        return userId == null ? null : resolver.users(List.of(userId)).get(userId);
    }

    /// Còn nợ thì admin phải khai đã thu đủ và thu bằng cách nào; không nợ thì bỏ qua số khai.
    private Collected collection(LockerOrder order, StorageHandoverRequest request) {
        BigDecimal owed = outstanding(order);
        if (owed.signum() <= 0) {
            return null;
        }
        String method = request.collectionMethod() == null ? "" : request.collectionMethod().trim().toUpperCase(Locale.ROOT);
        if (request.collectedAmount() == null || request.collectedAmount().compareTo(owed) < 0 || !COLLECTION_METHODS.contains(method)) {
            throw new BusinessException(
                    "HANDOVER_OUTSTANDING_NOT_COLLECTED",
                    "Khách còn nợ " + vnd(owed) + " — xác nhận đã thu đủ và chọn cách thu trước khi trao đồ");
        }
        return new Collected(request.collectedAmount(), method);
    }

    /// Cùng công thức với `payment.outstandingAmount` trên API admin; payment-service lỗi thì dựa vào paymentStatus.
    BigDecimal outstanding(LockerOrder order) {
        BigDecimal total = order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice();
        Lookup<OrderPaymentSummary> payments = resolver.paymentSummaries(List.of(order.getId()));
        if (!payments.available()) {
            return "UNPAID".equalsIgnoreCase(order.getPaymentStatus()) ? total : BigDecimal.ZERO;
        }
        OrderPaymentSummary summary = payments.get(order.getId());
        BigDecimal paid = summary == null || summary.paidAmount() == null ? BigDecimal.ZERO : summary.paidAmount();
        return total.subtract(paid).max(BigDecimal.ZERO);
    }

    private static String historyNote(String type, Recipient recipient, Collected collection, String note) {
        String role = switch (type) {
            case "SENDER" -> "khách gửi";
            case "RECEIVER" -> "người nhận";
            case "DELEGATE" -> "người được ủy quyền";
            default -> "người khác";
        };
        StringBuilder sb = new StringBuilder("Trao trả đồ lưu kho tại quầy cho ")
                .append(role).append(' ').append(recipient.name())
                .append(" (SĐT ***").append(recipient.phone().substring(recipient.phone().length() - 4)).append(')');
        if (collection != null) {
            sb.append("; đã thu ").append(vnd(collection.amount()))
                    .append("CASH".equals(collection.method()) ? " tiền mặt" : " chuyển khoản");
        }
        if (note != null) {
            sb.append("; ghi chú: ").append(note);
        }
        return sb.length() > 1000 ? sb.substring(0, 1000) : sb.toString();
    }

    private static String vnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
