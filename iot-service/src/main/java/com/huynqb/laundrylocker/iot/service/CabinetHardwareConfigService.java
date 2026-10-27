package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigRequest;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigView;
import com.huynqb.laundrylocker.iot.model.CabinetHardwareConfig;
import com.huynqb.laundrylocker.iot.repository.CabinetHardwareConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Tab "Cấu hình phần cứng" của trang Điều khiển tủ: xem / lưu bản đồ chân và sơ đồ đấu dây, rồi
/// "Áp dụng" ⇒ lệnh MQTT `hw-config` để Pi chạy theo bản đồ mới mà không cần sửa code hay `.env`.
///
/// Xem: ADMIN mọi tủ, KTV tủ chỉ tủ mình phụ trách. Lưu và áp dụng: **chỉ ADMIN** (đổi chân sai làm
/// relay nhả khoá nhầm ô).
@Slf4j
@Service
@RequiredArgsConstructor
public class CabinetHardwareConfigService {

    static final String DEFAULT_TEMPLATE = "hardware/cabinet-hardware-default.json";
    static final int MAX_WIRING_ROWS = 300;
    static final int MAX_WIRING_TEXT = 300;
    static final List<String> WIRING_FIELDS = List.of("group", "from", "to", "via", "wire", "note");

    private final CabinetHardwareConfigRepository repository;
    private final CabinetControlService control;
    private final LockerMqttService lockerMqttService;
    private final CabinetControlLogService controlLogs;
    private final ObjectMapper objectMapper;

    private volatile JsonNode template;

    public HardwareConfigView get(Long lockerId, Long userId, String roles) {
        control.requireLockerInScope(lockerId, userId, roles);
        return repository.findById(lockerId).map(this::view).orElseGet(() -> {
            JsonNode t = template();
            return new HardwareConfigView(lockerId, t.get("config"), t.get("wiring"), true,
                    null, null, null, null, null, null);
        });
    }

    public HardwareConfigView save(Long lockerId, HardwareConfigRequest body, Long userId, String roles) {
        requireAdmin(roles);
        control.requireLockerInScope(lockerId, userId, roles);
        JsonNode config = body == null ? null : body.config();
        List<String> errors = HardwareConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            throw new BusinessException("HW_CONFIG_INVALID", String.join("; ", errors), HttpStatus.BAD_REQUEST);
        }
        CabinetHardwareConfig entity = repository.findById(lockerId).orElseGet(() -> {
            CabinetHardwareConfig created = new CabinetHardwareConfig();
            created.setLockerId(lockerId);
            return created;
        });
        entity.setConfig(write(config));
        entity.setWiring(write(sanitizeWiring(body.wiring())));
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        return view(repository.save(entity));
    }

    /// Gửi cấu hình **đã lưu** xuống tủ. Pi báo ACCEPTED rồi khởi động lại bộ điều khiển (~10 giây).
    public Map<String, Object> apply(Long lockerId, Long userId, String roles) {
        requireAdmin(roles);
        control.requireLockerInScope(lockerId, userId, roles);
        CabinetHardwareConfig entity = repository.findById(lockerId).orElseThrow(() -> new BusinessException(
                "HW_CONFIG_NOT_SAVED", "Chưa lưu cấu hình nào cho tủ này — bấm Lưu trước khi áp dụng"));
        JsonNode config = read(entity.getConfig());
        List<String> errors = HardwareConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            throw new BusinessException("HW_CONFIG_INVALID", String.join("; ", errors), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> response = new LinkedHashMap<>(control.await("cấu hình phần cứng",
                () -> lockerMqttService.sendHardwareConfigAsync(lockerId, config)));
        JsonNode result = response.get("result") instanceof JsonNode node ? node : null;
        List<String> warnings = texts(result, "warnings");
        List<String> rejected = texts(result, "errors");
        response.put("warnings", warnings);
        response.put("errors", rejected);

        String status = String.valueOf(response.get("status"));
        String message = String.valueOf(response.get("message"));
        if (!warnings.isEmpty()) {
            message = message + " — " + String.join("; ", warnings);
        }
        entity.setAppliedBy(userId);
        entity.setAppliedAt(LocalDateTime.now());
        entity.setApplyStatus(status);
        entity.setApplyMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        repository.save(entity);
        controlLogs.recordWeb(lockerId, userId, "HW_CONFIG", null, null, null, status, message);
        return response;
    }

    private void requireAdmin(String roles) {
        if (!UserRoles.isAdmin(roles)) {
            throw new BusinessException("CABINET_CONFIG_ADMIN_ONLY",
                    "Chỉ quản trị viên được sửa và áp dụng cấu hình phần cứng", HttpStatus.FORBIDDEN);
        }
    }

    /// Chỉ giữ các cột đã biết, cắt độ dài, bỏ dòng trống; tối đa MAX_WIRING_ROWS dòng.
    ArrayNode sanitizeWiring(JsonNode wiring) {
        ArrayNode rows = objectMapper.createArrayNode();
        if (wiring == null || !wiring.isArray()) {
            return rows;
        }
        if (wiring.size() > MAX_WIRING_ROWS) {
            throw new BusinessException("HW_WIRING_TOO_LONG",
                    "Sơ đồ đấu dây tối đa " + MAX_WIRING_ROWS + " dòng", HttpStatus.BAD_REQUEST);
        }
        for (JsonNode row : wiring) {
            if (!row.isObject()) {
                continue;
            }
            ObjectNode clean = objectMapper.createObjectNode();
            boolean any = false;
            for (String field : WIRING_FIELDS) {
                String value = row.path(field).isValueNode() ? row.path(field).asText("").trim() : "";
                if (value.length() > MAX_WIRING_TEXT) {
                    value = value.substring(0, MAX_WIRING_TEXT);
                }
                any |= !value.isEmpty();
                clean.put(field, value);
            }
            if (any) {
                rows.add(clean);
            }
        }
        return rows;
    }

    private HardwareConfigView view(CabinetHardwareConfig e) {
        return new HardwareConfigView(e.getLockerId(), read(e.getConfig()), read(e.getWiring()), false,
                e.getUpdatedBy(), e.getUpdatedAt(), e.getAppliedBy(), e.getAppliedAt(),
                e.getApplyStatus(), e.getApplyMessage());
    }

    JsonNode template() {
        if (template == null) {
            try (InputStream in = new ClassPathResource(DEFAULT_TEMPLATE).getInputStream()) {
                template = objectMapper.readTree(in);
            } catch (IOException e) {
                throw new IllegalStateException("Thiếu mẫu cấu hình phần cứng " + DEFAULT_TEMPLATE, e);
            }
        }
        return template;
    }

    private static List<String> texts(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        if (node != null && node.path(field).isArray()) {
            node.get(field).forEach(item -> out.add(item.asText()));
        }
        return out;
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            log.warn("Stored hardware config is not valid JSON: {}", e.getMessage());
            return objectMapper.nullNode();
        }
    }
}
