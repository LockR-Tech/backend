package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.TechnicianPresenceRequest;
import com.huynqb.laundrylocker.locker.dto.TechnicianPresenceResponse;
import com.huynqb.laundrylocker.locker.model.TechnicianPresence;
import com.huynqb.laundrylocker.locker.repository.TechnicianPresenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicianPresenceService {

    private final TechnicianPresenceRepository presenceRepository;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public TechnicianPresenceResponse updatePresence(Long userId, String roles, TechnicianPresenceRequest request) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User ID is required", HttpStatus.BAD_REQUEST);
        }

        String technicianType = resolveTechnicianType(roles);
        TechnicianPresence presence = presenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    TechnicianPresence p = new TechnicianPresence();
                    p.setUserId(userId);
                    p.setTechnicianType(technicianType);
                    p.setOnDuty(false);
                    return p;
                });

        presence.setTechnicianType(technicianType);

        if (request.onDuty() != null) {
            boolean wasOnDuty = Boolean.TRUE.equals(presence.getOnDuty());
            boolean nowOnDuty = Boolean.TRUE.equals(request.onDuty());
            if (!wasOnDuty && nowOnDuty) {
                presence.setShiftStartedAt(LocalDateTime.now());
            } else if (wasOnDuty && !nowOnDuty) {
                presence.setShiftEndedAt(LocalDateTime.now());
            }
            presence.setOnDuty(nowOnDuty);
        }

        if (request.latitude() != null && request.longitude() != null) {
            validateCoordinates(request.latitude(), request.longitude());
            presence.setLatitude(request.latitude());
            presence.setLongitude(request.longitude());
            presence.setAccuracyM(request.accuracyM());
            presence.setHeadingDeg(request.headingDeg());
            presence.setSpeedMs(request.speedMs());
            presence.setAltitudeM(request.altitudeM());
            presence.setLastLocatedAt(LocalDateTime.now());
        }

        if (request.batteryLevel() != null) {
            presence.setBatteryLevel(request.batteryLevel());
        }
        if (request.deviceInfo() != null) {
            presence.setDeviceInfo(request.deviceInfo().trim());
        }

        TechnicianPresence saved = presenceRepository.save(presence);
        TechnicianPresenceResponse resp = toResponse(saved);
        publishPresenceUpdated(resp);
        return resp;
    }

    private void publishPresenceUpdated(TechnicianPresenceResponse resp) {
        if (rabbitTemplate == null || resp == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", resp.userId());
            if (resp.userName() != null) payload.put("userName", resp.userName());
            if (resp.technicianType() != null) payload.put("technicianType", resp.technicianType());
            payload.put("onDuty", Boolean.TRUE.equals(resp.onDuty()));
            if (resp.latitude() != null) payload.put("latitude", resp.latitude());
            if (resp.longitude() != null) payload.put("longitude", resp.longitude());
            if (resp.accuracyM() != null) payload.put("accuracyM", resp.accuracyM());
            if (resp.headingDeg() != null) payload.put("headingDeg", resp.headingDeg());
            if (resp.speedMs() != null) payload.put("speedMs", resp.speedMs());
            if (resp.altitudeM() != null) payload.put("altitudeM", resp.altitudeM());
            if (resp.batteryLevel() != null) payload.put("batteryLevel", resp.batteryLevel());
            if (resp.deviceInfo() != null) payload.put("deviceInfo", resp.deviceInfo());
            if (resp.shiftStartedAt() != null) payload.put("shiftStartedAt", resp.shiftStartedAt().toString());
            if (resp.shiftEndedAt() != null) payload.put("shiftEndedAt", resp.shiftEndedAt().toString());
            if (resp.lastLocatedAt() != null) payload.put("lastLocatedAt", resp.lastLocatedAt().toString());
            if (resp.updatedAt() != null) payload.put("updatedAt", resp.updatedAt().toString());

            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.TECHNICIAN_PRESENCE_UPDATED,
                    DomainEvent.of(
                            DomainEventNames.TECHNICIAN_PRESENCE_UPDATED,
                            "locker-service",
                            payload
                    )
            );
            log.info("Published TECHNICIAN_PRESENCE_UPDATED event for KTV #{} (onDuty={})", resp.userId(), resp.onDuty());
        } catch (Exception ex) {
            log.warn("Failed to publish TECHNICIAN_PRESENCE_UPDATED for user {}: {}", resp.userId(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TechnicianPresenceResponse getPresence(Long userId, String roles) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User ID is required", HttpStatus.BAD_REQUEST);
        }
        return presenceRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> TechnicianPresenceResponse.empty(
                        userId,
                        resolveUserName(userId),
                        resolveTechnicianType(roles)
                ));
    }

    @Transactional(readOnly = true)
    public List<TechnicianPresenceResponse> getActivePresences(String filterType) {
        List<TechnicianPresence> list;
        if (StringUtils.hasText(filterType)) {
            list = presenceRepository.findByOnDutyTrueAndTechnicianType(filterType.trim().toUpperCase());
        } else {
            list = presenceRepository.findByOnDutyTrue();
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianPresenceResponse> getAllPresences(String filterType) {
        List<TechnicianPresence> list;
        if (StringUtils.hasText(filterType)) {
            list = presenceRepository.findByTechnicianType(filterType.trim().toUpperCase());
        } else {
            list = presenceRepository.findAllByOrderByLastLocatedAtDesc();
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianPresenceResponse> getAllPresences() {
        return getAllPresences(null);
    }

    @Transactional(readOnly = true)
    public Optional<TechnicianPresence> findLiveTechnician(Long userId) {
        if (userId == null) return Optional.empty();
        return presenceRepository.findByUserId(userId)
                .filter(p -> Boolean.TRUE.equals(p.getOnDuty()) && p.getLatitude() != null && p.getLongitude() != null);
    }

    private static String resolveTechnicianType(String roles) {
        var parsed = UserRoles.parse(roles);
        if (parsed.contains("DRONE_TECHNICIAN")) {
            return "DRONE_TECHNICIAN";
        }
        if (parsed.contains("LOCKER_TECHNICIAN")) {
            return "LOCKER_TECHNICIAN";
        }
        return "TECHNICIAN";
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new BusinessException("INVALID_COORDINATES", "Latitude or longitude is out of range", HttpStatus.BAD_REQUEST);
        }
    }

    private TechnicianPresenceResponse toResponse(TechnicianPresence p) {
        return new TechnicianPresenceResponse(
                p.getUserId(),
                resolveUserName(p.getUserId()),
                p.getTechnicianType(),
                Boolean.TRUE.equals(p.getOnDuty()),
                p.getLatitude(),
                p.getLongitude(),
                p.getAccuracyM(),
                p.getHeadingDeg(),
                p.getSpeedMs(),
                p.getAltitudeM(),
                p.getBatteryLevel(),
                p.getDeviceInfo(),
                p.getShiftStartedAt(),
                p.getShiftEndedAt(),
                p.getLastLocatedAt(),
                p.getUpdatedAt()
        );
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        try {
            var resp = userClient.getUser(userId);
            if (resp != null && resp.data() != null && StringUtils.hasText(resp.data().fullName())) {
                return resp.data().fullName();
            }
        } catch (Exception ignored) {}
        return "KTV #" + userId;
    }
}

