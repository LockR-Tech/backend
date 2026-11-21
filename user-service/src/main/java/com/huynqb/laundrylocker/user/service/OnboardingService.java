package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.dto.OnboardingStateView;
import com.huynqb.laundrylocker.user.model.UserOnboardingState;
import com.huynqb.laundrylocker.user.repository.UserOnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/// Trạng thái hướng dẫn lần đầu trên màn hình (F3-G13). Mọi thao tác chỉ trên chính người đang đăng nhập:
/// `userId` luôn lấy từ header `X-User-Id` do gateway gắn theo JWT, không nhận id từ client.
@Service
@RequiredArgsConstructor
public class OnboardingService {

    /// Mã nhóm: chữ thường, số, gạch ngang; bắt đầu bằng chữ hoặc số; tối đa 64 ký tự.
    static final Pattern GROUP_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    static final int MAX_VERSION = 1_000_000;
    /// Chặn ghi rác vô hạn vào JSONB của một người.
    static final int MAX_GROUPS = 300;

    private final UserOnboardingStateRepository repository;

    @Transactional(readOnly = true)
    public OnboardingStateView get(Long userId) {
        requireUser(userId);
        return repository.findById(userId)
                .map(OnboardingService::view)
                .orElseGet(() -> new OnboardingStateView(true, Map.of(), null));
    }

    /// false = "Bỏ qua vĩnh viễn" (không tự mở trên mọi thiết bị); true = cho tự mở lại.
    @Transactional
    public OnboardingStateView setAutoShow(Long userId, Boolean autoShow) {
        requireUser(userId);
        if (autoShow == null) {
            throw new BusinessException("ONBOARDING_AUTO_SHOW_REQUIRED", "Thiếu giá trị autoShow", HttpStatus.BAD_REQUEST);
        }
        UserOnboardingState state = load(userId);
        state.setAutoShow(autoShow);
        state.setUpdatedAt(LocalDateTime.now());
        return view(repository.save(state));
    }

    /// Ghi "đã xem nhóm `group` ở phiên bản `version`". Giữ phiên bản lớn nhất: máy cũ gửi phiên bản thấp hơn
    /// không làm người dùng phải xem lại.
    @Transactional
    public OnboardingStateView markSeen(Long userId, String group, Integer version) {
        requireUser(userId);
        String id = group == null ? "" : group.trim();
        if (!GROUP_ID.matcher(id).matches()) {
            throw new BusinessException("ONBOARDING_GROUP_INVALID",
                    "Mã nhóm hướng dẫn không hợp lệ (chỉ chữ thường, số, gạch ngang)", HttpStatus.BAD_REQUEST);
        }
        if (version == null || version < 1 || version > MAX_VERSION) {
            throw new BusinessException("ONBOARDING_VERSION_INVALID", "Phiên bản hướng dẫn không hợp lệ",
                    HttpStatus.BAD_REQUEST);
        }
        UserOnboardingState state = load(userId);
        Map<String, Integer> seen = new LinkedHashMap<>(state.getSeenGroups() == null ? Map.of() : state.getSeenGroups());
        if (!seen.containsKey(id) && seen.size() >= MAX_GROUPS) {
            throw new BusinessException("ONBOARDING_TOO_MANY_GROUPS", "Đã ghi quá nhiều nhóm hướng dẫn",
                    HttpStatus.BAD_REQUEST);
        }
        if (version > seen.getOrDefault(id, 0)) {
            seen.put(id, version);
        }
        state.setSeenGroups(seen);
        state.setUpdatedAt(LocalDateTime.now());
        return view(repository.save(state));
    }

    private UserOnboardingState load(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            UserOnboardingState created = new UserOnboardingState();
            created.setUserId(userId);
            return created;
        });
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("AUTH_REQUIRED", "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED);
        }
    }

    private static OnboardingStateView view(UserOnboardingState state) {
        Map<String, Integer> seen = state.getSeenGroups() == null ? Map.of() : Map.copyOf(state.getSeenGroups());
        return new OnboardingStateView(state.isAutoShow(), seen, state.getUpdatedAt());
    }
}
