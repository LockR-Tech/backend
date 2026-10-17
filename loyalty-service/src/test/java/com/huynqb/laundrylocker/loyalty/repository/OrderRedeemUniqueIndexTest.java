package com.huynqb.laundrylocker.loyalty.repository;

import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import com.huynqb.laundrylocker.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Migration V4 trên Postgres thật: mỗi đơn chỉ trừ điểm (ORDER_REDEEM) một lần và hoàn điểm
/// (ORDER_REDEEM_REFUND) một lần; cột amount lưu số tiền đã giảm.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderRedeemUniqueIndexTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("loyalty_db")
                    .withUsername("loyalty_user")
                    .withPassword("loyalty_pass");

    @Autowired private PointTransactionRepository transactionRepository;

    @Test
    void redeemAndRefundAreOncePerOrder() {
        transactionRepository.saveAndFlush(transaction(55L, -75, LoyaltyService.ORDER_REDEEM_TYPE));
        transactionRepository.saveAndFlush(transaction(55L, 75, LoyaltyService.ORDER_REDEEM_REFUND_TYPE));
        transactionRepository.saveAndFlush(transaction(56L, -10, LoyaltyService.ORDER_REDEEM_TYPE));

        assertEquals(0, new BigDecimal("7500").compareTo(
                transactionRepository.findFirstByOrderIdAndType(55L, LoyaltyService.ORDER_REDEEM_TYPE)
                        .orElseThrow().getAmount()));
        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(transaction(55L, -75, LoyaltyService.ORDER_REDEEM_TYPE)));
    }

    @Test
    void secondRefundForSameOrderIsRejected() {
        transactionRepository.saveAndFlush(transaction(57L, 20, LoyaltyService.ORDER_REDEEM_REFUND_TYPE));

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(
                        transaction(57L, 20, LoyaltyService.ORDER_REDEEM_REFUND_TYPE)));
    }

    private static PointTransaction transaction(Long orderId, int points, String type) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(7L);
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setAmount(BigDecimal.valueOf(Math.abs(points) * 100L));
        return transaction;
    }
}
