package com.huynqb.laundrylocker.auth.repository;

import com.huynqb.laundrylocker.auth.model.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    Optional<AuthAccount> findByEmail(String email);

    Optional<AuthAccount> findByPhoneNumber(String phoneNumber);

    Optional<AuthAccount> findByUserId(Long userId);

    List<AuthAccount> findByUserIdIn(Collection<Long> userIds);
}
