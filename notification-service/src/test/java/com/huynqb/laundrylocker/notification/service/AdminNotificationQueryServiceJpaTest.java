package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationBulkDeleteRequest;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationStats;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import com.huynqb.laundrylocker.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Chạy thật truy vấn trang Thông báo admin trên H2: lọc/phân trang ở DB, thẻ số liệu, xoá mềm hàng loạt,
/// và thông báo ví cũ (ví đã gỡ khỏi sản phẩm) không bao giờ hiện.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class AdminNotificationQueryServiceJpaTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 20, 8, 0);

    @Autowired private TestEntityManager em;
    @Autowired private NotificationRepository repository;

    private AdminNotificationQueryService service;
    private NotificationMessage overdueUnread;
    private NotificationMessage overdueRead;
    private NotificationMessage created;

    @BeforeEach
    void seed() {
        service = new AdminNotificationQueryService(repository, em.getEntityManager());
        overdueUnread = persist(7L, "ORDER_PICKUP_OVERDUE", "Đơn ORD-1 quá hạn lấy hàng", "Vui lòng tới lấy đồ", null, 1);
        overdueRead = persist(8L, "ORDER_PICKUP_OVERDUE", "Đơn ORD-2 quá hạn lấy hàng", "Vui lòng tới lấy đồ", 30, 2);
        created = persist(7L, "ORDER_CREATED", "Đã tạo đơn ORD-3", "Ô số 4 tại tủ CAB-01", 90, 3);
        // Thông báo ví cũ — giữ trong DB nhưng không được hiện ở trang admin.
        persist(7L, "WALLET_TOPUP", "Nạp ví thành công", "Đã cộng 250.000đ vào ví LockR.", 5, 4);
        persist(7L, "PAYMENT", "Thanh toán thành công", "Đã trừ 50.000đ từ ví cho đơn ORD-STG-0001.", null, 5);
        persist(9L, "OPEN_PROMOTION_TAB", "Ưu đãi mới: DRONE5K", "Nhập mã DRONE5K. Lưu ngay vào ví voucher!", null, 6);
        NotificationMessage deleted = persist(7L, "ORDER_CREATED", "Đơn đã xoá", "x", null, 7);
        deleted.setDeletedAt(T0);
        deleted.setDeletedReason("trùng");
        em.flush();
        em.clear();
    }

    @Test
    void searchPagesNewestFirstAndHidesLegacyWalletRows() {
        PageResponse<NotificationResponse> page = service.search(0, 2, null, null, null, null);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(List.of(created.getId(), overdueRead.getId()), ids(page));
        assertEquals(List.of(overdueUnread.getId()), ids(service.search(1, 2, null, null, null, null)));
    }

    @Test
    void searchFiltersByStatusTypeTextIdAndRecipients() {
        assertEquals(List.of(created.getId(), overdueRead.getId()), ids(service.search(0, 10, "READ", null, null, null)));
        assertEquals(List.of(overdueUnread.getId()), ids(service.search(0, 10, "UNREAD", null, null, null)));
        assertEquals(List.of(overdueRead.getId(), overdueUnread.getId()),
                ids(service.search(0, 10, null, "ORDER_PICKUP_OVERDUE", null, null)));
        assertEquals(List.of(created.getId()), ids(service.search(0, 10, null, null, "cab-01", null)));
        assertEquals(List.of(created.getId()), ids(service.search(0, 10, null, null, "#" + created.getId(), null)));
        // "An" khớp tên người nhận #8 (trang admin tra mã người nhận từ danh sách người dùng).
        assertEquals(List.of(overdueRead.getId()), ids(service.search(0, 10, null, null, "An", List.of(8L))));
        // Tìm chữ của thông báo ví cũ không làm nó hiện lại.
        assertEquals(List.of(), ids(service.search(0, 10, null, null, "ví", null)));
        assertEquals(List.of(), ids(service.search(0, 10, null, "WALLET_TOPUP", null, null)));
    }

    @Test
    void statsCountOnlyVisibleRows() {
        AdminNotificationStats stats = service.stats();

        assertEquals(3, stats.total());
        assertEquals(2, stats.read());
        assertEquals(1, stats.unread());
        assertEquals(60L, stats.averageReadMinutes());
        assertEquals(List.of(
                new AdminNotificationStats.TypeCount("ORDER_PICKUP_OVERDUE", 2),
                new AdminNotificationStats.TypeCount("ORDER_CREATED", 1)), stats.types());
    }

    @Test
    void bulkDeleteBySelectionOrFilterIsSoftAndKeepsReason() {
        List<Long> deleted = service.bulkDelete(new AdminNotificationBulkDeleteRequest(
                List.of(overdueUnread.getId(), 999_999L), null, null, null, null, null, " gửi nhầm "), 1L);
        assertEquals(List.of(overdueUnread.getId()), deleted);

        List<Long> byFilter = service.bulkDelete(new AdminNotificationBulkDeleteRequest(
                null, true, "READ", "ORDER_PICKUP_OVERDUE", null, null, "dọn dẹp"), 1L);
        assertEquals(List.of(overdueRead.getId()), byFilter);

        em.clear();
        assertEquals(List.of(created.getId()), ids(service.search(0, 10, null, null, null, null)));
        Object[] row = (Object[]) em.getEntityManager()
                .createNativeQuery("select deleted_by, deleted_reason, deleted_at from notification_schema.notifications where id = :id")
                .setParameter("id", overdueUnread.getId()).getSingleResult();
        assertEquals(1L, ((Number) row[0]).longValue());
        assertEquals("gửi nhầm", row[1]);
        assertNotNull(row[2]);
    }

    @Test
    void bulkDeleteNeedsReasonAndSelection() {
        assertEquals("DELETE_REASON_REQUIRED", assertThrows(BusinessException.class, () -> service.bulkDelete(
                new AdminNotificationBulkDeleteRequest(List.of(created.getId()), null, null, null, null, null, " "), 1L))
                .getCode());
        assertEquals("NOTIFICATION_IDS_REQUIRED", assertThrows(BusinessException.class, () -> service.bulkDelete(
                new AdminNotificationBulkDeleteRequest(List.of(), false, null, null, null, null, "x"), 1L)).getCode());
    }

    @Test
    void statsWithoutReadRowsHaveNoAverage() {
        service.bulkDelete(new AdminNotificationBulkDeleteRequest(null, true, "READ", null, null, null, "x"), 1L);
        em.clear();

        AdminNotificationStats stats = service.stats();
        assertEquals(1, stats.total());
        assertNull(stats.averageReadMinutes());
    }

    private NotificationMessage persist(Long userId, String type, String title, String message,
                                        Integer readAfterMinutes, int hour) {
        NotificationMessage n = new NotificationMessage();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        if (readAfterMinutes != null) {
            n.setIsRead(true);
            n.setStatus("READ");
            n.setReadAt(T0.plusHours(hour).plusMinutes(readAfterMinutes));
        }
        em.persist(n);
        em.flush();
        // created_at do @PrePersist gán = now và không cập nhật qua entity ⇒ đặt giờ cố định bằng SQL để thứ
        // tự và thời gian đọc xác định.
        em.getEntityManager().createNativeQuery("update notification_schema.notifications set created_at = :at where id = :id")
                .setParameter("at", T0.plusHours(hour))
                .setParameter("id", n.getId())
                .executeUpdate();
        return n;
    }

    private static List<Long> ids(PageResponse<NotificationResponse> page) {
        return page.content().stream().map(NotificationResponse::id).toList();
    }
}
