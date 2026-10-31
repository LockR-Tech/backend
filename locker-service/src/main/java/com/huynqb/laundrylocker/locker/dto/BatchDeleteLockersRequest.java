package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

public record BatchDeleteLockersRequest(
        List<Long> lockerIds,
        Boolean deleteAll,
        String reason) {
}
