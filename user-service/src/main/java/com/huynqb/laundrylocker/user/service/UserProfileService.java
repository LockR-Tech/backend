package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final CloudinaryMediaStorage mediaStorage;

    @Transactional
    public UserSummary create(UserProfileRequest request) {
        UserProfile user = new UserProfile();
        apply(user, request);
        return toSummary(userProfileRepository.save(user));
    }

    @Transactional
    public UserSummary update(Long id, UserProfileRequest request) {
        UserProfile user = find(id);
        apply(user, request);
        return toSummary(userProfileRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserSummary get(Long id) {
        return toSummary(find(id));
    }

    @Transactional(readOnly = true)
    public UserSummary getByPhone(String phoneNumber) {
        return userProfileRepository
                .findFirstByPhoneNumber(phoneNumber)
                .map(this::toSummary)
                .orElseThrow(
                        () ->
                                new com.huynqb.laundrylocker.common.exception.BusinessException(
                                        "USER_NOT_FOUND", "No user with phone " + phoneNumber));
    }

    /// Tra cứu theo lô cho các màn hình báo cáo admin (order/payment-service ghép
    /// tên khách). Id không tồn tại bị bỏ qua thay vì lỗi cả lô.
    @Transactional(readOnly = true)
    public List<UserSummary> getMany(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return userProfileRepository.findAllById(distinct).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> list() {
        return userProfileRepository.findAll().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listByRole(String role) {
        String expected = role == null ? "" : role.trim().toUpperCase();
        return userProfileRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .filter(user -> parseRoles(user.getRoles()).contains(expected))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.huynqb.laundrylocker.user.dto.AdminUserView> listAdminViews(
            String search, String status, String role) {
        String q = search == null ? null : search.toLowerCase();
        return userProfileRepository.findAll().stream()
                .filter(u -> {
                    if (status != null && !status.equalsIgnoreCase(u.getStatus())) return false;
                    if (role != null && !u.getRoles().toLowerCase().contains(role.toLowerCase())) return false;
                    if (q != null) {
                        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                                + (u.getLastName() == null ? "" : u.getLastName())).toLowerCase();
                        String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                        if (!name.contains(q) && !email.contains(q)) return false;
                    }
                    return true;
                })
                .map(
                        u ->
                                new com.huynqb.laundrylocker.user.dto.AdminUserView(
                                        u.getId(),
                                        u.getEmail(),
                                        u.getPhoneNumber(),
                                        ((u.getFirstName() == null ? "" : u.getFirstName())
                                                + " "
                                                + (u.getLastName() == null ? "" : u.getLastName()))
                                                .trim(),
                                        u.getStatus(),
                                        parseRoles(u.getRoles()),
                                        u.getCreatedAt(),
                                        null,
                                        null,
                                        u.getImageUrl()))
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        userProfileRepository.delete(find(id));
    }

    @Transactional
    public UserSummary updateStatus(Long id, String status) {
        UserProfile user = find(id);
        user.setStatus(status);
        return toSummary(userProfileRepository.save(user));
    }

    @Transactional
    public UserSummary updateRoles(Long id, Set<String> roles) {
        UserProfile user = find(id);
        user.setRoles(
                roles == null || roles.isEmpty()
                        ? "USER"
                        : roles.stream().map(String::toUpperCase).collect(Collectors.joining(",")));
        return toSummary(userProfileRepository.save(user));
    }

    /// API cũ nhận URL tuỳ ý — giữ để tương thích; client mới gửi MediaUpload.
    @Transactional
    public UserSummary updateAvatar(Long id, String imageUrl) {
        UserProfile user = find(id);
        String previous = user.getImageUrl();
        user.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl : null);
        UserSummary saved = toSummary(userProfileRepository.save(user));
        mediaStorage.deleteReplacedAfterCommit(previous, user.getImageUrl());
        return saved;
    }

    /// Ảnh đại diện đã upload lên Cloudinary (ADR-0004). `actorUserId` là người upload —
    /// chính người dùng, hoặc ADMIN khi đổi hộ.
    @Transactional
    public UserSummary updateAvatar(Long id, MediaUpload upload, Long actorUserId) {
        VerifiedMedia media = mediaStorage.verify(upload, MediaPurpose.AVATAR, actorUserId);
        return updateAvatar(id, media.secureUrl());
    }

    @Transactional
    public UserSummary removeAvatar(Long id) {
        return updateAvatar(id, (String) null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics(Long userId) {
        return Map.of("userId", userId, "profileComplete", true);
    }

    private UserProfile find(Long id) {
        return userProfileRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private void apply(UserProfile user, UserProfileRequest request) {
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.birthday() != null) {
            user.setBirthday(request.birthday());
        }
        // Form hồ sơ không gửi ảnh ⇒ giữ avatar hiện tại; đổi/xoá avatar qua /avatar.
        if (StringUtils.hasText(request.imageUrl())) {
            user.setImageUrl(request.imageUrl());
        }
        user.setStatus(StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRoles(request.roles().stream().map(String::toUpperCase).collect(Collectors.joining(",")));
        } else if (!StringUtils.hasText(user.getRoles())) {
            user.setRoles("USER");
        }
    }

    private UserSummary toSummary(UserProfile user) {
        String fullName =
                ((user.getFirstName() == null ? "" : user.getFirstName())
                        + " "
                        + (user.getLastName() == null ? "" : user.getLastName()))
                        .trim();
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                fullName,
                user.getStatus(),
                parseRoles(user.getRoles()),
                user.getImageUrl());
    }

    private Set<String> parseRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return Set.of("USER");
        }
        return List.of(roles.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }
}
