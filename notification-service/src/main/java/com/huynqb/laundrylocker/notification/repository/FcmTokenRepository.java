package com.huynqb.laundrylocker.notification.repository;

import com.huynqb.laundrylocker.notification.model.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findByUserId(Long userId);

    @Query("select distinct f.token from FcmToken f")
    List<String> findAllDistinctTokens();

    void deleteByUserId(Long userId);

    void deleteByUserIdAndToken(Long userId, String token);
}
