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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Migration V3 trên Postgres thật: mỗi đơn chỉ có một dòng điểm tự cộng (ORDER_EARN); điều chỉnh tay theo
/// cùng đơn vẫn ghi được.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderEarnUniqueIndexTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("loyalty_db")
                    .withUsername("loyalty_user")
                    .withPassword("loyalty_pass");

    @Autowired private PointTransactionRepository transactionRepository;

    @Test
    void secondOrderEarnForSameOrderIsRejected() {
        transactionRepository.saveAndFlush(transaction(55L, LoyaltyService.ORDER_EARN_TYPE));
        transactionRepository.saveAndFlush(transaction(55L, "ADJUSTMENT"));
        transactionRepository.saveAndFlush(transaction(56L, LoyaltyService.ORDER_EARN_TYPE));

        assertTrue(transactionRepository.existsByOrderIdAndType(55L, LoyaltyService.ORDER_EARN_TYPE));
        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(transaction(55L, LoyaltyService.ORDER_EARN_TYPE)));
    }

    private static PointTransaction transaction(Long orderId, String type) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(7L);
        transaction.setOrderId(orderId);
        transaction.setPoints(10);
        transaction.setType(type);
        return transaction;
    }
}
