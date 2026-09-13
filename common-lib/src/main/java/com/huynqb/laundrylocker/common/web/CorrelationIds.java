package com.huynqb.laundrylocker.common.web;

import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".id";

    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private CorrelationIds() {
    }

    public static String resolve(String candidate) {
        if (StringUtils.hasText(candidate)) {
            String value = candidate.trim();
            if (value.length() <= MAX_LENGTH && SAFE_VALUE.matcher(value).matches()) {
                return value;
            }
        }
        return UUID.randomUUID().toString();
    }
}
