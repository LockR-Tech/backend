package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerBox;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LockerBoxRepository extends JpaRepository<LockerBox, Long> {

    List<LockerBox> findByLockerId(Long lockerId);

    List<LockerBox> findByStatusAndReservedUntilBefore(String status, LocalDateTime cutoff);

    /// Sweep TTL bỏ qua một loại ô (DRONE): ô drone được giữ suốt vòng đời đơn drone (chờ KTV,
    /// hoãn bay…), chỉ order-service (job đối soát) biết đơn còn sống hay không.
    List<LockerBox> findByStatusAndReservedUntilBeforeAndCellTypeNot(
            String status, LocalDateTime cutoff, String cellType);

    /// Khoá dòng ô cho các chuyển trạng thái đọc-kiểm-ghi (reserve/occupy/release): hai đơn đặt
    /// cùng lúc không thể cùng thấy AVAILABLE rồi cùng giữ một ô.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LockerBox b where b.id = :id")
    Optional<LockerBox> findByIdForUpdate(@Param("id") Long id);

    List<LockerBox> findByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);

    List<LockerBox> findByLockerIdOrderByRowIndexAscColIndexAsc(Long lockerId);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndCellTypeAndActiveTrueOrderByBoxNumberAsc(
            Long lockerId, String status, String cellType);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndCellTypeAndSizeAndActiveTrueOrderByBoxNumberAsc(
            Long lockerId, String status, String cellType, String size);

    List<LockerBox> findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc(String status);

    List<LockerBox> findByLockerIdIn(java.util.Collection<Long> lockerIds);

    long countByLockerId(Long lockerId);

    long countByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);

    boolean existsByLockerIdAndBoxNumber(Long lockerId, Integer boxNumber);

    Optional<LockerBox> findByLockerIdAndBoxNumber(Long lockerId, Integer boxNumber);

    List<LockerBox> findByLockerIdAndIdIn(Long lockerId, java.util.Collection<Long> ids);

    List<LockerBox> findByLockerIdAndBoxNumberIn(Long lockerId, java.util.Collection<Integer> boxNumbers);

    List<LockerBox> findByArucoDictionaryAndArucoMarkerIdIsNotNull(String arucoDictionary);

    Optional<LockerBox> findByArucoDictionaryAndArucoMarkerId(String arucoDictionary, Integer arucoMarkerId);

    boolean existsByArucoDictionaryAndArucoMarkerId(String arucoDictionary, Integer arucoMarkerId);
}
