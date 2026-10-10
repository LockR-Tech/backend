package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /// Tổng hợp giao dịch điểm cho thống kê admin (một lượt quét bảng). Điểm phát ra là
    /// tổng giao dịch dương; điểm đã dùng là tổng trị tuyệt đối giao dịch âm. Các mốc thời
    /// gian đã quy về múi giờ lưu trữ.
    @Query(value = """
            SELECT COUNT(*) AS "transactions",
                   COALESCE(SUM(points) FILTER (WHERE points > 0), 0) AS "pointsIssued",
                   COALESCE(-SUM(points) FILTER (WHERE points < 0), 0) AS "pointsRedeemed",
                   COALESCE(SUM(points) FILTER (WHERE points > 0 AND created_at >= :monthStart), 0)
                       AS "pointsIssuedThisMonth",
                   COALESCE(-SUM(points) FILTER (WHERE points < 0 AND created_at >= :monthStart), 0)
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
