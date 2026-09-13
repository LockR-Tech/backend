package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.dto.DroneDeliveryResponse;
import com.huynqb.laundrylocker.locker.model.*;
import com.huynqb.laundrylocker.locker.repository.DroneDeliveryRequestRepository;
import com.huynqb.laundrylocker.locker.repository.DroneUnitRepository;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/// Luồng giao hàng bằng drone: KHÁCH tạo yêu cầu -> đội bay (MAINTENANCE)
/// điều phối một drone trong fleet -> đánh dấu đã thả hàng. Thay cho mock
/// in-memory trên mobile (DroneDeliveryStore).
@Service
@RequiredArgsConstructor
public class DroneDeliveryService {

    private final DroneDeliveryRequestRepository requestRepository;
    private final DroneUnitRepository droneUnitRepository;
    private final LockerUnitRepository lockerUnitRepository;
    private final LockerBoxRepository lockerBoxRepository;

    @Transactional
    public DroneDeliveryResponse create(
            Long userId, Long lockerId, Long boxId, String receiverPhone, String description) {
        LockerUnit locker =
                lockerUnitRepository
                        .findById(lockerId)
                        .orElseThrow(() -> new BusinessException("LOCKER_NOT_FOUND", "Locker not found"));
        DroneDeliveryRequest request = new DroneDeliveryRequest();
        request.setLockerId(locker.getId());
        request.setRequesterUserId(userId);
        request.setReceiverPhone(receiverPhone);
        request.setDescription(description);
        if (boxId != null) {
            LockerBox box =
                    lockerBoxRepository
                            .findById(boxId)
                            .orElseThrow(() -> new BusinessException("BOX_NOT_FOUND", "Box not found"));
            if (!lockerId.equals(box.getLockerId())) {
                throw new BusinessException("BOX_NOT_IN_LOCKER", "Box does not belong to this locker");
            }
            if (!"DRONE".equalsIgnoreCase(box.getCellType())) {
                throw new BusinessException(
                        "DRONE_CELL_REQUIRED", "Drone deliveries drop into DRONE cells only");
            }
            request.setBoxId(box.getId());
            request.setBoxNumber(box.getBoxNumber());
        }
        return toResponse(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<DroneDeliveryResponse> myRequests(Long userId) {
        return requestRepository.findByRequesterUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DroneDeliveryResponse cancel(Long id, Long userId) {
        DroneDeliveryRequest request = find(id);
        if (!request.getRequesterUserId().equals(userId)) {
            throw new BusinessException("DRONE_DELIVERY_NOT_OWNER", "Only the requester can cancel");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(
                    "DRONE_DELIVERY_STATUS_INVALID", "Only PENDING requests can be canceled");
        }
        request.setStatus("CANCELED");
        return toResponse(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<DroneDeliveryResponse> queue(String status) {
        List<DroneDeliveryRequest> requests =
                StringUtils.hasText(status)
                        ? requestRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase())
                        : requestRepository.findAllByOrderByCreatedAtDesc();
        return requests.stream().map(this::toResponse).toList();
    }

    /// Đội bay nhận yêu cầu: gán drone (tuỳ chọn) và chuyển drone sang IN_FLIGHT.
    @Transactional
    public DroneDeliveryResponse dispatch(Long id, Long dispatcherId, Long droneUnitId) {
        DroneDeliveryRequest request = find(id);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(
                    "DRONE_DELIVERY_STATUS_INVALID", "Only PENDING requests can be dispatched");
        }
        if (droneUnitId != null) {
            DroneUnit drone =
                    droneUnitRepository
                            .findById(droneUnitId)
                            .orElseThrow(() -> new BusinessException("DRONE_NOT_FOUND", "Drone not found"));
            if (!Boolean.TRUE.equals(drone.getActive())) {
                throw new BusinessException("DRONE_INACTIVE", "Drone has been decommissioned");
            }
            if (DroneStatus.FAULT.equals(drone.getStatus())
                    || DroneStatus.MAINTENANCE.equals(drone.getStatus())
                    || DroneStatus.IN_FLIGHT.equals(drone.getStatus())) {
                throw new BusinessException(
                        "DRONE_NOT_AVAILABLE", "Drone is not available for dispatch: " + drone.getStatus());
            }
            drone.setStatus(DroneStatus.IN_FLIGHT);
            droneUnitRepository.save(drone);
            request.setDroneUnitId(drone.getId());
        }
        request.setStatus("DISPATCHED");
        request.setDispatchedBy(dispatcherId);
        return toResponse(requestRepository.save(request));
    }

    /// Drone đã thả hàng xong: yêu cầu DELIVERED, drone quay về IDLE.
    @Transactional
    public DroneDeliveryResponse complete(Long id) {
        DroneDeliveryRequest request = find(id);
        if (!"DISPATCHED".equals(request.getStatus())) {
            throw new BusinessException(
                    "DRONE_DELIVERY_STATUS_INVALID", "Only DISPATCHED requests can be completed");
        }
        if (request.getDroneUnitId() != null) {
            droneUnitRepository
                    .findById(request.getDroneUnitId())
                    .filter(drone -> DroneStatus.IN_FLIGHT.equals(drone.getStatus()))
                    .ifPresent(
                            drone -> {
                                drone.setStatus(DroneStatus.IDLE);
                                droneUnitRepository.save(drone);
                            });
        }
        request.setStatus("DELIVERED");
        return toResponse(requestRepository.save(request));
    }

    private DroneDeliveryRequest find(Long id) {
        return requestRepository
                .findById(id)
                .orElseThrow(
                        () -> new BusinessException("DRONE_DELIVERY_NOT_FOUND", "Delivery request not found"));
    }

    private DroneDeliveryResponse toResponse(DroneDeliveryRequest request) {
        LockerUnit locker = lockerUnitRepository.findById(request.getLockerId()).orElse(null);
        String droneCode =
                request.getDroneUnitId() == null
                        ? null
                        : droneUnitRepository
                        .findById(request.getDroneUnitId())
                        .map(DroneUnit::getCode)
                        .orElse(null);
        return new DroneDeliveryResponse(
                request.getId(),
                request.getLockerId(),
                locker == null ? null : locker.getCode(),
                locker == null ? null : locker.getName(),
                locker == null ? null : locker.getAddress(),
                request.getBoxId(),
                request.getBoxNumber(),
                request.getRequesterUserId(),
                request.getReceiverPhone(),
                request.getDescription(),
                request.getStatus(),
                request.getDroneUnitId(),
                droneCode,
                request.getDispatchedBy(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
