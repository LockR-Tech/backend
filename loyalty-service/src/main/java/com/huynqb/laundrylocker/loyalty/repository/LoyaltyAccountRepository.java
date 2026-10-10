package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByUserId(Long userId);

    /// Tổng hợp trên toàn bộ tài khoản cho thống kê admin (một lượt quét bảng).
    /// `monthStart` là đầu tháng kinh doanh đã quy về múi giờ lưu trữ.
    @Query(value = """
            SELECT COUNT(*) AS "totalMembers",
                   COUNT(*) FILTER (WHERE created_at >= :monthStart) AS "newMembersThisMonth",
                   COALESCE(SUM(points), 0) AS "totalPoints",
                   COALESCE(AVG(points), 0) AS "averagePoints",
                   COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY points), 0) AS "medianPoints"
            FROM {h-schema}loyalty_accounts
            """, nativeQuery = true)
    AccountAggregate aggregate(@Param("monthStart") LocalDateTime monthStart);

    @Query("select a.tier as tier, count(a) as members from LoyaltyAccount a group by a.tier")
    List<TierCount> countByTier();

    /// Kiểu số để `Number` vì driver trả bigint/numeric/double tuỳ hàm tổng hợp.
    interface AccountAggregate {
        Number getTotalMembers();

        Number getNewMembersThisMonth();

        Number getTotalPoints();

        Number getAveragePoints();

        Number getMedianPoints();
    }

    interface TierCount {
        String getTier();

        Long getMembers();
    }
}
