package com.huynqb.laundrylocker.locker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.dto.InstallationCheckRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationPhotosRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationResponse;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.model.InstallationCheckItem;
import com.huynqb.laundrylocker.locker.model.InstallationStatus;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerInstallation;
import com.huynqb.laundrylocker.locker.model.LockerInstallationCheck;
import com.huynqb.laundrylocker.locker.model.LockerInstallationPhoto;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationCheckRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationPhotoRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Lắp đặt & nghiệm thu tủ (F3-G12): vòng đời hồ sơ, các bước hệ thống tự xác nhận, ảnh bắt buộc.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerInstallationServiceTest {

    private static final String SECRET = "unit-secret";
    private static final long LOCKER = 7L;
    private static final long TECH = 21L;
    private static final long ADMIN = 1L;
    private static final long ID = 300L;

    @Mock private LockerInstallationRepository installationRepository;
    @Mock private LockerInstallationCheckRepository checkRepository;
    @Mock private LockerInstallationPhotoRepository photoRepository;
    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private LockerService lockerService;
    @Mock private IotClient iotClient;

    private CloudinaryMediaStorage storage;
    private LockerInstallationService service;

    private final List<LockerInstallationCheck> checkRows = new ArrayList<>();
    private final List<LockerInstallationPhoto> photoRows = new ArrayList<>();
    private LockerUnit locker;
    private LockerInstallation installation;

    @BeforeEach
    void setUp() {
        storage = spy(new CloudinaryMediaStorage("cloudinary://key:" + SECRET + "@demo", "lockr"));
        doNothing().when(storage).deleteAfterCommit(any());
        service = new LockerInstallationService(installationRepository, checkRepository, photoRepository,
                lockerRepository, boxRepository, lockerService, iotClient, storage, new ObjectMapper());

        locker = new LockerUnit();
        locker.setId(LOCKER);
        locker.setCode("TU-07");
        locker.setName("Tủ Vinhomes");
        locker.setStatus("INACTIVE");
        when(lockerRepository.findById(LOCKER)).thenReturn(Optional.of(locker));
        when(boxRepository.findByLockerId(LOCKER)).thenReturn(List.of(box(71L, 1, "AVAILABLE"), box(72L, 2, "AVAILABLE")));

        when(installationRepository.save(any(LockerInstallation.class))).thenAnswer(invocation -> {
            LockerInstallation row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ID);
            }
            installation = row;
            return row;
        });
        when(installationRepository.findById(ID)).thenAnswer(invocation -> Optional.ofNullable(installation));
        when(checkRepository.findByInstallationIdOrderByIdAsc(ID)).thenAnswer(invocation -> List.copyOf(checkRows));
        when(checkRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<LockerInstallationCheck> rows = invocation.getArgument(0);
            rows.forEach(this::store);
            return rows;
        });
        when(checkRepository.save(any(LockerInstallationCheck.class))).thenAnswer(invocation -> {
            LockerInstallationCheck row = invocation.getArgument(0);
            store(row);
            return row;
        });
        when(photoRepository.findByInstallationIdOrderByIdAsc(ID)).thenAnswer(invocation -> List.copyOf(photoRows));
        when(photoRepository.countByInstallationId(ID)).thenAnswer(invocation -> (long) photoRows.size());
        when(photoRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<LockerInstallationPhoto> rows = invocation.getArgument(0);
            long id = 900 + photoRows.size();
            for (LockerInstallationPhoto row : rows) {
                row.setId(id++);
                photoRows.add(row);
            }
            return rows;
        });
        doAnswer(invocation -> photoRows.remove(invocation.<LockerInstallationPhoto>getArgument(0)))
                .when(photoRepository).delete(any(LockerInstallationPhoto.class));
        when(photoRepository.findById(anyLong())).thenAnswer(invocation -> photoRows.stream()
                .filter(photo -> photo.getId().equals(invocation.getArgument(0))).findFirst());
    }

    // ─── Lập hồ sơ ───

    @Test
    void createPlansLockerAssignsTechnicianAndSeedsEveryCheck() {
        InstallationResponse response = service.create(LOCKER, request(TECH), ADMIN);

        assertEquals("SCHEDULED", response.status());
        assertEquals("INACTIVE", installation.getPreviousLockerStatus());
        assertEquals(InstallationCheckItem.values().length, checkRows.size());
        assertTrue(checkRows.stream().allMatch(check -> LockerInstallationCheck.PENDING.equals(check.getStatus())));
        verify(lockerService).assignLockerTechnician(LOCKER, TECH);
        verify(lockerService).applyInstallationStatus(LOCKER, "PLANNED");
        verify(lockerService).notifyInstallation(eq(DomainEventNames.LOCKER_INSTALLATION_ASSIGNED), eq(TECH), eq(ID),
                startsWith("Bạn được giao lắp đặt tủ Tủ Vinhomes"));
    }

    @Test
    void createRejectsWhenAnotherDossierIsOpen() {
        when(installationRepository.existsByLockerIdAndStatusIn(eq(LOCKER), anyCollection())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(LOCKER, request(TECH), ADMIN));

        assertEquals("INSTALLATION_ALREADY_OPEN", ex.getCode());
        verify(lockerService, never()).applyInstallationStatus(anyLong(), anyString());
    }

    @Test
    void createRejectsLockerStillHoldingOrders() {
        when(boxRepository.findByLockerId(LOCKER)).thenReturn(List.of(box(71L, 1, "OCCUPIED"), box(72L, 2, "AVAILABLE")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(LOCKER, request(TECH), ADMIN));

        assertEquals("INSTALLATION_LOCKER_IN_USE", ex.getCode());
        assertTrue(ex.getMessage().contains("#1"));
    }

    // ─── Checklist ───

    @Test
    void technicianCannotTickStepsTheSystemVerifies() {
        inProgress();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateCheck(
                ID, "NETWORK", new InstallationCheckRequest("PASSED", null), TECH, false));

        assertEquals("INSTALLATION_CHECK_AUTO", ex.getCode());
    }

    @Test
    void photoRequiredStepNeedsAPhotoBeforePassing() {
        inProgress();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateCheck(
                ID, "MOUNTING", new InstallationCheckRequest("PASSED", null), TECH, false));
        assertEquals("INSTALLATION_PHOTO_REQUIRED", ex.getCode());

        service.addPhotos(ID, new InstallationPhotosRequest("MOUNTING", List.of(photo("m1"))), TECH, false);
        service.updateCheck(ID, "MOUNTING", new InstallationCheckRequest("PASSED", null), TECH, false);

        LockerInstallationCheck mounting = check(InstallationCheckItem.MOUNTING);
        assertEquals("PASSED", mounting.getStatus());
        assertEquals("TECHNICIAN", mounting.getVerifiedBy());
        assertEquals(TECH, mounting.getCheckedBy());
    }

    @Test
    void lidCanBeMarkedNotApplicableWithReasonAndSurvivesRefresh() {
        inProgress();
        commissioning(readyStatus(List.of()));

        assertThrows(BusinessException.class, () -> service.updateCheck(
                ID, "LID", new InstallationCheckRequest("NOT_APPLICABLE", " "), TECH, false));
        service.updateCheck(ID, "LID", new InstallationCheckRequest("NOT_APPLICABLE", "Tủ không có bãi đáp"), TECH, false);
        service.detail(ID, TECH, false);

        assertEquals("NOT_APPLICABLE", check(InstallationCheckItem.LID).getStatus());
        assertThrows(BusinessException.class, () -> service.updateCheck(
                ID, "MOUNTING", new InstallationCheckRequest("NOT_APPLICABLE", "x"), TECH, false));
    }

    @Test
    void technicianActionsReturnControllerSoAppKeepsBoxTestEnabled() {
        inProgress();
        commissioning(readyStatus(List.of()));

        // App thay cả hồ sơ bằng phản hồi; thiếu bộ điều khiển thì app ẩn nút mở thử ô ("Gán bộ điều khiển trước").
        InstallationResponse response = service.updateCheck(
                ID, "LID", new InstallationCheckRequest("NOT_APPLICABLE", "Tủ không có bãi đáp"), TECH, false);

        assertEquals("AA:BB:CC:DD:EE:FF", response.controllerMac());
        assertTrue(response.iotAvailable());
    }

    @Test
    void otherTechnicianCannotWorkOnTheDossier() {
        inProgress();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(ID, 99L, false));

        assertEquals("INSTALLATION_NOT_ASSIGNED", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ─── Hệ thống tự xác nhận ───

    @Test
    void autoChecksPassFromControllerConfigAndBoxTests() {
        inProgress();
        commissioning(readyStatus(List.of(
                command("BOX_OPEN_TEST", 71L, "FAILED", 0),
                command("BOX_OPEN_TEST", 71L, "SUCCESS", 1),   // mở thử lại sau khi sửa ⇒ lấy kết quả mới nhất
                command("BOX_OPEN_TEST", 72L, "OPEN", 2),
                command("LID_OPEN", null, "SUCCESS", 3),
                command("LID_CLOSE", null, "ACCEPTED", 4))));

        InstallationResponse response = service.detail(ID, TECH, false);

        assertEquals("AA:BB:CC:DD:EE:FF", response.controllerMac());
        for (InstallationCheckItem item : List.of(InstallationCheckItem.NETWORK, InstallationCheckItem.CONTROLLER,
                InstallationCheckItem.HARDWARE_CONFIG, InstallationCheckItem.BOX_TEST, InstallationCheckItem.LID)) {
            assertEquals("PASSED", check(item).getStatus(), item.name());
            assertEquals("SYSTEM", check(item).getVerifiedBy(), item.name());
        }
        InstallationResponse.Check boxTest = response.checks().stream()
                .filter(check -> check.item().equals("BOX_TEST")).findFirst().orElseThrow();
        assertEquals(2, boxTest.detail().size());
        assertEquals("PASSED", boxTest.detail().get(0).get("status").asText());
    }

    @Test
    void latestBoxFailureFailsBoxTestAndMissingBoxKeepsItPending() {
        inProgress();
        commissioning(readyStatus(List.of(
                command("BOX_OPEN", 71L, "SUCCESS", 0),
                command("BOX_OPEN", 71L, "TIMEOUT", 1))));
        service.detail(ID, TECH, false);
        assertEquals("FAILED", check(InstallationCheckItem.BOX_TEST).getStatus());
        assertTrue(check(InstallationCheckItem.BOX_TEST).getNote().contains("#1"));

        commissioning(readyStatus(List.of(command("BOX_OPEN", 71L, "SUCCESS", 0))));
        service.detail(ID, TECH, false);
        assertEquals("PENDING", check(InstallationCheckItem.BOX_TEST).getStatus());
    }

    @Test
    void failedLidCommandShowsControllerReason() {
        inProgress();
        commissioning(readyStatus(List.of(new IotClient.CommandResult("LID_OPEN", null, null, 1, "FAILED",
                "Trục 1 chưa bật trên Pi", LocalDateTime.of(2026, 11, 9, 9, 0)))));
        service.detail(ID, TECH, false);
        assertEquals("FAILED", check(InstallationCheckItem.LID).getStatus());
        assertEquals("Lệnh nắp lỗi (mở: FAILED — Trục 1 chưa bật trên Pi; đóng: chưa thử)",
                check(InstallationCheckItem.LID).getNote());
    }

    @Test
    void serverTimestampInNotesIsShownInVietnamTime() {
        // Container lưu giờ UTC: 16:25 UTC là 23:25 ở Việt Nam.
        assertEquals(" lúc 23:25 11/11/2026",
                LockerInstallationService.atStorageTime(LocalDateTime.of(2026, 11, 11, 16, 25), ZoneOffset.UTC));
        assertEquals("", LockerInstallationService.atStorageTime(null, ZoneOffset.UTC));
    }

    @Test
    void offlineOrUnsetControllerKeepsStepsPending() {
        inProgress();
        commissioning(new IotClient.CommissioningStatus(LOCKER, controller(false, "PARTIAL"), null, null, null, List.of()));

        service.detail(ID, TECH, false);

        assertEquals("PENDING", check(InstallationCheckItem.NETWORK).getStatus());
        assertEquals("FAILED", check(InstallationCheckItem.CONTROLLER).getStatus());
        assertEquals("PENDING", check(InstallationCheckItem.HARDWARE_CONFIG).getStatus());
    }

    // ─── Gửi duyệt / duyệt ───

    @Test
    void submitListsWhatIsMissing() {
        inProgress();
        commissioning(readyStatus(boxAndLidOk()));
        passManualSteps();
        service.addPhotos(ID, new InstallationPhotosRequest(null, List.of(photo("s1"))), TECH, false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(ID, TECH, false));

        assertEquals("INSTALLATION_INCOMPLETE", ex.getCode());
        assertTrue(ex.getMessage().contains("ảnh hiện trường"));
        assertEquals(InstallationStatus.IN_PROGRESS, installation.getStatus());
    }

    @Test
    void submitNeedsIotDataToVerify() {
        inProgress();
        when(iotClient.commissioning(eq(LOCKER), any())).thenThrow(new RuntimeException("iot down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(ID, TECH, false));

        assertEquals("IOT_SERVICE_UNAVAILABLE", ex.getCode());
    }

    @Test
    void completeDossierIsSubmittedThenApproved() {
        inProgress();
        commissioning(readyStatus(boxAndLidOk()));
        passManualSteps();
        service.addPhotos(ID, new InstallationPhotosRequest(null, List.of(photo("s1"), photo("s2"))), TECH, false);

        InstallationResponse submitted = service.submit(ID, TECH, false);
        assertEquals("SUBMITTED", submitted.status());
        verify(lockerService).applyInstallationStatus(LOCKER, "COMMISSIONING");

        InstallationResponse approved = service.approve(ID, ADMIN);
        assertEquals("APPROVED", approved.status());
        assertEquals(ADMIN, installation.getReviewedBy());
        verify(lockerService).applyInstallationStatus(LOCKER, "ACTIVE");
        verify(lockerService).notifyInstallation(eq(DomainEventNames.LOCKER_INSTALLATION_REVIEWED), eq(TECH), eq(ID),
                anyString());
    }

    @Test
    void returnSendsDossierBackWithReason() {
        inProgress();
        installation.setStatus(InstallationStatus.SUBMITTED);

        InstallationResponse response = service.returnToTechnician(ID, " Thiếu ảnh mặt sau ", ADMIN);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals("Thiếu ảnh mặt sau", installation.getReturnReason());
        // Một lần lúc bắt đầu lắp, một lần khi bị trả về.
        verify(lockerService, times(2)).applyInstallationStatus(LOCKER, "INSTALLING");
    }

    @Test
    void cancelRestoresPreviousLockerStatus() {
        inProgress();
        installation.setPreviousLockerStatus("MAINTENANCE");

        service.cancel(ID, "Đổi địa điểm", ADMIN);

        assertEquals(InstallationStatus.CANCELLED, installation.getStatus());
        verify(lockerService).applyInstallationStatus(LOCKER, "MAINTENANCE");
        assertThrows(BusinessException.class, () -> service.cancel(ID, "lần nữa", ADMIN));
    }

    @Test
    void deletingLastRequiredPhotoReopensTheStep() {
        inProgress();
        service.addPhotos(ID, new InstallationPhotosRequest("KIOSK", List.of(photo("k1"))), TECH, false);
        service.updateCheck(ID, "KIOSK", new InstallationCheckRequest("PASSED", null), TECH, false);
        Long photoId = photoRows.get(0).getId();

        service.deletePhoto(ID, photoId, TECH, false);

        assertEquals("PENDING", check(InstallationCheckItem.KIOSK).getStatus());
        assertNull(check(InstallationCheckItem.KIOSK).getVerifiedBy());
        verify(storage).deleteAfterCommit(startsWith("lockr/installations/u" + TECH + "/"));
    }

    @Test
    void duplicatePhotoIsRejected() {
        inProgress();
        ReportAttachmentRequest same = photo("dup");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addPhotos(
                ID, new InstallationPhotosRequest(null, List.of(same, same)), TECH, false));

        assertEquals("ATTACHMENT_DUPLICATE", ex.getCode());
        assertFalse(photoRows.stream().anyMatch(photo -> photo.getPublicId().endsWith("/dup")));
    }

    // ─── Dữ liệu mẫu ───

    private void inProgress() {
        service.create(LOCKER, request(TECH), ADMIN);
        service.start(ID, TECH, false);
    }

    private void passManualSteps() {
        service.addPhotos(ID, new InstallationPhotosRequest("MOUNTING", List.of(photo("m1"))), TECH, false);
        service.addPhotos(ID, new InstallationPhotosRequest("KIOSK", List.of(photo("k1"))), TECH, false);
        service.updateCheck(ID, "MOUNTING", new InstallationCheckRequest("PASSED", null), TECH, false);
        service.updateCheck(ID, "KIOSK", new InstallationCheckRequest("PASSED", null), TECH, false);
        service.updateCheck(ID, "BLUETOOTH", new InstallationCheckRequest("PASSED", null), TECH, false);
    }

    private void store(LockerInstallationCheck row) {
        if (row.getId() == null) {
            row.setId(500L + checkRows.size());
        }
        if (!checkRows.contains(row)) {
            checkRows.add(row);
        }
    }

    private LockerInstallationCheck check(InstallationCheckItem item) {
        return checkRows.stream().filter(check -> check.getItem() == item).findFirst().orElseThrow();
    }

    private void commissioning(IotClient.CommissioningStatus status) {
        when(iotClient.commissioning(eq(LOCKER), any())).thenReturn(ApiResponse.ok(status));
    }

    private static IotClient.CommissioningStatus readyStatus(List<IotClient.CommandResult> commands) {
        return new IotClient.CommissioningStatus(LOCKER, controller(true, "COMPLETED"), "SUCCESS",
                LocalDateTime.now(), null, commands);
    }

    private static List<IotClient.CommandResult> boxAndLidOk() {
        return List.of(command("BOX_OPEN_TEST", 71L, "SUCCESS", 0), command("BOX_OPEN_TEST", 72L, "SUCCESS", 1),
                command("LID_OPEN", null, "SUCCESS", 2), command("LID_CLOSE", null, "SUCCESS", 3));
    }

    private static IotClient.GatewayDeviceResponse controller(boolean online, String setupStatus) {
        return new IotClient.GatewayDeviceResponse(3L, "AA:BB:CC:DD:EE:FF", "pi4", "1.0", 1, 16, LOCKER, LOCKER,
                online, setupStatus, null, LocalDateTime.now());
    }

    private static IotClient.CommandResult command(String action, Long boxId, String status, int minute) {
        return new IotClient.CommandResult(action, boxId, boxId == null ? null : (int) (boxId - 70), null, status,
                null, LocalDateTime.of(2026, 11, 9, 9, minute));
    }

    private static InstallationRequest request(Long technicianId) {
        return new InstallationRequest(technicianId, LocalDateTime.of(2026, 11, 10, 8, 0), "Chị Lan", "0901234567",
                "Ổ 220V sảnh B", "wifi", "SSID LockR", "LR-16", "SN-001", null);
    }

    private static LockerBox box(Long id, int number, String status) {
        LockerBox box = new LockerBox();
        box.setId(id);
        box.setLockerId(LOCKER);
        box.setBoxNumber(number);
        box.setStatus(status);
        box.setActive(true);
        return box;
    }

    /// Ảnh "đã upload" hợp lệ: chữ ký phản hồi đúng như Cloudinary ký.
    private static ReportAttachmentRequest photo(String name) {
        String publicId = "lockr/installations/u" + TECH + "/" + name;
        long version = 1700000000L;
        String signature = CloudinaryMediaStorage.sign(
                Map.of("public_id", publicId, "version", String.valueOf(version)), SECRET);
        return new ReportAttachmentRequest(
                publicId, version, signature, "jpg", 2048L, 1200, 900, null, "2026-11-10T08:30:00", 10.84, 106.8);
    }
}
