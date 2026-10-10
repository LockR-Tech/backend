package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackAnalyticsResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackDetailResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.SatisfactionMetricsResponse;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderComplaint;
import com.huynqb.laundrylocker.order.model.OrderRating;
import com.huynqb.laundrylocker.order.service.AdminFeedbackService;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver;
import com.huynqb.laundrylocker.order.service.DashboardInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/// Đánh giá dịch vụ cho admin chạy trên H2 thật: lọc + phân trang, xử lý/trả lời, số liệu tổng hợp
/// (phân bố sao, mốc hôm nay/tuần/tháng, điểm theo loại đơn, khiếu nại nhiều nhất), giờ cao điểm.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admin-feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE SCHEMA IF NOT EXISTS order_schema",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class AdminFeedbackJpaTest {

    // 2026-10-10 05:00 UTC = 12:00 giờ Việt Nam, thứ Bảy.
    private static final Instant NOW = Instant.parse("2026-10-10T05:00:00Z");
    private static final BusinessTime TIME = new BusinessTime(
            ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE, Clock.fixed(NOW, ZoneOffset.UTC));

    @Autowired private TestEntityManager em;
    @Autowired private OrderRatingRepository ratings;
    @Autowired private LockerOrderRepository orders;

    private final AdminReferenceResolver references = mock(AdminReferenceResolver.class);
    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private AdminFeedbackService service;
    private LockerOrder send;
    private LockerOrder rental;

    @BeforeEach
    void seed() {
        service = new AdminFeedbackService(ratings, orders, references, notificationClient);
        ReflectionTestUtils.invokeMethod(service, "setTime", TIME);
        when(references.users(anyCollection())).thenReturn(new AdminReferenceResolver.Lookup<>(Map.of(
                44L, new UserSummary(44L, "an@lockr.vn", "0901111111", "Nguyễn An", "ACTIVE"),
                99L, new UserSummary(99L, "admin@lockr.vn", null, "Quản trị", "ACTIVE")), true));

        send = order("ORD-1", "SEND", LocalDateTime.of(2026, 10, 1, 3, 0));
        rental = order("ORD-2", "RENTAL", LocalDateTime.of(2026, 10, 2, 3, 0));
        LockerOrder drone = order("ORD-3", "DRONE_DELIVERY", LocalDateTime.of(2026, 10, 10, 1, 30));
        rating(send, 44L, 5, "Rất tốt", LocalDateTime.of(2026, 10, 10, 2, 0)); // hôm nay 09:00 VN
        rating(rental, 44L, 2, "Tủ bẩn", LocalDateTime.of(2026, 10, 6, 2, 0)); // tuần này (thứ Hai 05/10)
        rating(drone, 45L, 4, null, LocalDateTime.of(2026, 9, 20, 2, 0)); // tháng trước
        complaint(rental, "DIRTY_BOX", LocalDateTime.of(2026, 10, 6, 2, 0));
        complaint(rental, "DIRTY_BOX", LocalDateTime.of(2026, 10, 7, 2, 0));
        complaint(send, "LATE", LocalDateTime.of(2026, 10, 8, 2, 0));
        em.flush();
        em.clear();
    }

    @Test
    void listFiltersByStarsAndStatusNewestFirstWithCustomerAndOrder() {
        PageResponse<FeedbackResponse> all = service.search(0, 2, null, null, null);
        assertEquals(3, all.totalElements());
        assertEquals(2, all.totalPages());
        assertEquals(List.of(5, 2), all.content().stream().map(FeedbackResponse::rating).toList());
        FeedbackResponse first = all.content().get(0);
        assertEquals("Nguyễn An", first.userName());
        assertEquals("an@lockr.vn", first.email());
        assertEquals("SEND · ORD-1", first.orderDescription());
        assertFalse(first.resolved());

        assertEquals(List.of(2), service.search(0, 20, null, 3, null).content().stream()
                .map(FeedbackResponse::rating).toList());
        assertEquals(2, service.search(0, 20, 4, null, false).totalElements());
    }

    @Test
    void resolvingAndReopeningTrackWhoAndWhen() {
        Long id = idOfRating(5);

        FeedbackResponse resolved = service.updateStatus(id, "resolved", 99L);
        assertTrue(resolved.resolved());
        FeedbackDetailResponse detail = service.detail(id);
        assertEquals("Quản trị", detail.resolvedBy());
        assertEquals(LocalDateTime.of(2026, 10, 10, 5, 0), detail.resolvedAt());
        assertEquals(1, service.search(0, 20, null, null, true).totalElements());

        assertFalse(service.updateStatus(id, "PENDING", 99L).resolved());
        assertNull(service.detail(id).resolvedAt());
        assertEquals("FEEDBACK_STATUS_INVALID",
                assertThrows(BusinessException.class, () -> service.updateStatus(id, "DONE", 99L)).getCode());
    }

    @Test
    void replyIsStoredAndTheCustomerIsNotifiedEvenIfNotifyingFails() {
        Long id = idOfRating(2);
        when(notificationClient.requestNotification(any())).thenThrow(new RuntimeException("down"));

        FeedbackDetailResponse detail = service.reply(id, "  Xin lỗi, đã vệ sinh ô  ", 99L);

        assertEquals("Xin lỗi, đã vệ sinh ô", detail.adminReply());
        assertEquals(LocalDateTime.of(2026, 10, 10, 5, 0), detail.repliedAt());
        assertEquals("RENTAL", detail.serviceType());
        assertEquals(0, new BigDecimal("15000").compareTo(detail.orderAmount()));
        verify(notificationClient).requestNotification(argThat((NotificationRequest n) ->
                n.userId().equals(44L) && "FEEDBACK_REPLY".equals(n.type()) && rental.getId().equals(n.referenceId())));
        assertEquals("FEEDBACK_REPLY_EMPTY",
                assertThrows(BusinessException.class, () -> service.reply(id, "  ", 99L)).getCode());
    }

    @Test
    void analyticsCountsStarsAndBusinessDayWeekMonth() {
        FeedbackAnalyticsResponse analytics = service.analytics("day");

        assertEquals(3, analytics.totalFeedback());
        assertEquals(3.67, analytics.averageRating());
        assertEquals(Map.of("1", 0L, "2", 1L, "3", 0L, "4", 1L, "5", 1L), analytics.ratingDistribution());
        assertEquals(1, analytics.feedbackToday());
        assertEquals(2, analytics.feedbackThisWeek());
        assertEquals(2, analytics.feedbackThisMonth());
        assertEquals(3, analytics.unresolvedCount());
        assertEquals(14, analytics.trends().size());
        assertEquals(1, analytics.trends().get(13).count());
        assertEquals(5.0, analytics.trends().get(13).avgRating());
        assertEquals(12, service.analytics(null).trends().size());
    }

    @Test
    void satisfactionScoresByServiceAndTopComplaint() {
        SatisfactionMetricsResponse metrics = service.satisfaction();

        assertEquals(73.3, metrics.overallSatisfactionScore());
        assertEquals(0.0, metrics.npsScore()); // 1/3 năm sao − 1/3 từ 1–3 sao
        assertEquals(66.7, metrics.positivePercentage());
        assertEquals(33.3, metrics.negativePercentage());
        assertEquals("DIRTY_BOX", metrics.mostCommonComplaint());
        assertEquals("SEND", metrics.topServiceQuality());
        assertEquals(Map.of("SEND", 5.0, "RENTAL", 2.0, "DRONE_DELIVERY", 4.0), metrics.departmentScores());
    }

    @Test
    void peakHoursBucketOrdersByVietnamHour() {
        DashboardInsightService insights = new DashboardInsightService(orders);
        ReflectionTestUtils.invokeMethod(insights, "setTime", TIME);

        List<DashboardInsightService.HourBucket> hours = insights.peakHours("2026-10-01", "2026-10-10");

        assertEquals(24, hours.size());
        assertEquals(2, hours.get(10).orders()); // 03:00 UTC = 10:00 VN (ORD-1, ORD-2)
        assertEquals(1, hours.get(8).orders()); // 01:30 UTC = 08:30 VN (ORD-3)
        assertEquals(3, hours.stream().mapToLong(DashboardInsightService.HourBucket::orders).sum());
    }

    private Long idOfRating(int stars) {
        return ratings.findAll().stream().filter(r -> r.getRating() == stars).findFirst().orElseThrow().getId();
    }

    private LockerOrder order(String code, String type, LocalDateTime createdAt) {
        LockerOrder order = new LockerOrder();
        order.setOrderCode(code);
        order.setType(type);
        order.setStatus("COMPLETED");
        order.setPaymentStatus("PAID");
        order.setUserId(44L);
        order.setTotalPrice(BigDecimal.valueOf(15000));
        em.persist(order);
        em.flush();
        setCreatedAt("LockerOrder", order.getId(), createdAt);
        return order;
    }

    private void rating(LockerOrder order, Long userId, int stars, String comment, LocalDateTime createdAt) {
        OrderRating rating = new OrderRating();
        rating.setOrderId(order.getId());
        rating.setUserId(userId);
        rating.setRating(stars);
        rating.setComment(comment);
        em.persist(rating);
        em.flush();
        setCreatedAt("OrderRating", rating.getId(), createdAt);
    }

    private void complaint(LockerOrder order, String type, LocalDateTime createdAt) {
        OrderComplaint complaint = new OrderComplaint();
        complaint.setOrderId(order.getId());
        complaint.setUserId(44L);
        complaint.setType(type);
        complaint.setDescription("x");
        em.persist(complaint);
        em.flush();
        setCreatedAt("OrderComplaint", complaint.getId(), createdAt);
    }

    private void setCreatedAt(String entity, Long id, LocalDateTime at) {
        em.getEntityManager()
                .createQuery("update " + entity + " e set e.createdAt = :at where e.id = :id")
                .setParameter("at", at)
                .setParameter("id", id)
                .executeUpdate();
    }
}
