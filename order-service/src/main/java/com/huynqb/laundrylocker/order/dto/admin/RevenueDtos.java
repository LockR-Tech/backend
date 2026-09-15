package com.huynqb.laundrylocker.order.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response của /api/admin/revenue/**.
 *
 * <p>Doanh thu = tiền thực thu: payment COMPLETED của đơn thật (orderId &gt; 0), không gồm nạp
 * ví VNPAY_TOPUP; thanh toán bằng ví (WALLET) được tính khi payment của đơn hoàn tất. Ngày
 * doanh thu = ngày (giờ Việt Nam) của thời điểm thu (updatedAt của payment COMPLETED). Hoàn
 * tiền = refund COMPLETED của đơn, theo processedAt. netRevenue = totalRevenue − refundAmount.
 */
public final class RevenueDtos {

    private RevenueDtos() {
    }

    public record RevenuePeriod(
            LocalDate from,
            LocalDate to,
            BigDecimal totalRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            /// Đơn tạo trong kỳ (mọi trạng thái).
            long orderCount,
            /// Đơn có ít nhất một khoản thu trong kỳ.
            long paidOrderCount,
            long paymentCount,
            /// Đơn chuyển COMPLETED trong kỳ (theo completedAt).
            long completedOrderCount,
            /// Đơn tạo trong kỳ hiện đang CANCELED.
            long canceledOrderCount,
            /// totalRevenue / paidOrderCount (làm tròn đồng); null khi không có đơn thu tiền.
            BigDecimal averageOrderValue) {
    }

    /// % thay đổi so với kỳ trước (2 chữ số); null khi kỳ trước bằng 0.
    public record RevenueChanges(
            BigDecimal totalRevenuePct,
            BigDecimal refundAmountPct,
            BigDecimal netRevenuePct,
            BigDecimal orderCountPct,
            BigDecimal paidOrderCountPct,
            BigDecimal averageOrderValuePct) {
    }

    public record RevenueSummaryResponse(
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo,
            RevenuePeriod current,
            RevenuePeriod previous,
            RevenueChanges changes,
            RevenuePeriod today,
            /// Từ thứ Hai tuần này tới hôm nay.
            RevenuePeriod thisWeek,
            /// Từ ngày 1 tháng này tới hôm nay.
            RevenuePeriod thisMonth) {
    }

    public record DailyRevenue(
            LocalDate date,
            BigDecimal revenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            long paidOrderCount,
            long paymentCount,
            long orderCount) {
    }

    public record DailyRevenueResponse(LocalDate from, LocalDate to, BigDecimal totalRevenue, List<DailyRevenue> days) {
    }

    /// serviceType: SEND | RENTAL | DRONE_DELIVERY | STORAGE | OTHER | UNKNOWN | OVERTIME_FEE.
    /// Dòng OVERTIME_FEE là phần tiền thu bù phí quá hạn (extraFee), đã trừ khỏi dòng loại đơn.
    public record ServiceRevenue(
            String serviceType,
            BigDecimal revenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal sharePct,
            long orderCount,
            long paidOrderCount,
            long paymentCount) {
    }

    public record ServiceRevenueResponse(
            LocalDate from, LocalDate to, BigDecimal totalRevenue, List<ServiceRevenue> items) {
    }

    public record MethodRevenue(
            String method,
            BigDecimal revenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal sharePct,
            long paymentCount,
            long paidOrderCount) {
    }

    public record MethodRevenueResponse(
            LocalDate from, LocalDate to, BigDecimal totalRevenue, List<MethodRevenue> items) {
    }

    /// lockerId null = đơn không gắn tủ. revenuePerBox = revenue / boxCount (null khi boxCount = 0).
    public record LockerRevenue(
            Long lockerId,
            String code,
            String name,
            String address,
            String status,
            Long storeId,
            String storeName,
            long boxCount,
            long orderCount,
            long paidOrderCount,
            BigDecimal revenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal revenuePerBox,
            BigDecimal sharePct) {
    }

    /// lookupAvailable=false: locker/store-service lỗi, tên/địa chỉ để null.
    public record LockerRevenueResponse(
            LocalDate from, LocalDate to, BigDecimal totalRevenue, boolean lookupAvailable, List<LockerRevenue> items) {
    }

    /// storeId null = tủ/đơn chưa gán cửa hàng.
    public record StoreRevenue(
            Long storeId,
            String name,
            String address,
            String contactPhone,
            long lockerCount,
            long boxCount,
            long orderCount,
            long paidOrderCount,
            BigDecimal revenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal sharePct) {
    }

    public record StoreRevenueResponse(
            LocalDate from, LocalDate to, BigDecimal totalRevenue, boolean lookupAvailable, List<StoreRevenue> items) {
    }

    public record CustomerRevenue(
            Long userId,
            String fullName,
            String phoneNumber,
            String email,
            long orderCount,
            long paidOrderCount,
            BigDecimal totalSpent,
            BigDecimal refundAmount,
            BigDecimal netSpent,
            BigDecimal averageOrderValue,
            LocalDateTime lastOrderAt,
            LocalDateTime lastPaidAt) {
    }

    public record CustomerDetailOrder(
            Long orderId,
            String orderCode,
            String type,
            String status,
            String paymentStatus,
            Long lockerId,
            String lockerCode,
            String lockerName,
            BigDecimal totalPrice,
            BigDecimal extraFee,
            BigDecimal discount,
            /// Thu trong kỳ.
            BigDecimal paidInRange,
            BigDecimal refundedInRange,
            /// Từ payment-service, mọi thời điểm; null khi không tra được.
            BigDecimal paidAllTime,
            List<String> paymentMethods,
            String lastPaymentMethod,
            LocalDateTime lastPaidAt,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            LocalDateTime completedAt) {
    }

    public record CustomerRevenueDetailResponse(
            LocalDate from,
            LocalDate to,
            Long userId,
            String fullName,
            String phoneNumber,
            String email,
            String status,
            long orderCount,
            long paidOrderCount,
            BigDecimal totalSpent,
            BigDecimal refundAmount,
            BigDecimal netSpent,
            BigDecimal averageOrderValue,
            LocalDateTime firstOrderAt,
            LocalDateTime lastOrderAt,
            List<CustomerDetailOrder> orders) {
    }
}
