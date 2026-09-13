package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerReportRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LockerReportRatingRepository extends JpaRepository<LockerReportRating, Long> {
    Optional<LockerReportRating> findByReportId(Long reportId);

    List<LockerReportRating> findByReportIdIn(List<Long> reportIds);
}
