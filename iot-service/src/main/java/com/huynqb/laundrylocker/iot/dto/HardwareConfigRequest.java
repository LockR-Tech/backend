package com.huynqb.laundrylocker.iot.dto;

import com.fasterxml.jackson.databind.JsonNode;

/// Lưu cấu hình phần cứng: `config` = bản đồ chân (mqtt-contract § 2.1 `hw-config`),
/// `wiring` = danh sách dòng đấu dây {group, from, to, via, wire, note}.
public record HardwareConfigRequest(JsonNode config, JsonNode wiring) {
}
