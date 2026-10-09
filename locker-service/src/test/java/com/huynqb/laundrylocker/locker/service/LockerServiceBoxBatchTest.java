package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.locker.dto.*;
import com.huynqb.laundrylocker.locker.model.CellType;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockerServiceBoxBatchTest {

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private LockerService lockerService;

    private final Long LOCKER_ID = 1L;

    @Test
    void createBox_Success_BroadcastsLayoutUpdate() {
        BoxRequest request = new BoxRequest(LOCKER_ID, 10, "MEDIUM", "AVAILABLE", "STANDARD", 2, 3);
        when(boxRepository.existsByLockerIdAndBoxNumber(LOCKER_ID, 10)).thenReturn(false);

        LockerBox savedBox = new LockerBox();
        savedBox.setId(101L);
        savedBox.setLockerId(LOCKER_ID);
        savedBox.setBoxNumber(10);
        savedBox.setStatus("AVAILABLE");
        savedBox.setSize("MEDIUM");
        savedBox.setCellType("STANDARD");
        savedBox.setRowIndex(2);
        savedBox.setColIndex(3);

        when(boxRepository.save(any(LockerBox.class))).thenReturn(savedBox);

        LockerBoxSummary summary = lockerService.createBox(request);

        assertNotNull(summary);
        assertEquals(10, summary.boxNumber());
        assertEquals("AVAILABLE", summary.status());

        verify(rabbitTemplate).convertAndSend(
                eq(DomainEventNames.EXCHANGE),
                eq(DomainEventNames.LOCKER_LAYOUT_UPDATED),
                any(DomainEvent.class));
    }

    @Test
    void createBox_DuplicateNumber_ThrowsBusinessException() {
        BoxRequest request = new BoxRequest(LOCKER_ID, 10, "MEDIUM", "AVAILABLE", "STANDARD", 2, 3);
        when(boxRepository.existsByLockerIdAndBoxNumber(LOCKER_ID, 10)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> lockerService.createBox(request));
        assertEquals("BOX_ALREADY_EXISTS", ex.getCode());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void createBoxesBatch_ExplicitList_Success() {
        when(lockerRepository.existsById(LOCKER_ID)).thenReturn(true);
        when(boxRepository.existsByLockerIdAndBoxNumber(eq(LOCKER_ID), anyInt())).thenReturn(false);

        List<BoxItemRequest> items = List.of(
                new BoxItemRequest(1, "SMALL", "AVAILABLE", "STANDARD", 1, 0),
                new BoxItemRequest(2, "MEDIUM", "AVAILABLE", "STANDARD", 1, 1),
                new BoxItemRequest(3, "LARGE", "AVAILABLE", "STANDARD", 1, 2)
        );
        BatchCreateBoxesRequest request = new BatchCreateBoxesRequest(items, null, null, null, null, null, null, null);

        LockerBox b1 = new LockerBox();
        b1.setId(11L); b1.setLockerId(LOCKER_ID); b1.setBoxNumber(1); b1.setStatus("AVAILABLE"); b1.setSize("SMALL"); b1.setCellType("STANDARD");
        LockerBox b2 = new LockerBox();
        b2.setId(12L); b2.setLockerId(LOCKER_ID); b2.setBoxNumber(2); b2.setStatus("AVAILABLE"); b2.setSize("MEDIUM"); b2.setCellType("STANDARD");
        LockerBox b3 = new LockerBox();
        b3.setId(13L); b3.setLockerId(LOCKER_ID); b3.setBoxNumber(3); b3.setStatus("AVAILABLE"); b3.setSize("LARGE"); b3.setCellType("STANDARD");

        when(boxRepository.saveAll(anyList())).thenReturn(List.of(b1, b2, b3));

        List<CellResponse> result = lockerService.createBoxesBatch(LOCKER_ID, request);

        assertEquals(3, result.size());
        assertEquals(1, result.get(0).boxNumber());
        assertEquals(2, result.get(1).boxNumber());
        assertEquals(3, result.get(2).boxNumber());

        verify(rabbitTemplate, times(3)).convertAndSend(
                eq(DomainEventNames.EXCHANGE),
                eq(DomainEventNames.LOCKER_LAYOUT_UPDATED),
                any(DomainEvent.class));
    }

    @Test
    void createBoxesBatch_RangeGenerator_Success() {
        when(lockerRepository.existsById(LOCKER_ID)).thenReturn(true);
        when(boxRepository.existsByLockerIdAndBoxNumber(eq(LOCKER_ID), anyInt())).thenReturn(false);

        BatchCreateBoxesRequest request = new BatchCreateBoxesRequest(
                null, 5, 2, "MEDIUM", "AVAILABLE", "STANDARD", 2, 0);

        LockerBox b1 = new LockerBox();
        b1.setId(51L); b1.setLockerId(LOCKER_ID); b1.setBoxNumber(5); b1.setStatus("AVAILABLE"); b1.setSize("MEDIUM"); b1.setCellType("STANDARD");
        LockerBox b2 = new LockerBox();
        b2.setId(52L); b2.setLockerId(LOCKER_ID); b2.setBoxNumber(6); b2.setStatus("AVAILABLE"); b2.setSize("MEDIUM"); b2.setCellType("STANDARD");

        when(boxRepository.saveAll(anyList())).thenReturn(List.of(b1, b2));

        List<CellResponse> result = lockerService.createBoxesBatch(LOCKER_ID, request);

        assertEquals(2, result.size());
        assertEquals(5, result.get(0).boxNumber());
        assertEquals(6, result.get(1).boxNumber());
    }

    @Test
    void createBoxesBatch_DuplicateInRequest_ThrowsBusinessException() {
        when(lockerRepository.existsById(LOCKER_ID)).thenReturn(true);

        List<BoxItemRequest> items = List.of(
                new BoxItemRequest(1, "SMALL", "AVAILABLE", "STANDARD", 1, 0),
                new BoxItemRequest(1, "MEDIUM", "AVAILABLE", "STANDARD", 1, 1)
        );
        BatchCreateBoxesRequest request = new BatchCreateBoxesRequest(items, null, null, null, null, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> lockerService.createBoxesBatch(LOCKER_ID, request));
        assertEquals("DUPLICATE_BOX_NUMBER", ex.getCode());
        verify(boxRepository, never()).saveAll(any());
    }

    @Test
    void deleteBox_Occupied_ThrowsBusinessException() {
        LockerBox box = new LockerBox();
        box.setId(10L);
        box.setLockerId(LOCKER_ID);
        box.setBoxNumber(10);
        box.setStatus("OCCUPIED");

        when(boxRepository.findById(10L)).thenReturn(Optional.of(box));

        BusinessException ex = assertThrows(BusinessException.class, () -> lockerService.deleteBox(10L));
        assertEquals("BOX_IN_USE", ex.getCode());
        verify(boxRepository, never()).delete(any());
    }

    @Test
    void deleteBoxByNumber_Success() {
        LockerBox box = new LockerBox();
        box.setId(10L);
        box.setLockerId(LOCKER_ID);
        box.setBoxNumber(10);
        box.setStatus("AVAILABLE");

        when(boxRepository.findByLockerIdAndBoxNumber(LOCKER_ID, 10)).thenReturn(Optional.of(box));

        lockerService.deleteBoxByNumber(LOCKER_ID, 10);

        verify(boxRepository).delete(box);
        verify(rabbitTemplate).convertAndSend(
                eq(DomainEventNames.EXCHANGE),
                eq(DomainEventNames.LOCKER_LAYOUT_UPDATED),
                any(DomainEvent.class));
    }

    @Test
    void deleteBoxesBatch_Success() {
        when(lockerRepository.existsById(LOCKER_ID)).thenReturn(true);

        LockerBox b1 = new LockerBox();
        b1.setId(1L); b1.setLockerId(LOCKER_ID); b1.setBoxNumber(1); b1.setStatus("AVAILABLE");
        LockerBox b2 = new LockerBox();
        b2.setId(2L); b2.setLockerId(LOCKER_ID); b2.setBoxNumber(2); b2.setStatus("FAULT");

        when(boxRepository.findByLockerIdAndIdIn(LOCKER_ID, List.of(1L, 2L))).thenReturn(List.of(b1, b2));

        BatchDeleteBoxesRequest request = new BatchDeleteBoxesRequest(List.of(1L, 2L), null);
        int deleted = lockerService.deleteBoxesBatch(LOCKER_ID, request);

        assertEquals(2, deleted);
        verify(boxRepository).deleteAll(anyList());
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(DomainEventNames.EXCHANGE),
                eq(DomainEventNames.LOCKER_LAYOUT_UPDATED),
                any(DomainEvent.class));
    }

    @Test
    void deleteBoxesBatch_HasOccupied_ThrowsAndDoesNotDelete() {
        when(lockerRepository.existsById(LOCKER_ID)).thenReturn(true);

        LockerBox b1 = new LockerBox();
        b1.setId(1L); b1.setLockerId(LOCKER_ID); b1.setBoxNumber(1); b1.setStatus("AVAILABLE");
        LockerBox b2 = new LockerBox();
        b2.setId(2L); b2.setLockerId(LOCKER_ID); b2.setBoxNumber(2); b2.setStatus("RESERVED");

        when(boxRepository.findByLockerIdAndIdIn(LOCKER_ID, List.of(1L, 2L))).thenReturn(List.of(b1, b2));

        BatchDeleteBoxesRequest request = new BatchDeleteBoxesRequest(List.of(1L, 2L), null);
        BusinessException ex = assertThrows(BusinessException.class, () -> lockerService.deleteBoxesBatch(LOCKER_ID, request));

        assertEquals("BOX_IN_USE", ex.getCode());
        verify(boxRepository, never()).deleteAll(any());
    }
}
