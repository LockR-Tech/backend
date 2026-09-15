package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.locker.dto.BoxLookupResponse;
import com.huynqb.laundrylocker.locker.dto.LockerResponse;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockerLookupServiceTest {

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @InjectMocks private LockerLookupService service;

    @Test
    void lockersCountsBoxesWithOneQueryForTheWholeBatch() {
        when(lockerRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(locker(1L, "LK-01"), locker(2L, "LK-02")));
        when(boxRepository.findByLockerIdIn(List.of(1L, 2L))).thenReturn(List.of(
                box(11L, 1L, 1, "AVAILABLE", true),
                box(12L, 1L, 2, "OCCUPIED", true),
                box(13L, 1L, 3, "AVAILABLE", false),
                box(21L, 2L, 1, "AVAILABLE", true)));

        List<LockerResponse> result = service.lockers(Arrays.asList(1L, 2L, 1L, null));

        assertEquals(2, result.size());
        assertEquals("LK-01", result.get(0).code());
        assertEquals(3, result.get(0).totalBoxes());
        assertEquals(1, result.get(0).availableBoxes());
        assertEquals(1, result.get(1).totalBoxes());
        verify(boxRepository, never()).countByLockerId(1L);
    }

    @Test
    void lockersWithoutIdsReturnsEveryLocker() {
        when(lockerRepository.findAll()).thenReturn(List.of(locker(5L, "LK-05")));
        when(boxRepository.findByLockerIdIn(List.of(5L))).thenReturn(List.of());

        List<LockerResponse> result = service.lockers(null);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).totalBoxes());
    }

    @Test
    void boxesMapsNumbersAndSkipsEmptyInput() {
        when(boxRepository.findAllById(List.of(11L))).thenReturn(List.of(box(11L, 1L, 4, "OCCUPIED", true)));

        List<BoxLookupResponse> result = service.boxes(List.of(11L));

        assertEquals(4, result.get(0).boxNumber());
        assertEquals(1L, result.get(0).lockerId());
        assertTrue(service.boxes(List.of()).isEmpty());
    }

    @Test
    void emptyBoxLookupDoesNotHitDatabase() {
        service.boxes(null);
        verifyNoInteractions(boxRepository);
    }

    private LockerUnit locker(Long id, String code) {
        LockerUnit locker = new LockerUnit();
        locker.setId(id);
        locker.setCode(code);
        locker.setName("Tủ " + code);
        locker.setStoreId(3L);
        return locker;
    }

    private LockerBox box(Long id, Long lockerId, int number, String status, boolean active) {
        LockerBox box = new LockerBox();
        box.setId(id);
        box.setLockerId(lockerId);
        box.setBoxNumber(number);
        box.setStatus(status);
        box.setActive(active);
        return box;
    }
}
