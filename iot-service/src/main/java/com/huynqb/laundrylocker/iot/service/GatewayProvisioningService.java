package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.iot.dto.AssignGatewayRequest;
import com.huynqb.laundrylocker.iot.dto.GatewayDeviceResponse;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/// Admin gán bộ điều khiển tủ (Pi) vào một tủ — ADR-0008, docs/01-overview/mqtt-contract.md § 3.
///
/// Pi tự báo qua `iot/{mac}/discovery/result` ⇒ lưu `gateway_devices`. Admin gán ⇒ gửi
/// `iot/{mac}/command/setup` kèm sơ đồ `[{boxId, slotIndex, row, column, label}]`; Pi mở thử
/// từng ô (tuỳ chọn) rồi báo `iot/{mac}/setup/progress` và `/setup/result`.
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayProvisioningService {

    private static final Pattern MAC = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    /// Pi gửi heartbeat mỗi 60 s; quá 150 s không thấy thì coi là mất kết nối.
    static final long ONLINE_WINDOW_SECONDS = 150;
    static final int SETUP_TEST_TIMEOUT_SECONDS = 10;

    private final GatewayDeviceRepository repository;
    private final LockerMqttService mqtt;
    private final CabinetLayoutLookup layoutLookup;
    private final ObjectMapper objectMapper;
    private final BoxAccessLogRepository boxAccessLogRepository;

    static String normalizeMac(String raw) {
        if (raw == null) {
            return null;
        }
        String mac = raw.trim().toUpperCase(Locale.ROOT).replace('-', ':');
        return MAC.matcher(mac).matches() ? mac : null;
    }

    // ─── Bản tin từ Pi ───

    @Transactional
    public void onDiscovery(String topicMac, JsonNode data) {
        String mac = normalizeMac(topicMac);
        if (mac == null || !mac.equals(normalizeMac(text(data, "macAddress")))) {
            log.warn("Discovery ignored: topic MAC {} vs payload {}", topicMac, text(data, "macAddress"));
            return;
        }
        GatewayDevice device = repository.findByMacAddress(mac).orElseGet(() -> {
            GatewayDevice created = new GatewayDevice();
            created.setMacAddress(mac);
            log.info("New cabinet controller discovered: {}", mac);
            return created;
        });
        device.setHardware(text(data, "hardware"));
        device.setFirmwareVersion(text(data, "firmwareVersion"));
        device.setReportedLockerId(data.hasNonNull("lockerId") ? data.get("lockerId").asLong() : null);
        JsonNode slaves = data.path("slaves");
        if (slaves.isArray() && !slaves.isEmpty()) {
            JsonNode first = slaves.get(0);
            if (first.hasNonNull("slaveId")) {
                device.setSlaveId(first.get("slaveId").asInt());
            }
            device.setAvailableSlots(first.hasNonNull("availableSlots") ? first.get("availableSlots").asInt() : null);
        } else if (slaves.isArray()) {
            device.setAvailableSlots(0);   // Pi báo không thấy phần cứng
        }
        device.setLastSeenAt(LocalDateTime.now());
        repository.save(device);

        // Ghi nhận nhật ký phát hiện bộ điều khiển tủ
        Long targetLockerId = device.getLockerId() != null ? device.getLockerId() : device.getReportedLockerId();
        if (targetLockerId != null && boxAccessLogRepository != null) {
            BoxAccessLog logEntry = new BoxAccessLog();
            logEntry.setLockerId(targetLockerId);
            logEntry.setBoxId(0L);
            logEntry.setCredentialType("DISCOVERY");
            logEntry.setResult("ONLINE");
            String hwStr = device.getHardware() != null ? device.getHardware().toUpperCase() : "GPIO";
            int slots = device.getAvailableSlots() != null ? device.getAvailableSlots() : 7;
            String fw = device.getFirmwareVersion() != null ? device.getFirmwareVersion() : "v1.0.0";
            String seenTime = LocalDateTime.now().format(TIME_FMT);
            logEntry.setMessage(String.format("%s · %d ô phần cứng · firmware %s · thấy lần cuối %s",
                    hwStr, slots, fw, seenTime));
            boxAccessLogRepository.save(logEntry);
        }
    }

    /// Heartbeat có MAC ⇒ cập nhật "lần cuối thấy". Không tạo thiết bị mới (chờ discovery).
    @Transactional
    public void touch(String rawMac) {
        String mac = normalizeMac(rawMac);
        if (mac == null) {
            return;
        }
        repository.findByMacAddress(mac).ifPresent(device -> {
            device.setLastSeenAt(LocalDateTime.now());
            repository.save(device);
        });
    }

    @Transactional
    public void onSetupProgress(String topicMac, JsonNode data) {
        findForCommand(topicMac, data).ifPresent(device -> {
            JsonNode progress = data.path("progress");
            device.setSetupStatus("RUNNING");
            device.setSetupProgress(progress.path("tested").asInt() + "/" + progress.path("total").asInt());
            device.setLastSeenAt(LocalDateTime.now());
            repository.save(device);
        });
    }

    @Transactional
    public void onSetupResult(String topicMac, JsonNode data) {
        findForCommand(topicMac, data).ifPresent(device -> {
            String status = text(data, "status");
            device.setSetupStatus(Set.of("COMPLETED", "PARTIAL", "FAILED").contains(status) ? status : "FAILED");
            JsonNode summary = data.path("summary");
            device.setSetupProgress(summary.path("totalOk").asInt() + "/" + summary.path("total").asInt());
            device.setSetupResult(data.toString());
            device.setSetupFinishedAt(LocalDateTime.now());
            device.setLastSeenAt(LocalDateTime.now());
            repository.save(device);
            log.info("Setup {} of {} for locker {}: {}", text(data, "commandId"), device.getMacAddress(),
                    device.getLockerId(), status);

            // Ghi nhận nhật ký kiểm tra sơ đồ phần cứng hoặc cảnh báo lỗi
            if (device.getLockerId() != null && boxAccessLogRepository != null) {
                BoxAccessLog audit = new BoxAccessLog();
                audit.setLockerId(device.getLockerId());
                audit.setBoxId(0L);
                audit.setCredentialType("HARDWARE");
                if ("COMPLETED".equals(status)) {
                    audit.setResult("SUCCESS");
                    audit.setMessage("Kiểm tra sơ đồ phần cứng thành công: " + device.getSetupProgress() + " ô hoạt động tốt");
                } else {
                    audit.setResult("FAILED");
                    String err = data.hasNonNull("errorMessage") ? data.get("errorMessage").asText() :
                            "Phần cứng có ô kẹt chốt hoặc cảm biến không phản hồi (" + device.getSetupProgress() + ")";
                    audit.setMessage("Cảnh báo lỗi phần cứng: Kiểm tra sơ đồ " + status + " - " + err);
                }
                boxAccessLogRepository.save(audit);
            }
        });
    }

    /// Chỉ nhận kết quả của lệnh setup gần nhất — bản tin cũ (lệnh trước đó) bị bỏ qua.
    private java.util.Optional<GatewayDevice> findForCommand(String topicMac, JsonNode data) {
        String mac = normalizeMac(topicMac);
        String commandId = text(data, "commandId");
        return repository.findByMacAddress(mac == null ? "" : mac)
                .filter(device -> commandId != null && commandId.equals(device.getSetupCommandId()));
    }

    // ─── Thao tác của admin ───

    @Transactional(readOnly = true)
    public List<GatewayDeviceResponse> list() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GatewayDeviceResponse getByLockerId(Long lockerId) {
        if (lockerId == null) return null;
        return repository.findByLockerId(lockerId).map(this::toResponse).orElse(null);
    }

    @Transactional
    public GatewayDeviceResponse assign(Long id, AssignGatewayRequest request) {
        GatewayDevice device = repository.findById(id).orElseThrow(() -> new NotFoundException("Gateway", id));
        Long lockerId = request.lockerId();
        requireBroker();

        repository.findByLockerId(lockerId)
                .filter(other -> !other.getId().equals(device.getId()))
                .ifPresent(other -> {
                    throw new BusinessException("LOCKER_ALREADY_ASSIGNED",
                            "Tủ " + lockerId + " đang gắn thiết bị " + other.getMacAddress() + " — gỡ thiết bị đó trước",
                            HttpStatus.CONFLICT);
                });

        LockerLayoutView layout = layoutLookup.layout(lockerId).orElseThrow(() -> new BusinessException(
                "LOCKER_LAYOUT_UNAVAILABLE",
                "Không tải được sơ đồ tủ " + lockerId + " (tủ không tồn tại hoặc locker-service lỗi)",
                HttpStatus.BAD_GATEWAY));
        List<LockerLayoutView.Cell> cells = validCells(layout, device);

        String commandId = UUID.randomUUID().toString();
        ObjectNode payload = setupPayload(device, layout, cells, commandId, !Boolean.FALSE.equals(request.testDoors()));

        device.setLockerId(lockerId);
        device.setSetupStatus("PENDING");
        device.setSetupCommandId(commandId);
        device.setSetupProgress(null);
        device.setSetupResult(null);
        device.setSetupRequestedAt(LocalDateTime.now());
        device.setSetupFinishedAt(null);
        GatewayDevice saved = repository.save(device);

        // Gửi sau commit: Pi trả lời nhanh (không mở thử ô) có thể tới trước khi commit xong,
        // lúc đó setupCommandId chưa có trong DB và kết quả bị bỏ qua.
        publishAfterCommit(saved, "iot/" + device.getMacAddress() + "/command/setup", payload);
        return toResponse(saved);
    }

    @Transactional
    public GatewayDeviceResponse unassign(Long id) {
        GatewayDevice device = repository.findById(id).orElseThrow(() -> new NotFoundException("Gateway", id));
        requireBroker();
        String commandId = UUID.randomUUID().toString();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", "CLEAR_SETUP");
        payload.put("commandId", commandId);

        device.setLockerId(null);
        device.setSetupStatus("CLEARED");
        device.setSetupCommandId(commandId);
        device.setSetupProgress(null);
        device.setSetupRequestedAt(LocalDateTime.now());
        device.setSetupFinishedAt(null);
        GatewayDevice saved = repository.save(device);
        publishAfterCommit(saved, "iot/" + device.getMacAddress() + "/command/clear-setup", payload);
        return toResponse(saved);
    }

    /// Yêu cầu Pi báo lại discovery (số ô, tủ đang phục vụ).
    public void rediscover(Long id) {
        GatewayDevice device = repository.findById(id).orElseThrow(() -> new NotFoundException("Gateway", id));
        requireBroker();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("maxCabinets", 1);
        try {
            mqtt.publish("iot/" + device.getMacAddress() + "/discovery/start", payload);
        } catch (Exception ex) {
            throw new BusinessException("MQTT_UNAVAILABLE", "Không gửi được lệnh tới thiết bị: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /// Xoá thiết bị khỏi danh sách (Pi thử nghiệm, Pi đã thay). Đang gắn tủ thì phải gỡ trước.
    @Transactional
    public void forget(Long id) {
        GatewayDevice device = repository.findById(id).orElseThrow(() -> new NotFoundException("Gateway", id));
        if (device.getLockerId() != null) {
            throw new BusinessException("GATEWAY_ASSIGNED", "Thiết bị đang gắn tủ " + device.getLockerId() + " — gỡ trước",
                    HttpStatus.CONFLICT);
        }
        repository.delete(device);
    }

    // ─── nội bộ ───

    private void requireBroker() {
        if (!mqtt.isConnected()) {
            throw new BusinessException("MQTT_UNAVAILABLE", "iot-service chưa kết nối được broker MQTT",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private List<LockerLayoutView.Cell> validCells(LockerLayoutView layout, GatewayDevice device) {
        List<LockerLayoutView.Cell> cells = layout.cells() == null ? List.of() : layout.cells().stream()
                .filter(cell -> cell.id() != null && cell.boxNumber() != null)
                .sorted(Comparator.comparing(LockerLayoutView.Cell::boxNumber))
                .toList();
        if (cells.isEmpty()) {
            throw new BusinessException("LOCKER_HAS_NO_BOXES", "Tủ " + layout.lockerId() + " chưa có ô nào");
        }
        Set<Integer> seen = new HashSet<>();
        for (LockerLayoutView.Cell cell : cells) {
            if (cell.boxNumber() < 1) {
                throw new BusinessException("INVALID_BOX_NUMBER", "Số ô phải từ 1 trở lên (ô id " + cell.id() + ")");
            }
            if (!seen.add(cell.boxNumber())) {
                throw new BusinessException("DUPLICATE_BOX_NUMBER", "Tủ có hai ô cùng số " + cell.boxNumber());
            }
        }
        Integer hardwareSlots = device.getAvailableSlots();
        int highest = cells.get(cells.size() - 1).boxNumber();
        if (hardwareSlots != null && highest > hardwareSlots) {
            throw new BusinessException("LAYOUT_EXCEEDS_HARDWARE",
                    "Tủ có ô số " + highest + " nhưng thiết bị chỉ điều khiển được " + hardwareSlots + " ô");
        }
        return cells;
    }

    ObjectNode setupPayload(GatewayDevice device, LockerLayoutView layout, List<LockerLayoutView.Cell> cells,
                            String commandId, boolean testDoors) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", "SETUP_LOCKERS");
        payload.put("commandId", commandId);
        payload.put("macAddress", device.getMacAddress());
        payload.put("lockerId", layout.lockerId());
        payload.put("cabinetId", String.valueOf(layout.lockerId()));
        payload.put("cabinetCode", layout.code());
        payload.put("slaveId", device.getSlaveId() == null ? 1 : device.getSlaveId());
        payload.put("totalRows", (int) cells.stream().map(LockerLayoutView.Cell::rowIndex).filter(Objects::nonNull).distinct().count());
        payload.put("totalColumns", (int) cells.stream().map(LockerLayoutView.Cell::colIndex).filter(Objects::nonNull).distinct().count());
        payload.put("testDoors", testDoors);
        payload.put("testTimeout", SETUP_TEST_TIMEOUT_SECONDS);
        ArrayNode lockerLayout = payload.putArray("lockerLayout");
        for (LockerLayoutView.Cell cell : cells) {
            ObjectNode slot = lockerLayout.addObject();
            slot.put("boxId", cell.id());
            slot.put("slotIndex", cell.slotIndex());
            slot.put("row", cell.rowIndex() == null ? 0 : cell.rowIndex());
            slot.put("column", cell.colIndex() == null ? 0 : cell.colIndex());
            slot.put("label", String.valueOf(cell.boxNumber()));
        }
        return payload;
    }

    private void publishAfterCommit(GatewayDevice device, String topic, ObjectNode payload) {
        Runnable send = () -> {
            try {
                mqtt.publish(topic, payload);
                log.info("Published {} to {}", payload.path("action").asText(), topic);
            } catch (Exception ex) {
                log.warn("Publish to {} failed: {}", topic, ex.getMessage());
                repository.findById(device.getId()).ifPresent(latest -> {
                    if (Objects.equals(latest.getSetupCommandId(), payload.path("commandId").asText())) {
                        latest.setSetupStatus("FAILED");
                        latest.setSetupResult("{\"errorMessage\":\"Không gửi được lệnh qua MQTT\"}");
                        latest.setSetupFinishedAt(LocalDateTime.now());
                        repository.save(latest);
                    }
                });
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    GatewayDeviceResponse toResponse(GatewayDevice device) {
        LocalDateTime lastSeen = device.getLastSeenAt();
        boolean online = lastSeen != null && lastSeen.isAfter(LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS));
        JsonNode result = null;
        if (device.getSetupResult() != null) {
            try {
                result = objectMapper.readTree(device.getSetupResult());
            } catch (Exception ex) {
                log.debug("Unparsable setup result for {}: {}", device.getMacAddress(), ex.getMessage());
            }
        }
        return new GatewayDeviceResponse(
                device.getId(), device.getMacAddress(), device.getHardware(), device.getFirmwareVersion(),
                device.getSlaveId(), device.getAvailableSlots(), device.getReportedLockerId(), device.getLockerId(),
                online, device.getSetupStatus(), device.getSetupProgress(), result,
                device.getSetupRequestedAt(), device.getSetupFinishedAt(), lastSeen);
    }

    private static String text(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText() : null;
    }
}
