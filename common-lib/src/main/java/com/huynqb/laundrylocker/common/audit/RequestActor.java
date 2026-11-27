package com.huynqb.laundrylocker.common.audit;

/// Người đang gọi API (gateway gắn `X-User-Id`, `X-User-Roles` sau khi kiểm JWT) — {@link RequestActorFilter}
/// ghi cho từng request để nhật ký thao tác biết ai bấm mà không phải truyền qua mọi hàm.
/// Ngoài request (job hẹn giờ, sự kiện MQTT, Pi gọi nội bộ) thì `current()` rỗng ⇒ nhật ký ghi là hệ thống.
public record RequestActor(Long userId, String roles) {

    private static final ThreadLocal<RequestActor> CURRENT = new ThreadLocal<>();

    public static RequestActor current() {
        return CURRENT.get();
    }

    static void set(RequestActor actor) {
        CURRENT.set(actor);
    }

    static void clear() {
        CURRENT.remove();
    }

    /// Chạy `action` với người thao tác cho trước (vd luồng nội bộ biết rõ ai bấm), trả lại người cũ sau đó.
    public static void runAs(RequestActor actor, Runnable action) {
        RequestActor previous = CURRENT.get();
        CURRENT.set(actor);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
