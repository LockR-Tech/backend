package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /// Nhóm giao dịch tạo nên từng con số thống kê — cùng định nghĩa với `aggregate` bên dưới.
    String SCOPE_FILTER = """
             where t.createdAt >= :since
               and t.createdAt < :until
               and (:userId = -1 or t.userId = :userId)
               and (:anyOrder = true or t.orderId in :orderIds)
               and (:scope = 'ALL'
                    or (:scope = 'ISSUED'
                        and ((t.points > 0 and t.type not in ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND'))
                             or t.type = 'ORDER_EARN_REVERSAL'))
                    or (:scope = 'REDEEMED'
                        and ((t.points < 0 and t.type <> 'ORDER_EARN_REVERSAL')
                             or t.type in ('ORDER_REDEEM_REFUND', 'VOUCHER_EXCHANGE_REFUND'))))
            """;

    @Query("select t from PointTransaction t" + SCOPE_FILTER)
    Page<PointTransaction> findByScope(
            @Param("scope") String scope, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until,
            @Param("userId") long userId, @Param("anyOrder") boolean anyOrder, @Param("orderIds") Collection<Long> orderIds,
            Pageable pageable);

    @Query("select t.type as type, count(t) as transactions, coalesce(sum(t.points), 0) as points"
            + " from PointTransaction t" + SCOPE_FILTER + " group by t.type order by abs(coalesce(sum(t.points), 0)) desc")
    List<GroupTotal> totalsByType(
            @Param("scope") String scope, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until,
            @Param("userId") long userId, @Param("anyOrder") boolean anyOrder, @Param("orderIds") Collection<Long> orderIds);

    @Query("select t.userId as userId, count(t) as transactions, coalesce(sum(t.points), 0) as points"
            + " from PointTransaction t" + SCOPE_FILTER + " group by t.userId order by abs(coalesce(sum(t.points), 0)) desc")
    List<UserTotal> totalsByUser(
            @Param("scope") String scope, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until,
            @Param("userId") long userId, @Param("anyOrder") boolean anyOrder, @Param("orderIds") Collection<Long> orderIds,
            Pageable pageable);

    /// Các đơn có giao dịch khớp bộ lọc — để lọc theo loại đơn (loại đơn nằm ở order-service).
    @Query("select distinct t.orderId from PointTransaction t" + SCOPE_FILTER + " and t.orderId is not null")
    List<Long> orderIdsByScope(
            @Param("scope") String scope, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until,
            @Param("userId") long userId, @Param("anyOrder") boolean anyOrder, @Param("orderIds") Collection<Long> orderIds);

    interface GroupTotal {
        String getType();

        Long getTransactions();

        Number getPoints();
    }

    interface UserTotal {
        Long getUserId();

        Long getTransactions();

        Number getPoints();
    }

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
