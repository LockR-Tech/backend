package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByOrderIdAndType(Long orderId, String type);

    /// ORDER_REDEEM / ORDER_REDEEM_REFUND có unique index theo order_id nên mỗi đơn có tối đa một dòng.
    Optional<PointTransaction> findFirstByOrderIdAndType(Long orderId, String type);

    /// VOUCHER_EXCHANGE / VOUCHER_EXCHANGE_REFUND có unique index theo reference nên mỗi lần đổi có tối đa một dòng.
    Optional<PointTransaction> findFirstByReferenceAndType(String reference, String type);

    boolean existsByReferenceAndType(String reference, String type);

    /// Tổng điểm các giao dịch một loại của một đơn (vd các lần thu hồi điểm đã tích, mang dấu âm).
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.orderId = :orderId AND t.type = :type")
    long sumPointsByOrderIdAndType(@Param("orderId") Long orderId, @Param("type") String type);

    /// Tổng hợp giao dịch điểm cho thống kê admin (một lượt quét bảng). Điểm phát ra là
    /// tổng giao dịch dương trừ hoàn điểm đã dùng (ORDER_REDEEM_REFUND, VOUCHER_EXCHANGE_REFUND) và trừ điểm thu hồi khi đơn được
    /// hoàn tiền (ORDER_EARN_REVERSAL, âm); điểm đã dùng là tổng trị tuyệt đối giao dịch âm khác
    /// trừ đi phần đã hoàn lại. Các mốc thời gian đã quy về múi giờ lưu trữ.
    @Query(value = """
            SELECT COUNT(*) AS "transactions",
                   COALESCE(SUM(points) FILTER (WHERE (points > 0 AND type NOT IN ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND'))
                                                OR type = 'ORDER_EARN_REVERSAL'), 0)
                       AS "pointsIssued",
                   COALESCE(-SUM(points) FILTER (WHERE (points < 0 AND type <> 'ORDER_EARN_REVERSAL')
                                                 OR type IN ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND')), 0)
                       AS "pointsRedeemed",
                   COALESCE(SUM(points) FILTER (WHERE ((points > 0 AND type NOT IN ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND'))
                                                       OR type = 'ORDER_EARN_REVERSAL')
                                                AND created_at >= :monthStart), 0)
                       AS "pointsIssuedThisMonth",
                   COALESCE(-SUM(points) FILTER (WHERE ((points < 0 AND type <> 'ORDER_EARN_REVERSAL')
                                                        OR type IN ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND'))
                                                 AND created_at >= :monthStart), 0)
                       AS "pointsRedeemedThisMonth",
                   COUNT(DISTINCT user_id) FILTER (WHERE created_at >= :activeSince) AS "activeMembers"
            FROM {h-schema}point_transactions
            """, nativeQuery = true)
    TransactionAggregate aggregate(
            @Param("monthStart") LocalDateTime monthStart, @Param("activeSince") LocalDateTime activeSince);

    /// Kiểu số để `Number` vì driver trả bigint/numeric tuỳ hàm tổng hợp.
    interface TransactionAggregate {
        Number getTransactions();

        Number getPointsIssued();

        Number getPointsRedeemed();

        Number getPointsIssuedThisMonth();

        Number getPointsRedeemedThisMonth();

        Number getActiveMembers();
    }
}
