package com.huynqb.laundrylocker.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String resource, Long id) {
        super("NOT_FOUND", resource + " not found: " + id, HttpStatus.NOT_FOUND);
    }
}
