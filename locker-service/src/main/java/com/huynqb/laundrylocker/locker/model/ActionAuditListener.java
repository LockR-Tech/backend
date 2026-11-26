package com.huynqb.laundrylocker.locker.model;

import com.huynqb.laundrylocker.common.audit.ActionLog;
import com.huynqb.laundrylocker.common.audit.RequestActor;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Nhật ký thao tác cho tủ, ô và drone (F3-G16) — ghi ở tầng thực thể nên bắt được MỌI luồng đổi (web admin,
/// app KTV, phiếu sự cố tự chặn tủ, kiosk…), người thao tác lấy từ {@link RequestActor}.
///
/// Không ghi những thay đổi tự động theo đơn hàng / chuyến bay (ô giữ chỗ ↔ có hàng ↔ trống, drone bay ↔ sạc), trừ
/// khi chính admin / KTV bấm — để nhật ký chỉ có việc của con người và sự cố, không bị ngập.
@Component
public class ActionAuditListener {

    /// Trạng thái ô đổi theo vòng đời đơn — không phải thao tác vận hành.
    private static final Set<String> ORDER_BOX_STATUSES = Set.of("AVAILABLE", "RESERVED", "OCCUPIED");
    /// Trạng thái drone đổi theo chuyến bay.
    private static final Set<String> FLIGHT_DRONE_STATUSES =
            Set.of(DroneStatus.IDLE, DroneStatus.RESERVED, DroneStatus.CHARGING, DroneStatus.IN_FLIGHT);
    private static final Set<String> LOCKER_INFO_FIELDS =
            Set.of("code", "name", "address", "latitude", "longitude", "storeId", "description");

    private final ObjectProvider<ActionLog> actionLog;

    public ActionAuditListener(ObjectProvider<ActionLog> actionLog) {
        this.actionLog = actionLog;
    }

    @PostLoad
    void loaded(Object entity) {
        if (entity instanceof AuditTracked tracked) {
            tracked.setAuditBaseline(tracked.auditFields());
        }
    }

    @PostPersist
    void created(Object entity) {
        switch (entity) {
            case LockerUnit locker -> record(lockerEntry("LOCKER_CREATED", locker).change(null, locker.getStatus()));
            case LockerBox box -> record(boxEntry("BOX_CREATED", box).change(null, box.getStatus()));
            case DroneUnit drone -> record(droneEntry("DRONE_CREATED", drone).change(null, drone.getStatus()));
            default -> {
            }
        }
        if (entity instanceof AuditTracked tracked) {
            tracked.setAuditBaseline(tracked.auditFields());
        }
    }

    @PostRemove
    void removed(Object entity) {
        switch (entity) {
            case LockerUnit locker -> record(lockerEntry("LOCKER_DELETED", locker));
            case LockerBox box -> record(boxEntry("BOX_DELETED", box));
            case DroneUnit drone -> record(droneEntry("DRONE_DELETED", drone));
            default -> {
            }
        }
    }

    @PostUpdate
    void updated(Object entity) {
        if (!(entity instanceof AuditTracked tracked)) {
            return;
        }
        Map<String, String> before = tracked.getAuditBaseline();
        Map<String, String> after = tracked.auditFields();
        tracked.setAuditBaseline(after);
        if (before == null) {
            return;
        }
        List<ActionLog.Entry> entries = switch (entity) {
            case LockerUnit locker -> lockerChanges(locker, before, after);
            case LockerBox box -> boxChanges(box, before, after);
            case DroneUnit drone -> droneChanges(drone, before, after);
            default -> List.of();
        };
        entries.forEach(this::record);
    }

    // ------------------------------------------------------------------ tủ

    static List<ActionLog.Entry> lockerChanges(LockerUnit locker, Map<String, String> before, Map<String, String> after) {
        List<ActionLog.Entry> entries = new ArrayList<>();
        if (changed(before, after, "deletedAt") && after.get("deletedAt") != null) {
            entries.add(lockerEntry("LOCKER_DELETED", locker).note(locker.getDeletedReason()));
            return entries;
        }
        if (changed(before, after, "status")) {
            String to = after.get("status");
            String action = switch (to == null ? "" : to) {
                case "MAINTENANCE" -> "TICKET".equals(after.get("maintenanceSource"))
                        ? "LOCKER_MAINTENANCE_BY_TICKET" : "LOCKER_MAINTENANCE_ON";
                case "ACTIVE" -> "LOCKER_ACTIVATED";
                case "INACTIVE" -> "LOCKER_DEACTIVATED";
                default -> "LOCKER_STATUS_CHANGED";
            };
            entries.add(lockerEntry(action, locker).change(before.get("status"), to));
        }
        if (changed(before, after, "assignedTechnicianId")) {
            entries.add(lockerEntry(technicianAction("LOCKER", before.get("assignedTechnicianId"),
                    after.get("assignedTechnicianId")), locker)
                    .change(before.get("assignedTechnicianId"), after.get("assignedTechnicianId")));
        }
        if (changed(before, after, "landingPadStatus")) {
            entries.add(lockerEntry("LANDING_PAD_STATUS_CHANGED", locker)
                    .change(before.get("landingPadStatus"), after.get("landingPadStatus")));
        }
        if (changed(before, after, "landingPad")) {
            entries.add(lockerEntry(
                    "true".equals(after.get("landingPad")) ? "LANDING_PAD_ENABLED" : "LANDING_PAD_DISABLED", locker));
        }
        List<String> infoChanged = LOCKER_INFO_FIELDS.stream().filter(key -> changed(before, after, key)).sorted().toList();
        if (!infoChanged.isEmpty()) {
            entries.add(lockerEntry("LOCKER_INFO_UPDATED", locker)
                    .change(describe(before, infoChanged), describe(after, infoChanged)));
        }
        return entries;
    }

    private static ActionLog.Entry lockerEntry(String action, LockerUnit locker) {
        return ActionLog.Entry.of(action, "LOCKER", locker.getId(), label(locker.getCode(), locker.getName()))
                .locker(locker.getId());
    }

    // ------------------------------------------------------------------ ô

    static List<ActionLog.Entry> boxChanges(LockerBox box, Map<String, String> before, Map<String, String> after) {
        List<ActionLog.Entry> entries = new ArrayList<>();
        if (changed(before, after, "deletedAt") && after.get("deletedAt") != null) {
            entries.add(boxEntry("BOX_DELETED", box).note(box.getDeletedReason()));
            return entries;
        }
        if (changed(before, after, "status")) {
            String from = before.get("status");
            String to = after.get("status");
            boolean orderFlow = ORDER_BOX_STATUSES.contains(from) && ORDER_BOX_STATUSES.contains(to);
            if (!orderFlow || staffActor()) {
                String action;
                if ("FAULT".equals(to)) {
                    action = "BOX_FAULT";
                } else if ("CLEANING".equals(to)) {
                    action = "BOX_CLEANING";
                } else if (!ORDER_BOX_STATUSES.contains(to)) {
                    action = "BOX_OUT_OF_SERVICE";
                } else if (!ORDER_BOX_STATUSES.contains(from)) {
                    action = "BOX_BACK_IN_SERVICE";
                } else {
                    action = "BOX_STATUS_CHANGED";
                }
                entries.add(boxEntry(action, box).change(from, to)
                        .note("FAULT".equals(to) ? box.getFaultReason() : null));
            }
        }
        if (changed(before, after, "active")) {
            entries.add(boxEntry("true".equals(after.get("active")) ? "BOX_ENABLED" : "BOX_DISABLED", box));
        }
        List<String> infoChanged = List.of("size", "cellType").stream().filter(key -> changed(before, after, key)).toList();
        if (!infoChanged.isEmpty()) {
            entries.add(boxEntry("BOX_UPDATED", box).change(describe(before, infoChanged), describe(after, infoChanged)));
        }
        return entries;
    }

    private static ActionLog.Entry boxEntry(String action, LockerBox box) {
        return ActionLog.Entry.of(action, "BOX", box.getId(), box.getBoxNumber() == null ? null : "#" + box.getBoxNumber())
                .locker(box.getLockerId());
    }

    // ------------------------------------------------------------------ drone

    static List<ActionLog.Entry> droneChanges(DroneUnit drone, Map<String, String> before, Map<String, String> after) {
        List<ActionLog.Entry> entries = new ArrayList<>();
        if (changed(before, after, "deletedAt") && after.get("deletedAt") != null) {
            entries.add(droneEntry("DRONE_DELETED", drone).note(drone.getDeletedReason()));
            return entries;
        }
        if (changed(before, after, "status")) {
            String from = before.get("status");
            String to = after.get("status");
            boolean flightFlow = FLIGHT_DRONE_STATUSES.contains(from) && FLIGHT_DRONE_STATUSES.contains(to);
            if (!flightFlow || staffActor()) {
                entries.add(droneEntry("DRONE_STATUS_CHANGED", drone).change(from, to)
                        .note(DroneStatus.FAULT.equals(to) ? drone.getFaultReason() : null));
            }
        }
        if (changed(before, after, "assignedTechnicianId")) {
            entries.add(droneEntry(technicianAction("DRONE", before.get("assignedTechnicianId"),
                    after.get("assignedTechnicianId")), drone)
                    .change(before.get("assignedTechnicianId"), after.get("assignedTechnicianId")));
        }
        if (changed(before, after, "lockerId")) {
            entries.add(droneEntry("DRONE_MOVED", drone).change(before.get("lockerId"), after.get("lockerId")));
        }
        if (changed(before, after, "active")) {
            entries.add(droneEntry("true".equals(after.get("active")) ? "DRONE_ENABLED" : "DRONE_DISABLED", drone));
        }
        return entries;
    }

    private static ActionLog.Entry droneEntry(String action, DroneUnit drone) {
        return ActionLog.Entry.of(action, "DRONE", drone.getId(), drone.getCode()).locker(drone.getLockerId());
    }

    // ------------------------------------------------------------------ chung

    /// `LOCKER_TECHNICIAN_ASSIGNED` / `_UNASSIGNED` / `_CHANGED` (tương tự cho DRONE).
    private static String technicianAction(String prefix, String from, String to) {
        if (from == null) {
            return prefix + "_TECHNICIAN_ASSIGNED";
        }
        return to == null ? prefix + "_TECHNICIAN_UNASSIGNED" : prefix + "_TECHNICIAN_CHANGED";
    }

    /// Admin hoặc KTV đang thao tác (không phải job / thiết bị / khách đặt đơn).
    static boolean staffActor() {
        RequestActor actor = RequestActor.current();
        if (actor == null || actor.roles() == null) {
            return false;
        }
        for (String role : actor.roles().split(",")) {
            String r = role.trim().toUpperCase();
            if (r.startsWith("ROLE_")) {
                r = r.substring(5);
            }
            if (r.equals("ADMIN") || r.endsWith("_TECHNICIAN") || r.equals("TECHNICIAN")) {
                return true;
            }
        }
        return false;
    }

    private static boolean changed(Map<String, String> before, Map<String, String> after, String key) {
        return !Objects.equals(before.get(key), after.get(key));
    }

    private static String describe(Map<String, String> values, List<String> keys) {
        StringBuilder text = new StringBuilder();
        for (String key : keys) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(key).append('=').append(values.get(key) == null ? "—" : values.get(key));
        }
        return text.toString();
    }

    private static String label(String code, String name) {
        if (code == null) {
            return name;
        }
        return name == null || name.equals(code) ? code : code + " · " + name;
    }

    private void record(ActionLog.Entry entry) {
        ActionLog log = actionLog.getIfAvailable();
        if (log != null) {
            log.record(entry);
        }
    }
}
