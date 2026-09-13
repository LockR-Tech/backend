package com.huynqb.laundrylocker.store.service;

import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.store.client.OrderClient;
import com.huynqb.laundrylocker.store.dto.StoreRequest;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.model.StoreLocation;
import com.huynqb.laundrylocker.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository repository;
    private final OrderClient orderClient;

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
        double radius = radiusKm == null ? 10.0 : radiusKm;
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

    @Transactional
    public void delete(Long id) {
        repository.delete(repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id)));
    }

    @Transactional
    public StoreResponse updateImage(Long id, String imageUrl) {
        StoreLocation store = repository.findById(id).orElseThrow(() -> new NotFoundException("Store", id));
        store.setImage(imageUrl);
        return toResponse(repository.save(store));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ratings(Long storeId) {
        try {
            return orderClient.storeRatings(storeId).data();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void apply(StoreLocation store, StoreRequest request) {
        store.setName(request.name());
        store.setContactPhone(request.contactPhone());
        store.setAddress(request.address());
        store.setLatitude(request.latitude());
        store.setLongitude(request.longitude());
        store.setImage(request.image());
        store.setDescription(request.description());
        store.setActive(request.active() == null ? true : request.active());
        store.setStatus(StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
    }

    private StoreResponse toResponse(StoreLocation store) {
        return toResponse(store, null);
    }

    private StoreResponse toResponse(StoreLocation store, Double distanceKm) {
        return new StoreResponse(
                store.getId(), store.getName(), store.getContactPhone(), store.getAddress(),
                store.getLatitude(), store.getLongitude(), store.getImage(), store.getDescription(), store.getActive(),
                distanceKm, store.getStatus());
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
