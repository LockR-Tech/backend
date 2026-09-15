package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.Wallet;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.huynqb.laundrylocker.payment.repository.PaymentSpecifications.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Chạy thật các Specification trên H2 để bắt lỗi truy vấn Criteria (tên field,
/// coalesce, subquery) mà unit test Mockito không thấy.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class PaymentSpecificationsJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;

    private PaymentRecord walletOrderPayment;
    private PaymentRecord cashOrderPayment;

    @BeforeEach
    void seed() {
        walletOrderPayment = persistPayment(10L, 44L, "WALLET", "COMPLETED", "PAY-10-1", LocalDateTime.of(2026, 9, 5, 3, 0));
        cashOrderPayment = persistPayment(11L, 45L, "CASH", "COMPLETED", "PAY-11-1", LocalDateTime.of(2026, 8, 30, 3, 0));
        persistPayment(0L, 44L, "VNPAY_TOPUP", "COMPLETED", "TOPUP_44_1", LocalDateTime.of(2026, 9, 6, 3, 0));
        persistPayment(12L, 44L, "VNPAY", "FAILED", "PAY-12-1", LocalDateTime.of(2026, 9, 7, 3, 0));

        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(walletOrderPayment.getId());
        refund.setOrderId(10L);
        refund.setAmount(BigDecimal.valueOf(2000));
        refund.setStatus("COMPLETED");
        em.persist(refund);
        refund.setProcessedAt(LocalDateTime.of(2026, 9, 8, 0, 0));

        Wallet wallet = new Wallet();
        wallet.setUserId(44L);
        em.persist(wallet);
        WalletTransaction tx = new WalletTransaction();
        tx.setWalletId(wallet.getId());
        tx.setUserId(44L);
        tx.setType("DEBIT");
        tx.setAmount(BigDecimal.valueOf(15000));
        tx.setBalanceAfter(BigDecimal.ZERO);
        tx.setSource("ORDER_PAYMENT");
        tx.setReferenceId("ORDERPAY-10");
        em.persist(tx);
        em.flush();
    }

    @Test
    void paidBetweenWithOrderKindExcludesTopupsFailuresAndOtherMonths() {
        List<PaymentRecord> result = paymentRepository.findAll(all(
                paymentPaidBetween(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0)),
                paymentKind("ORDER")), Sort.by("id"));

        assertEquals(List.of(walletOrderPayment.getId()), result.stream().map(PaymentRecord::getId).toList());
    }

    @Test
    void searchFiltersCombineStatusMethodKindUserAndReference() {
        assertEquals(1, paymentRepository.findAll(all(paymentKind("TOPUP"))).size());
        assertEquals(3, paymentRepository.findAll(all(paymentKind("ORDER"))).size());
        assertEquals(2, paymentRepository.findAll(all(paymentMethodIn(List.of("wallet", "cash")))).size());
        assertEquals(1, paymentRepository.findAll(all(paymentStatusIn(List.of("FAILED")), paymentUser(44L))).size());
        assertEquals(1, paymentRepository.findAll(all(paymentReferenceLike("pay-11"))).size());
        assertEquals(1, paymentRepository.findAll(all(paymentReferenceLike("PAY-12"))).size());
        assertEquals(4, paymentRepository.findAll(all(paymentStatusIn(List.of(" ")), null)).size());
    }

    @Test
    void refundFiltersUseCoalescedTimeAndPaymentOwner() {
        assertEquals(1, refundRepository.findAll(all(
                completedOrderRefundBetween(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0)),
                refundUser(44L))).size());
        assertEquals(0, refundRepository.findAll(all(refundUser(45L))).size());
        assertEquals(1, refundRepository.findAll(all(refundPayment(walletOrderPayment.getId()), refundOrder(10L),
                refundStatusIn(List.of("completed")))).size());
    }

    @Test
    void walletFiltersMatchTypeSourceAndReference() {
        assertEquals(1, walletTransactionRepository.findAll(all(
                walletUser(44L), walletTypeIn(List.of("DEBIT")), walletSourceIn(List.of("ORDER_PAYMENT")),
                walletReferenceLike("orderpay"))).size());
        assertEquals(0, walletTransactionRepository.findAll(all(walletSourceIn(List.of("TOPUP")))).size());
    }

    private PaymentRecord persistPayment(Long orderId, Long userId, String method, String status, String ref, LocalDateTime at) {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(BigDecimal.valueOf(15000));
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setReferenceId(ref);
        em.persist(payment);
        em.flush();
        // @PrePersist ghi đè createdAt/updatedAt ⇒ đặt lại bằng native update.
        em.getEntityManager()
                .createQuery("update PaymentRecord p set p.createdAt = :at, p.updatedAt = :at where p.id = :id")
                .setParameter("at", at)
                .setParameter("id", payment.getId())
                .executeUpdate();
        em.clear();
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }
}
