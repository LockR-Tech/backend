package com.huynqb.laundrylocker.common.util;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Predicate;

public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private CodeGenerator() {
    }

    public static String generateAlphanumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC_CHARS.charAt(RANDOM.nextInt(ALPHANUMERIC_CHARS.length())));
        }
        return sb.toString();
    }

    public static String generateAlphanumericCode() {
        return generateAlphanumericCode(DEFAULT_CODE_LENGTH);
    }

    public static String generateUniqueCode(int length, Predicate<String> isDuplicate) {
        int attempts = 0;
        String code;
        do {
            code = generateAlphanumericCode(length);
            attempts++;
            if (attempts > MAX_GENERATION_ATTEMPTS) {
                code = UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
                break;
            }
        } while (isDuplicate.test(code));
        return code;
    }

    public static String generateUniqueCode(Predicate<String> isDuplicate) {
        return generateUniqueCode(DEFAULT_CODE_LENGTH, isDuplicate);
    }

    public static String generatePinCode(int digits) {
        if (digits < 1 || digits > 10) {
            throw new IllegalArgumentException("Digits must be between 1 and 10");
        }
        int min = (int) Math.pow(10, digits - 1);
        int max = (int) Math.pow(10, digits) - 1;
        return String.valueOf(min + RANDOM.nextInt(max - min + 1));
    }

    public static String generatePinCode() {
        return generatePinCode(6);
    }

    public static String generateToken() {
        return UUID.randomUUID().toString();
    }

    public static String generateShortToken() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
