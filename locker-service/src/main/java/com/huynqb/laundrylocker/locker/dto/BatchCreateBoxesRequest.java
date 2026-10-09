package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

public record BatchCreateBoxesRequest(
        List<BoxItemRequest> boxes,
        Integer startBoxNumber,
        Integer count,
        String size,
        String status,
        String cellType,
        Integer startRowIndex,
        Integer startColIndex) {
}
