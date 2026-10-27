package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Luật hợp lệ của bản đồ chân tủ — **giống hệt** `iot/config/hardware_config.py::validate` để server
/// báo lỗi ngay lúc lưu thay vì đợi Pi từ chối. Đổi một bên thì đổi cả hai.
final class HardwareConfigValidator {

    static final int MIN_GPIO = 2;
    static final int MAX_GPIO = 27;
    static final int MIN_UNLOCK_MS = 100;
    static final int MAX_UNLOCK_MS = 10_000;
    static final int MAX_BOXES = 16;
    static final List<String> LID_PIN_KEYS = List.of("pulPin", "dirPin", "homePin", "endPin");
    private static final List<String> FLAGS = List.of(
            "relayActiveHigh", "doorClosedLow", "requireDoorSensor", "lidBypassPhysicalMotion");

    private HardwareConfigValidator() {
    }

    static List<String> validate(JsonNode cfg) {
        List<String> errors = new ArrayList<>();
        if (cfg == null || !cfg.isObject()) {
            errors.add("Cấu hình phải là một đối tượng JSON");
            return errors;
        }
        List<Integer> relays = pinList(cfg, "relayPins", "Chân relay", errors);
        List<Integer> doors = pinList(cfg, "doorPins", "Chân cảm biến cửa", errors);
        if (!relays.isEmpty() && !doors.isEmpty() && relays.size() != doors.size()) {
            errors.add("Số chân relay (" + relays.size() + ") phải bằng số chân cảm biến cửa ("
                    + doors.size() + ") — mỗi ô một cặp");
        }
        for (String flag : FLAGS) {
            if (!cfg.path(flag).isBoolean()) {
                errors.add(flag + " phải là true hoặc false");
            }
        }
        JsonNode unlock = cfg.path("unlockMs");
        if (!unlock.isIntegralNumber() || unlock.asInt() < MIN_UNLOCK_MS || unlock.asInt() > MAX_UNLOCK_MS) {
            errors.add("Thời gian kích relay phải từ " + MIN_UNLOCK_MS + " tới " + MAX_UNLOCK_MS + " ms");
        }

        // (chân, vai trò) theo thứ tự để báo trùng dễ hiểu
        List<Map.Entry<Integer, String>> used = new ArrayList<>();
        for (int i = 0; i < relays.size(); i++) {
            used.add(Map.entry(relays.get(i), "relay ô " + (i + 1)));
        }
        for (int i = 0; i < doors.size(); i++) {
            used.add(Map.entry(doors.get(i), "cảm biến cửa ô " + (i + 1)));
        }

        JsonNode lids = cfg.path("lids");
        if (!lids.isArray() || lids.size() > 2) {
            errors.add("lids phải là danh sách tối đa 2 trục");
        } else {
            Set<Integer> axes = new HashSet<>();
            Map<Integer, Integer> boxOwner = new HashMap<>();
            for (JsonNode lid : lids) {
                int axis = lid.path("axis").asInt(0);
                if (!lid.isObject() || (axis != 1 && axis != 2) || !axes.add(axis)) {
                    errors.add("Mỗi trục nắp phải có axis là 1 hoặc 2, không trùng");
                    continue;
                }
                for (String key : List.of("enabled", "openDirHigh", "limitActiveLow")) {
                    if (!lid.path(key).isBoolean()) {
                        errors.add("Trục " + axis + ": " + key + " phải là true hoặc false");
                    }
                }
                boolean enabled = lid.path("enabled").asBoolean(false) && lid.path("enabled").isBoolean();
                for (String key : LID_PIN_KEYS) {
                    JsonNode pin = lid.path(key);
                    if (!pin.isIntegralNumber()) {
                        errors.add("Trục " + axis + ": thiếu chân " + key);
                    } else if (enabled) {
                        used.add(Map.entry(pin.asInt(), "trục " + axis + " " + key));
                    }
                }
                JsonNode boxes = lid.path("boxes");
                if (!boxes.isArray()) {
                    errors.add("Trục " + axis + ": boxes phải là danh sách số ô");
                    continue;
                }
                for (JsonNode boxNode : boxes) {
                    if (!boxNode.isIntegralNumber()) {
                        errors.add("Trục " + axis + ": boxes phải là danh sách số ô");
                        continue;
                    }
                    int box = boxNode.asInt();
                    if (!relays.isEmpty() && (box < 1 || box > relays.size())) {
                        errors.add("Trục " + axis + ": ô " + box + " không có trong tủ (1–" + relays.size() + ")");
                    } else if (boxOwner.containsKey(box)) {
                        errors.add("Ô " + box + " không thể nằm dưới cả trục " + boxOwner.get(box) + " và trục " + axis);
                    } else {
                        boxOwner.put(box, axis);
                    }
                }
            }
        }

        Map<Integer, String> owner = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : used) {
            int pin = e.getKey();
            if (pin < MIN_GPIO || pin > MAX_GPIO) {
                errors.add(e.getValue() + ": GPIO" + pin + " không dùng được (chỉ GPIO2–GPIO27)");
            } else if (owner.containsKey(pin)) {
                errors.add("GPIO" + pin + " bị dùng hai lần: " + owner.get(pin) + " và " + e.getValue());
            } else {
                owner.put(pin, e.getValue());
            }
        }
        return errors;
    }

    private static List<Integer> pinList(JsonNode cfg, String field, String label, List<String> errors) {
        JsonNode node = cfg.path(field);
        List<Integer> pins = new ArrayList<>();
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_BOXES) {
            errors.add(label + ": cần 1–" + MAX_BOXES + " số GPIO");
            return List.of();
        }
        for (JsonNode pin : node) {
            if (!pin.isIntegralNumber()) {
                errors.add(label + ": cần 1–" + MAX_BOXES + " số GPIO");
                return List.of();
            }
            pins.add(pin.asInt());
        }
        return pins;
    }
}
