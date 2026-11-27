package com.huynqb.laundrylocker.user.model;

import com.huynqb.laundrylocker.common.audit.ActionLog;
import com.huynqb.laundrylocker.common.audit.RequestActor;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/// Nhật ký thao tác trên tài khoản (F3-G16): admin tạo tài khoản, khoá / mở khoá, đổi vai trò, xoá. Chỉ ghi khi
/// người thao tác là ADMIN — khách tự đăng ký hay tự sửa hồ sơ không vào nhật ký.
@Component
public class UserActionAuditListener {

    private final ObjectProvider<ActionLog> actionLog;

    public UserActionAuditListener(ObjectProvider<ActionLog> actionLog) {
        this.actionLog = actionLog;
    }

    @PostPersist
    void created(UserProfile user) {
        if (adminActor()) {
            record(entry("USER_CREATED", user).change(null, user.getRoles()));
        }
        user.rememberAudit();
    }

    @PostUpdate
    void updated(UserProfile user) {
        String beforeStatus = user.getAuditStatus();
        String beforeRoles = user.getAuditRoles();
        boolean loaded = user.isAuditLoaded();
        user.rememberAudit();
        if (!loaded || !adminActor()) {
            return;
        }
        if (user.getDeletedAt() != null) {
            record(entry("USER_DELETED", user).note(user.getDeletedReason()));
            return;
        }
        if (!Objects.equals(beforeStatus, user.getStatus())) {
            String action = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "USER_ACTIVATED" : "USER_LOCKED";
            record(entry(action, user).change(beforeStatus, user.getStatus()));
        }
        if (!Objects.equals(beforeRoles, user.getRoles())) {
            record(entry("USER_ROLES_CHANGED", user).change(beforeRoles, user.getRoles()));
        }
    }

    private static ActionLog.Entry entry(String action, UserProfile user) {
        String name = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
        String contact = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();
        String label = name.isEmpty() ? contact : contact == null ? name : name + " · " + contact;
        return ActionLog.Entry.of(action, "USER", user.getId(), label);
    }

    static boolean adminActor() {
        RequestActor actor = RequestActor.current();
        if (actor == null || actor.roles() == null) {
            return false;
        }
        for (String role : actor.roles().split(",")) {
            String r = role.trim().toUpperCase();
            if (r.equals("ADMIN") || r.equals("ROLE_ADMIN")) {
                return true;
            }
        }
        return false;
    }

    private void record(ActionLog.Entry entry) {
        ActionLog log = actionLog.getIfAvailable();
        if (log != null) {
            log.record(entry);
        }
    }
}
