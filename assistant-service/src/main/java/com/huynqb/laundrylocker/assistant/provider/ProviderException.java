package com.huynqb.laundrylocker.assistant.provider;

/// Nhà cung cấp bên ngoài (embedding, Claude) lỗi hoặc không phản hồi.
public class ProviderException extends RuntimeException {

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderException(String message) {
        super(message);
    }
}
