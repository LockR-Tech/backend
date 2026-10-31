package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Danh sách chi tiết phải cộng lại ra đúng con số trên thẻ thống kê (`aggregate`), kể cả các loại
/// giao dịch hoàn/thu hồi điểm.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LoyaltyDrilldownQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("loyalty_db")
                    .withUsername("loyalty_user")
                    .withPassword("loyalty_pass");

    private static final LocalDateTime ALL_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MONTH_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime UNTIL = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final long ANY_USER = -1L;
    private static final List<Long> NO_ORDERS = List.of(-1L);

    @Autowired private TestEntityManager em;
    @Autowired private LoyaltyAccountRepository accountRepository;
    @Autowired private PointTransactionRepository transactionRepository;

    private void seed() {
        account(1L, 700);
        account(2L, 50);
        account(3L, 0);
        transaction(1L, 1000, "ORDER_EARN", 101L, LocalDateTime.of(2026, 8, 10, 3, 0));
        transaction(1L, -300, "ORDER_REDEEM", 102L, LocalDateTime.of(2026, 8, 20, 3, 0));
        transaction(2L, 200, "ORDER_EARN", 103L, LocalDateTime.of(2026, 9, 5, 3, 0));
        transaction(2L, -100, "ORDER_EARN_REVERSAL", 103L, LocalDateTime.of(2026, 9, 6, 3, 0));
        transaction(2L, -80, "ORDER_REDEEM", 104L, LocalDateTime.of(2026, 9, 7, 3, 0));
        transaction(2L, 30, "ORDER_REDEEM_REFUND", 104L, LocalDateTime.of(2026, 9, 8, 3, 0));
        em.flush();
        em.clear();
    }

    private long sum(String scope, LocalDateTime since, long sign) {
        return transactionRepository.totalsByType(scope, since, UNTIL, ANY_USER, true, NO_ORDERS).stream()
                .mapToLong(row -> sign * row.getPoints().longValue())
                .sum();
    }

    @Test
    void scopesAddUpToStatisticCards() {
        seed();
        PointTransactionRepository.TransactionAggregate cards = transactionRepository.aggregate(MONTH_START, ALL_TIME);

        assertEquals(1100L, cards.getPointsIssued().longValue());
        assertEquals(350L, cards.getPointsRedeemed().longValue());
        assertEquals(cards.getPointsIssued().longValue(), sum("ISSUED", ALL_TIME, 1));
        assertEquals(cards.getPointsRedeemed().longValue(), sum("REDEEMED", ALL_TIME, -1));
        assertEquals(cards.getPointsIssuedThisMonth().longValue(), sum("ISSUED", MONTH_START, 1));
        assertEquals(cards.getPointsRedeemedThisMonth().longValue(), sum("REDEEMED", MONTH_START, -1));
        assertEquals(
                cards.getTransactions().longValue(),
                transactionRepository.findByScope("ALL", ALL_TIME, UNTIL, ANY_USER, true, NO_ORDERS, PageRequest.of(0, 2))
                        .getTotalElements());
    }

    @Test
    void memberRowsMatchTheirTransactionsAndKeepMembersWithoutAny() {
        seed();

        List<LoyaltyAccountRepository.MemberRow> rows = accountRepository.findMembers();

        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(LoyaltyAccountRepository.MemberRow::getUserId).toList());
        LoyaltyAccountRepository.MemberRow first = rows.get(0);
        assertEquals(1000L, first.getPointsIssued().longValue());
        assertEquals(300L, first.getPointsRedeemed().longValue());
        assertEquals(2L, first.getTransactionCount().longValue());
        LoyaltyAccountRepository.MemberRow second = rows.get(1);
        assertEquals(100L, second.getPointsIssued().longValue());
        assertEquals(50L, second.getPointsRedeemed().longValue());
        LoyaltyAccountRepository.MemberRow idle = rows.get(2);
        assertEquals(0L, idle.getTransactionCount().longValue());
        assertNull(idle.getLastTransactionAt());
    }

    @Test
    void filtersByMemberDateAndOrders() {
        seed();

        assertEquals(4L, transactionRepository
                .findByScope("ALL", ALL_TIME, UNTIL, 2L, true, NO_ORDERS, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2L, transactionRepository
                .findByScope("ALL", LocalDateTime.of(2026, 9, 6, 0, 0), LocalDateTime.of(2026, 9, 8, 0, 0),
                        ANY_USER, true, NO_ORDERS, PageRequest.of(0, 10))
                .getTotalElements());
        assertEquals(2L, transactionRepository
                .findByScope("ALL", ALL_TIME, UNTIL, ANY_USER, false, List.of(104L), PageRequest.of(0, 10))
                .getTotalElements());
        assertEquals(List.of(101L, 102L), transactionRepository
                .orderIdsByScope("ALL", ALL_TIME, UNTIL, 1L, true, NO_ORDERS).stream().sorted().toList());
    }

    @Test
    void topUsersAreSortedByPoints() {
        seed();

        List<Long> users = transactionRepository
                .totalsByUser("ISSUED", ALL_TIME, UNTIL, ANY_USER, true, NO_ORDERS, PageRequest.of(0, 20)).stream()
                .map(PointTransactionRepository.UserTotal::getUserId)
                .collect(Collectors.toList());

        assertEquals(List.of(1L, 2L), users);
    }

    private void account(Long userId, int points) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setPoints(points);
        em.persist(account);
    }

    private void transaction(Long userId, int points, String type, Long orderId, LocalDateTime createdAt) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setOrderId(orderId);
        em.persist(transaction);
        em.flush();
        em.getEntityManager()
                .createNativeQuery("UPDATE {h-schema}point_transactions SET created_at = :at WHERE id = :id")
                .setParameter("at", createdAt)
                .setParameter("id", transaction.getId())
                .executeUpdate();
    }
}
