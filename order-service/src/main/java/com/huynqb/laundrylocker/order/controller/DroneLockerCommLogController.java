package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import com.huynqb.laundrylocker.order.repository.DroneLockerCommLogRepository;
import com.huynqb.laundrylocker.order.service.DroneLockerCommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/drone-locker-logs")
@RequiredArgsConstructor
public class DroneLockerCommLogController {

    private final DroneLockerCommLogRepository commLogRepository;
    private final DroneLockerCommunicationService commService;

    @GetMapping
    public ApiResponse<PageResponse<DroneLockerCommLog>> searchLogs(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String droneId,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) Long compartmentId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        LocalDateTime from = parseDateTime(fromDate, false);
        LocalDateTime to = parseDateTime(toDate, true);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "timestamp", "id"));
        Page<DroneLockerCommLog> resultPage = commLogRepository.searchLogs(
                orderId,
                StringUtils.hasText(droneId) ? droneId.trim() : null,
                lockerId,
                compartmentId,
                StringUtils.hasText(eventType) ? eventType.trim() : null,
                StringUtils.hasText(processingStatus) ? processingStatus.trim() : null,
                from,
                to,
                pageable
        );

        PageResponse<DroneLockerCommLog> response = new PageResponse<>(
                resultPage.getContent(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );

        return ApiResponse.ok(response);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<List<DroneLockerCommLog>> getSessionTimeline(@PathVariable String sessionId) {
        List<DroneLockerCommLog> logs = commLogRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        return ApiResponse.ok(logs);
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<List<DroneLockerCommLog>> getOrderCommLogs(@PathVariable Long orderId) {
        List<DroneLockerCommLog> logs = commLogRepository.findByOrderIdOrderByTimestampAsc(orderId);
        return ApiResponse.ok(logs);
    }

    @GetMapping("/active-sessions")
    public ApiResponse<List<DroneLockerCommunicationService.DeliverySessionState>> getActiveSessions() {
        return ApiResponse.ok(commService.getActiveSessions());
    }

    private LocalDateTime parseDateTime(String text, boolean endOfDay) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        text = text.trim();
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse date string: {}", text);
            return null;
        }
    }
}

