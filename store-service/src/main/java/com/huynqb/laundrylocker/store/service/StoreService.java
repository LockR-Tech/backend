package com.huynqb.laundrylocker.store.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.store.client.LockerClient;
import com.huynqb.laundrylocker.store.client.OrderClient;
import com.huynqb.laundrylocker.store.dto.StoreRequest;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.model.StoreLocation;
import com.huynqb.laundrylocker.store.repository.StoreRepository;
import com.huynqb.laundrylocker.store.settings.StoreRules;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository repository;
    private final OrderClient orderClient;
    private final CloudinaryMediaStorage mediaStorage;
    /// Bán kính tìm cửa hàng mặc định do admin cấu hình (ADR-0005).
    private final StoreRules rules;
    /// Kiểm tra tủ còn thuộc cửa hàng trước khi xoá.
    private final LockerClient lockerClient;

    @Transactional
    public StoreResponse create(StoreRequest request) {
        StoreLocation store = new StoreLocation();
        apply(store, request);
        return toResponse(repository.save(store));
    }

    @Transactional(readOnly = true)
    public StoreResponse get(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id)));
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    /// Tra cứu theo lô cho báo cáo admin; `ids` rỗng/null ⇒ toàn bộ cửa hàng.
    @Transactional(readOnly = true)
    public List<StoreResponse> getMany(java.util.Collection<Long> ids) {
        List<Long> distinct =
                ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return (distinct.isEmpty() ? repository.findAll() : repository.findAllById(distinct)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> adminList(String search, String status) {
        String q = search == null ? null : search.toLowerCase();
        return repository.findAll().stream()
                .filter(s -> {
                    if (status != null && !status.equalsIgnoreCase(s.getStatus())) return false;
                    if (q != null) {
                        String name = s.getName() == null ? "" : s.getName().toLowerCase();
                        String addr = s.getAddress() == null ? "" : s.getAddress().toLowerCase();
                        if (!name.contains(q) && !addr.contains(q)) return false;
                    }
                    return true;
                })
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> nearby(Double latitude, Double longitude, Double radiusKm) {
        double radius = radiusKm == null ? rules.nearbyDefaultRadiusKm() : radiusKm;
        return repository.findByStatusAndActiveTrue("ACTIVE").stream()
                .map(store -> toResponse(store, distanceKm(latitude, longitude, store.getLatitude(), store.getLongitude())))
                .filter(store -> store.distanceKm() == null || store.distanceKm() <= radius)
                .toList();
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        StoreLocation store = repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id));
        apply(store, request);
        return toResponse(repository.save(store));
    }

    @Transactional
    public StoreResponse updateStatus(Long id, String status) {
        StoreLocation store = repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id));
        store.setStatus(status);
        store.setActive("ACTIVE".equalsIgnoreCase(status));
        return toResponse(repository.save(store));
    }

    /// Không xoá cửa hàng còn tủ (tủ sẽ trỏ tới cửa hàng không tồn tại). Chưa tra được
    /// locker-service thì từ chối thay vì xoá khi chưa chắc chắn.
    @Transactional
    public void delete(Long id) {
        StoreLocation store = repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id));
        List<LockerClient.LockerRef> lockers = lockersOf(id);
        if (!lockers.isEmpty()) {
            String codes = lockers.stream()
                    .limit(5)
                    .map(locker -> StringUtils.hasText(locker.code()) ? locker.code() : "#" + locker.id())
                    .collect(Collectors.joining(", "));
            throw new BusinessException(
                    "STORE_HAS_LOCKERS",
                    "Cửa hàng còn " + lockers.size() + " tủ (" + codes + (lockers.size() > 5 ? ", …" : "")
                            + "). Chuyển tủ sang cửa hàng khác hoặc xoá tủ trước khi xoá cửa hàng.",
                    HttpStatus.CONFLICT);
        }
        repository.delete(store);
    }

    private List<LockerClient.LockerRef> lockersOf(Long storeId) {
        ApiResponse<List<LockerClient.LockerRef>> response;
        try {
            response = lockerClient.lockersByStore(storeId);
        } catch (RuntimeException ex) {
            response = null;
        }
        if (response == null || !response.success()) {
            throw new BusinessException(
                    "LOCKER_SERVICE_UNAVAILABLE",
                    "Chưa kiểm tra được các tủ thuộc cửa hàng, vui lòng thử lại sau.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        List<LockerClient.LockerRef> lockers = response.data() == null ? List.of() : response.data();
        // Phòng khi bên kia bỏ qua tham số storeId: chỉ tính tủ thực sự thuộc cửa hàng này.
        return lockers.stream()
                .filter(locker -> locker.storeId() == null || storeId.equals(locker.storeId()))
                .toList();
    }

    /// API cũ nhận URL tuỳ ý — giữ để tương thích; client mới dùng `updateImage(id, MediaUpload, userId)`.
    @Transactional
    public StoreResponse updateImage(Long id, String imageUrl) {
        StoreLocation store = repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id));
        String previous = store.getImage();
        store.setImage(imageUrl);
        StoreLocation saved = repository.save(store);
        mediaStorage.deleteReplacedAfterCommit(previous, imageUrl);
        return toResponse(saved);
    }

    /// Ảnh cửa hàng đã upload lên Cloudinary (ADR-0004); ảnh cũ của hệ thống bị dọn sau khi lưu.
    @Transactional
    public StoreResponse updateImage(Long id, MediaUpload upload, Long actorUserId) {
        VerifiedMedia media = mediaStorage.verify(upload, MediaPurpose.STORE_IMAGE, actorUserId);
        return updateImage(id, media.secureUrl());
    }

    @Transactional
    public StoreResponse removeImage(Long id) {
        return updateImage(id, (String) null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ratings(Long storeId) {
        try {
            return orderClient.storeRatings(storeId).data();
        } catch (Exception ex) {
            return List.of();
        }
    }

    /// Chỉ ghi đè field có gửi: field null giữ giá trị hiện tại (tạo mới ⇒ mặc định của entity:
    /// active = true, status = ACTIVE); chuỗi rỗng xoá field chữ.
    private void apply(StoreLocation store, StoreRequest request) {
        store.setName(request.name());
        if (request.contactPhone() != null) {
            store.setContactPhone(textOrNull(request.contactPhone()));
        }
        if (request.address() != null) {
            store.setAddress(textOrNull(request.address()));
        }
        if (request.latitude() != null) {
            store.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            store.setLongitude(request.longitude());
        }
        // Form sửa thông tin không gửi ảnh ⇒ giữ ảnh hiện tại; xoá ảnh qua DELETE /image.
        if (StringUtils.hasText(request.image())) {
            store.setImage(request.image());
        }
        if (request.description() != null) {
            store.setDescription(textOrNull(request.description()));
        }
        if (request.active() != null) {
            store.setActive(request.active());
        }
        if (StringUtils.hasText(request.status())) {
            store.setStatus(request.status());
        }
    }

    private static String textOrNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private StoreResponse toResponse(StoreLocation store) {
        return toResponse(store, null);
    }

    private StoreResponse toResponse(StoreLocation store, Double distanceKm) {
        return new StoreResponse(
                store.getId(), store.getName(), store.getContactPhone(), store.getAddress(),
                store.getLatitude(), store.getLongitude(), store.getImage(), store.getDescription(), store.getActive(),
                distanceKm, store.getStatus(), store.getImage(), store.getCreatedAt(), store.getUpdatedAt());
    }

    private Double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
