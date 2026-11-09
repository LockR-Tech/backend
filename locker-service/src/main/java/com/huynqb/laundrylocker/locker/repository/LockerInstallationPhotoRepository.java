package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerInstallationPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LockerInstallationPhotoRepository extends JpaRepository<LockerInstallationPhoto, Long> {

    List<LockerInstallationPhoto> findByInstallationIdOrderByIdAsc(Long installationId);

    long countByInstallationId(Long installationId);

    boolean existsByPublicId(String publicId);
}
