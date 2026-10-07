package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.UserBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBankAccountRepository extends JpaRepository<UserBankAccount, Long> {
    Optional<UserBankAccount> findByUserId(Long userId);
}
