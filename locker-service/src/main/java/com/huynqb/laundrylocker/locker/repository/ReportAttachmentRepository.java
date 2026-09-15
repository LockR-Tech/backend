package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.AttachmentStage;
import com.huynqb.laundrylocker.locker.model.ReportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportAttachmentRepository extends JpaRepository<ReportAttachment, Long> {

    List<ReportAttachment> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);

    List<ReportAttachment> findByReportIdAndStageOrderByCreatedAtAscIdAsc(Long reportId, AttachmentStage stage);

    List<ReportAttachment> findByReportIdInOrderByCreatedAtAscIdAsc(Collection<Long> reportIds);

    List<ReportAttachment> findByRepairLogIdInOrderByCreatedAtAscIdAsc(Collection<Long> repairLogIds);

    long countByReportId(Long reportId);

    long countByReportIdAndStage(Long reportId, AttachmentStage stage);

    boolean existsByPublicId(String publicId);
}
