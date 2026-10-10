package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Chạy thật các truy vấn tổng hợp thống kê trên Postgres (Flyway + SQL native) để bắt lỗi
/// cú pháp FILTER/PERCENTILE_CONT, tên cột và schema mà unit test Mockito không thấy.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LoyaltyStatisticsQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("loyalty_db")
                    .withUsername("loyalty_user")
                    .withPassword("loyalty_pass");

    private static final LocalDateTime MONTH_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime ACTIVE_SINCE = LocalDateTime.of(2026, 9, 10, 0, 0);

    @Autowired private TestEntityManager em;
    @Autowired private LoyaltyAccountRepository accountRepository;
    @Autowired private PointTransactionRepository transactionRepository;

    @Test
    void aggregatesAccountsAndTransactionsAcrossMonthAndActivityWindows() {
        account(1L, 100, "BRONZE", LocalDateTime.of(2026, 8, 15, 3, 0));
        account(2L, 300, "SILVER", LocalDateTime.of(2026, 9, 5, 3, 0));
        account(3L, 1000, "GOLD", LocalDateTime.of(2026, 9, 20, 3, 0));
        account(4L, 0, "BRONZE", LocalDateTime.of(2026, 8, 31, 23, 59));

        transaction(1L, 150, "EARN", LocalDateTime.of(2026, 8, 15, 3, 0));
        transaction(1L, -50, "REDEEM", LocalDateTime.of(2026, 8, 20, 3, 0));
        transaction(2L, 300, "EARN", LocalDateTime.of(2026, 9, 5, 3, 0));
        transaction(3L, 1200, "EARN", LocalDateTime.of(2026, 9, 20, 3, 0));
        transaction(3L, -200, "REDEEM", LocalDateTime.of(2026, 9, 25, 3, 0));
        em.flush();
        em.clear();

        LoyaltyAccountRepository.AccountAggregate accounts = accountRepository.aggregate(MONTH_START);
        assertEquals(4L, accounts.getTotalMembers().longValue());
        assertEquals(2L, accounts.getNewMembersThisMonth().longValue());
        assertEquals(1400L, accounts.getTotalPoints().longValue());
        assertEquals(350.0, accounts.getAveragePoints().doubleValue(), 1e-9);
        // Trung vị của [0, 100, 300, 1000] = (100 + 300) / 2.
        assertEquals(200.0, accounts.getMedianPoints().doubleValue(), 1e-9);

        Map<String, Long> tiers = accountRepository.countByTier().stream()
                .collect(Collectors.toMap(
                        LoyaltyAccountRepository.TierCount::getTier,
                        LoyaltyAccountRepository.TierCount::getMembers));
        assertEquals(Map.of("BRONZE", 2L, "SILVER", 1L, "GOLD", 1L), tiers);

        PointTransactionRepository.TransactionAggregate transactions =
                transactionRepository.aggregate(MONTH_START, ACTIVE_SINCE);
        assertEquals(5L, transactions.getTransactions().longValue());
        assertEquals(1650L, transactions.getPointsIssued().longValue());
        assertEquals(250L, transactions.getPointsRedeemed().longValue());
        assertEquals(1500L, transactions.getPointsIssuedThisMonth().longValue());
        assertEquals(200L, transactions.getPointsRedeemedThisMonth().longValue());
        // Chỉ user 3 có giao dịch từ ACTIVE_SINCE (hai giao dịch vẫn tính một người).
        assertEquals(1L, transactions.getActiveMembers().longValue());
    }

    @Test
    void emptyTablesYieldZeroInsteadOfNull() {
        LoyaltyAccountRepository.AccountAggregate accounts = accountRepository.aggregate(MONTH_START);
        assertEquals(0L, accounts.getTotalMembers().longValue());
        assertEquals(0L, accounts.getTotalPoints().longValue());
        assertEquals(0.0, accounts.getAveragePoints().doubleValue(), 1e-9);
        assertEquals(0.0, accounts.getMedianPoints().doubleValue(), 1e-9);

        PointTransactionRepository.TransactionAggregate transactions =
                transactionRepository.aggregate(MONTH_START, ACTIVE_SINCE);
        assertEquals(0L, transactions.getTransactions().longValue());
        assertEquals(0L, transactions.getPointsIssued().longValue());
        assertEquals(0L, transactions.getPointsRedeemed().longValue());
        assertEquals(0L, transactions.getActiveMembers().longValue());
    }

    private void account(Long userId, int points, String tier, LocalDateTime createdAt) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setPoints(points);
        account.setTier(tier);
        em.persistAndFlush(account);
        // @PrePersist luôn ghi thời điểm hiện tại — đặt lại mốc tạo cho dữ liệu mẫu.
        em.getEntityManager()
                .createNativeQuery("UPDATE loyalty_schema.loyalty_accounts SET created_at = :at WHERE id = :id")
                .setParameter("at", createdAt)
                .setParameter("id", account.getId())
                .executeUpdate();
    }

    private void transaction(Long userId, int points, String type, LocalDateTime createdAt) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setPoints(points);
        transaction.setType(type);
        em.persistAndFlush(transaction);
        em.getEntityManager()
                .createNativeQuery("UPDATE loyalty_schema.point_transactions SET created_at = :at WHERE id = :id")
                .setParameter("at", createdAt)
                .setParameter("id", transaction.getId())
                .executeUpdate();
    }
}
