package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

public record BatchDeleteBoxesRequest(
        List<Long> boxIds,
        List<Integer> boxNumbers,
        Boolean deleteAll) {
    public BatchDeleteBoxesRequest(List<Long> boxIds, List<Integer> boxNumbers) {
        this(boxIds, boxNumbers, false);
    }
}
