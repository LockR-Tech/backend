package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BleOrderToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BleOrderTokenRepository extends JpaRepository<BleOrderToken, String> {

    Optional<BleOrderToken> findByNonceAndLockerId(String nonce, Long lockerId);

    /// Nhận xử lý sự kiện mở ô của mã: 1 ⇒ lần đầu, 0 ⇒ đã có sự kiện khác xử lý (Pi gửi lại, trùng).
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BleOrderToken t set t.processedAt = :at, t.result = :result "
            + "where t.nonce = :nonce and t.lockerId = :lockerId and t.processedAt is null")
    int claim(@Param("nonce") String nonce, @Param("lockerId") Long lockerId,
              @Param("result") String result, @Param("at") LocalDateTime at);
}
