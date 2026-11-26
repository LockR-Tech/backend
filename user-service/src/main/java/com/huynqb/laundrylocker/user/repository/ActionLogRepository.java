package com.huynqb.laundrylocker.user.repository;

import com.huynqb.laundrylocker.user.model.ActionLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActionLogRepository
        extends JpaRepository<ActionLogRecord, Long>, JpaSpecificationExecutor<ActionLogRecord> {

    boolean existsByEventId(String eventId);
}
