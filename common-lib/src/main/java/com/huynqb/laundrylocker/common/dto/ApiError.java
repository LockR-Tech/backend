package com.huynqb.laundrylocker.common.dto;

import java.util.List;

public record ApiError(String code, String message, List<String> fields) {
}
