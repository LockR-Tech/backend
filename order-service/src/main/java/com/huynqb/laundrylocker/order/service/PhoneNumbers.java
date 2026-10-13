package com.huynqb.laundrylocker.order.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// So khớp số điện thoại Việt Nam nhập theo nhiều kiểu: `0901234567`, `+84901234567`,
/// `84901234567`, `090 123 4567`. Đơn lưu số đúng như người gửi gõ, nên khi tra "đơn gửi tới số
/// của tôi" phải sinh đủ các cách viết của cùng một số.
public final class PhoneNumbers {

    /// Ký tự trình bày bị bỏ trước khi so sánh — truy vấn JPA bỏ đúng các ký tự này ở phía DB.
    public static final List<String> SEPARATORS = List.of(" ", "-", ".", "(", ")");

    private PhoneNumbers() {
    }

    /// Dạng nội địa `0xxxxxxxxx` cho số Việt Nam; số khác chỉ bỏ ký tự trình bày.
    /// null khi không có chữ số nào.
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.trim();
        for (String separator : SEPARATORS) {
            compact = compact.replace(separator, "");
        }
        boolean plus = compact.startsWith("+");
        String digits = compact.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("84") && digits.length() >= 11 && (plus || digits.length() == 11)) {
            String rest = digits.substring(2);
            return rest.startsWith("0") ? rest : "0" + rest;
        }
        return plus ? "+" + digits : digits;
    }

    /// Các cách viết cùng một số (đã bỏ ký tự trình bày) để so với giá trị lưu trong DB.
    public static Set<String> variants(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        result.add(normalized);
        if (normalized.startsWith("0") && normalized.length() >= 10) {
            String subscriber = normalized.substring(1);
            result.add("+84" + subscriber);
            result.add("84" + subscriber);
            result.add("+840" + subscriber);
            result.add("840" + subscriber);
        }
        return result;
    }

    public static boolean sameNumber(String a, String b) {
        String left = normalize(a);
        return left != null && left.equals(normalize(b));
    }

    /// Số lưu trong DB sau khi bỏ đúng các ký tự trình bày như chuỗi `replace(...)` của truy vấn
    /// JPA — để so trong Java đúng y như database so với [variants]. null giữ nguyên null.
    public static String stripSeparators(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw;
        for (String separator : SEPARATORS) {
            compact = compact.replace(separator, "");
        }
        return compact;
    }
}
