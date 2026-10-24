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

/// Migration V5 trên Postgres thật: mỗi lần đổi voucher (reference) chỉ trừ điểm một lần và hoàn tối đa
/// một lần; hai lần đổi khác nhau không chặn nhau.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class VoucherExchangeUniqueIndexTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("loyalty_db")
                    .withUsername("loyalty_user")
                    .withPassword("loyalty_pass");

    @Autowired private PointTransactionRepository transactionRepository;

    @Test
    void exchangeAndRefundAreOncePerReference() {
        transactionRepository.saveAndFlush(transaction("VX-a", -10_000, LoyaltyService.VOUCHER_EXCHANGE_TYPE));
        transactionRepository.saveAndFlush(transaction("VX-a", 10_000, LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE));
        transactionRepository.saveAndFlush(transaction("VX-b", -10_000, LoyaltyService.VOUCHER_EXCHANGE_TYPE));

        assertTrue(transactionRepository.existsByReferenceAndType("VX-a", LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE));
        assertThrows(DataIntegrityViolationException.class, () -> transactionRepository.saveAndFlush(
                transaction("VX-a", -10_000, LoyaltyService.VOUCHER_EXCHANGE_TYPE)));
    }

    @Test
    void secondRefundForSameExchangeIsRejected() {
        transactionRepository.saveAndFlush(transaction("VX-c", 5_000, LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE));

        assertThrows(DataIntegrityViolationException.class, () -> transactionRepository.saveAndFlush(
                transaction("VX-c", 5_000, LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE)));
    }

    private static PointTransaction transaction(String reference, int points, String type) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(7L);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setReference(reference);
        return transaction;
    }
}
