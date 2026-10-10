package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.dto.DroneUnitRequest;
import com.huynqb.laundrylocker.locker.dto.UpdateBoxRequest;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.DroneUnitRepository;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Chặn thao tác admin làm sai dữ liệu: mở ô khẩn cấp, sửa ô, gán drone vào tủ.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceAdminGuardsTest {

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private DroneUnitRepository droneUnitRepository;
    @Mock private IotClient iotClient;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private LockerService service;

    // ─── force-open ───

    @Test
    void forceOpenReportsFailureWhenIotCallThrows() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "OCCUPIED")));
        when(iotClient.forceUnlock(any())).thenThrow(new RuntimeException("iot-service down"));

        Map<String, Object> result = service.forceOpen(5L, 9L);

        assertEquals(false, result.get("accepted"));
        assertEquals("FAILED", result.get("status"));
        assertEquals(3, result.get("boxNumber"));
        // Không phát "ô đã mở" khi chưa chắc tủ mở.
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void forceOpenPassesThroughIotTimeout() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "OCCUPIED")));
        when(iotClient.forceUnlock(any())).thenReturn(ApiResponse.ok(Map.of(
                "accepted", false, "status", "TIMEOUT", "boxId", 5L, "message", "Tủ không phản hồi")));

        Map<String, Object> result = service.forceOpen(5L, 9L);

        assertEquals(false, result.get("accepted"));
        assertEquals("TIMEOUT", result.get("status"));
        assertEquals(3, result.get("boxNumber"));
    }

    @Test
    void forceOpenWithoutIotBodyIsNotReportedAsOpened() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "OCCUPIED")));
        when(iotClient.forceUnlock(any())).thenReturn(null);

        assertEquals(false, service.forceOpen(5L, 9L).get("accepted"));
    }

    @Test
    void forceOpenSuccessKeepsIotResult() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "OCCUPIED")));
        when(iotClient.forceUnlock(any())).thenReturn(ApiResponse.ok(Map.of("accepted", true, "status", "OPENED")));

        Map<String, Object> result = service.forceOpen(5L, 9L);

        assertEquals(true, result.get("accepted"));
        assertEquals("OPENED", result.get("status"));
    }

    // ─── updateBox ───

    @Test
    void renumberingOntoExistingBoxIsRejected() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "AVAILABLE")));
        when(boxRepository.existsByLockerIdAndBoxNumber(1L, 4)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBox(5L, request(4, null, null, null)));

        assertEquals("BOX_ALREADY_EXISTS", ex.getCode());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void keepingOwnBoxNumberIsAllowed() {
        LockerBox box = box(5L, 3, "AVAILABLE");
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box));
        when(boxRepository.existsByLockerIdAndBoxNumber(1L, 3)).thenReturn(true);
        when(boxRepository.save(any(LockerBox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateBox(5L, request(3, 2, 1, null));

        assertEquals(2, box.getRowIndex());
        assertEquals(1, box.getColIndex());
    }

    @Test
    void negativePositionIsRejected() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "AVAILABLE")));

        BusinessException row = assertThrows(BusinessException.class,
                () -> service.updateBox(5L, request(null, -1, null, null)));
        BusinessException col = assertThrows(BusinessException.class,
                () -> service.updateBox(5L, request(null, null, -2, null)));

        assertEquals("INVALID_BOX_POSITION", row.getCode());
        assertEquals("INVALID_BOX_POSITION", col.getCode());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void boxHoldingAnOrderCannotBeMarkedAvailable() {
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box(5L, 3, "OCCUPIED")));
        when(boxRepository.findById(6L)).thenReturn(Optional.of(box(6L, 4, "RESERVED")));
        LockerBox faulted = box(7L, 5, "FAULT");
        faulted.setPreFaultStatus("OCCUPIED");
        when(boxRepository.findById(7L)).thenReturn(Optional.of(faulted));

        for (Long id : new Long[]{5L, 6L, 7L}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateBox(id, request(null, null, null, "available")));
            assertEquals("BOX_IN_USE", ex.getCode());
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        }
        verify(boxRepository, never()).save(any());
    }

    @Test
    void idleBoxCanChangeStatus() {
        LockerBox box = box(5L, 3, "OUT_OF_SERVICE");
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box));
        when(boxRepository.save(any(LockerBox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateBox(5L, request(null, null, null, "AVAILABLE"));

        assertEquals("AVAILABLE", box.getStatus());
    }

    // ─── drone ───

    @Test
    void droneCannotBeCreatedAtLockerWithoutLandingPad() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker(1L, false)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createDroneUnit(new DroneUnitRequest(1L, "DR-01")));

        assertEquals("LANDING_PAD_ABSENT", ex.getCode());
        verify(droneUnitRepository, never()).save(any());
    }

    @Test
    void droneIsCreatedAtLockerWithLandingPad() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker(1L, true)));
        when(droneUnitRepository.save(any(DroneUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("DR-01", service.createDroneUnit(new DroneUnitRequest(1L, "DR-01")).code());
    }

    @Test
    void droneCannotMoveToLockerWithoutLandingPad() {
        DroneUnit unit = new DroneUnit();
        unit.setId(3L);
        unit.setLockerId(1L);
        unit.setCode("DR-01");
        when(droneUnitRepository.findById(3L)).thenReturn(Optional.of(unit));
        when(lockerRepository.findById(2L)).thenReturn(Optional.of(locker(2L, false)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateDroneUnit(3L, new com.huynqb.laundrylocker.locker.dto.DroneUpdateRequest(2L, null)));

        assertEquals("LANDING_PAD_ABSENT", ex.getCode());
        assertEquals(1L, unit.getLockerId());
    }

    private static UpdateBoxRequest request(Integer boxNumber, Integer row, Integer col, String status) {
        return new UpdateBoxRequest(boxNumber, null, null, row, col, null, status);
    }

    private static LockerBox box(Long id, int number, String status) {
        LockerBox box = new LockerBox();
        box.setId(id);
        box.setLockerId(1L);
        box.setBoxNumber(number);
        box.setStatus(status);
        return box;
    }

    private static LockerUnit locker(Long id, boolean landingPad) {
        LockerUnit locker = new LockerUnit();
        locker.setId(id);
        locker.setCode("CAB-" + id);
        locker.setName("Tủ " + id);
        locker.setLandingPad(landingPad);
        return locker;
    }
}
