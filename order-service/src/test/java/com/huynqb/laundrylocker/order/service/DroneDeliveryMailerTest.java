package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DroneDeliveryMailerTest {

    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private final LockerLookupClient lockerLookupClient = mock(LockerLookupClient.class);
    private final LockerCellClient lockerCellClient = mock(LockerCellClient.class);
    private final UserClient userClient = mock(UserClient.class);
    private final DroneDeliveryMailer mailer =
            new DroneDeliveryMailer(notificationClient, lockerLookupClient, lockerCellClient, userClient);

    @Test
    void launchEmailsTellBothSidesWhichLockerAndWhichBoxTheParcelGoesTo() {
        LockerOrder order = order();
        when(lockerCellClient.getCell(301L))
                .thenReturn(ApiResponse.ok(new CellDto(301L, 3, "M", "DRONE", 0, 2, "RESERVED", null)));
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));
        DroneMission mission = new DroneMission();
        mission.setDroneCode("DRONE-A1");

        DroneDeliveryMailer.Outcome outcome = mailer.launched(order, mission);

        assertTrue(outcome.senderReached());
        assertTrue(outcome.receiverReached());
        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient, times(2)).notifyGuest(sent.capture());
        List<GuestNotification.Request> mails = sent.getAllValues();
        assertEquals("gui@gmail.com", mails.get(0).email());
        assertEquals("nhan@gmail.com", mails.get(1).email());
        for (GuestNotification.Request mail : mails) {
            assertTrue(mail.emailBody().contains("Ô nhận: Ô số 3"), mail.emailBody());
            assertTrue(mail.emailHtml().contains("Ô số 3"));
        }
        assertTrue(mails.get(0).emailBody().contains("Người nhận: Trần Thị Nhận"));
        assertTrue(mails.get(1).emailBody().contains("Người gửi: Nguyễn Văn Gửi"));
    }

    @Test
    void dropReportAfterLaunchTellsBothSidesButNeverQuotesTheInternalReason() {
        LockerOrder order = order();
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        DroneDeliveryMailer.Outcome outcome = mailer.parcelDropReported(order, "DPI-21-1",
                java.time.Instant.parse("2026-10-30T07:05:00Z"), "10.84110, 106.80970", "drone đang trên đường", true);

        assertTrue(outcome.senderReached());
        assertTrue(outcome.receiverReached());
        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient, times(2)).notifyGuest(sent.capture());
        List<GuestNotification.Request> mails = sent.getAllValues();
        assertEquals("gui@gmail.com", mails.get(0).email());
        assertTrue(mails.get(0).subject().contains("gặp sự cố"));
        assertTrue(mails.get(0).emailBody().contains("Mã sự cố: DPI-21-1"), mails.get(0).emailBody());
        assertTrue(mails.get(0).emailHtml().contains("CẬP NHẬT SỰ CỐ"));
        assertEquals("nhan@gmail.com", mails.get(1).email());
        assertTrue(mails.get(1).emailBody().contains("Liên hệ người gửi: 0901000111"), mails.get(1).emailBody());
        for (GuestNotification.Request mail : mails) {
            assertTrue(mail.emailBody().contains("Thời gian: 14:05 30/10/2026"), mail.emailBody());
            assertTrue(mail.emailBody().contains("Vị trí báo rơi: 10.84110, 106.80970"), mail.emailBody());
            assertTrue(mail.emailBody().contains("Mô tả: Kiện rơi khỏi drone trong lúc bay (drone đang trên đường)."),
                    mail.emailBody());
        }
    }

    /// Mail 2: admin xác minh kết quả thu hồi ⇒ người gửi biết tìm thấy kiện chưa, kiện ra sao.
    @Test
    void verifiedRecoveryTellsBothSidesWhetherTheParcelWasFound() {
        LockerOrder order = order();
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        DroneDeliveryMailer.Outcome found = mailer.parcelRecoveryVerified(order, "DPI-21-1", true, "Hư hại nhẹ", "Trạm FPT",
                java.time.Instant.parse("2026-10-30T08:00:00Z"), true);

        assertTrue(found.senderReached());
        assertTrue(found.receiverReached());
        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient, times(2)).notifyGuest(sent.capture());
        GuestNotification.Request toSender = sent.getAllValues().get(0);
        assertEquals("gui@gmail.com", toSender.email());
        assertTrue(toSender.subject().startsWith("Đã tìm thấy kiện hàng"), toSender.subject());
        assertTrue(toSender.emailBody().contains("Tình trạng kiện: Hư hại nhẹ"), toSender.emailBody());
        assertTrue(toSender.emailBody().contains("Thời gian xác nhận: 15:00 30/10/2026"), toSender.emailBody());
        assertTrue(toSender.emailBody().contains("Kiện được đưa về trạm Trạm FPT"), toSender.emailBody());

        mailer.parcelRecoveryVerified(order, "DPI-21-1", false, null, null, null, false);
        verify(notificationClient, times(3)).notifyGuest(sent.capture());
        GuestNotification.Request lost = sent.getAllValues().get(sent.getAllValues().size() - 1);
        assertTrue(lost.subject().startsWith("Chưa tìm thấy kiện hàng"), lost.subject());
        assertTrue(lost.emailBody().contains("phương án xử lý"), lost.emailBody());
    }

    @Test
    void dropReportBeforeLaunchOnlyTellsTheSender() {
        LockerOrder order = order();
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        DroneDeliveryMailer.Outcome outcome = mailer.parcelDropReported(order, "DPI-21-1", null, null, null, false);

        assertTrue(outcome.senderReached());
        assertEquals(false, outcome.receiverReached());
        verify(notificationClient, times(1)).notifyGuest(any());
    }

    @Test
    void acceptedPlanWithoutRedeliveryTellsTheReceiverTheParcelWillNotArrive() {
        LockerOrder order = order();
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        mailer.incidentResolutionAccepted(order, "DPI-21-1", "Bồi thường", null, true);

        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient, times(2)).notifyGuest(sent.capture());
        assertTrue(sent.getAllValues().get(1).subject().contains("sẽ không được giao"));

        mailer.incidentResolutionAccepted(order, "DPI-21-1", "Giao lại miễn phí", "ORD-22", true);
        verify(notificationClient, times(4)).notifyGuest(sent.capture());
        assertTrue(sent.getAllValues().get(sent.getAllValues().size() - 1).subject().contains("ORD-22"));
    }

    @Test
    void destinationWithBoxNamesTheBoxNumberPrintedOnTheDoorNotTheInternalId() {
        LockerOrder order = order();
        when(lockerCellClient.getCell(301L))
                .thenReturn(ApiResponse.ok(new CellDto(301L, 3, "M", "DRONE", 0, 2, "RESERVED", null)));
        assertEquals("tủ #7 · Ô số 3", mailer.destinationWithBox(order));

        when(lockerCellClient.getCell(301L)).thenThrow(new RuntimeException("locker-service down"));
        assertEquals("tủ #7 · ô #301", mailer.destinationWithBox(order));

        order.setReservedBoxId(null);
        assertEquals("tủ #7 · chưa gán ô", mailer.destinationWithBox(order));
    }

    @Test
    void mailsCarryEachRecipientsAppLanguage() {
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));
        when(userClient.getUser(5L)).thenReturn(ApiResponse.ok(user(5L, "en")));
        when(userClient.getUser(9L)).thenReturn(ApiResponse.ok(user(9L, "vi")));
        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);

        // Người nhận chưa có tài khoản ⇒ theo ngôn ngữ người gửi.
        LockerOrder guestReceiver = order();
        guestReceiver.setUserId(5L);
        mailer.launched(guestReceiver, null);
        // Người nhận có tài khoản ⇒ ngôn ngữ của chính họ.
        LockerOrder memberReceiver = order();
        memberReceiver.setUserId(5L);
        memberReceiver.setReceiverUserId(9L);
        mailer.launched(memberReceiver, null);

        verify(notificationClient, times(4)).notifyGuest(sent.capture());
        List<String> languages = sent.getAllValues().stream().map(GuestNotification.Request::language).toList();
        assertEquals(List.of("en", "en", "en", "vi"), languages);
        // Nội dung vẫn soạn tiếng Việt — notification-service dịch lúc gửi.
        assertTrue(sent.getAllValues().get(0).subject().startsWith("Đơn hàng ORD-21"));
    }

    /// Mọi thư drone phải có bản dịch trọn câu: tên/địa chỉ trong dữ liệu mẫu không dấu nên bản tiếng Anh không
    /// được còn ký tự tiếng Việt nào (câu thiếu trong bảng dịch sẽ lộ ra ở đây).
    @Test
    void everyDroneMailHasACompleteEnglishVersion() {
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));
        when(lockerCellClient.getCell(301L))
                .thenReturn(ApiResponse.ok(new CellDto(301L, 3, "M", "DRONE", 0, 2, "RESERVED", null)));
        LockerOrder order = order();
        order.setSenderName("Nguyen Van Gui");
        order.setReceiverName("Tran Thi Nhan");
        order.setPickupAddress("12 Le Loi");
        order.setParcelFragile(true);
        DroneMission mission = new DroneMission();
        mission.setDroneCode("DRONE-A1");
        java.time.Instant at = java.time.Instant.parse("2026-11-02T07:30:00Z");

        mailer.launched(order, mission);
        mailer.failed(order, "Thời tiết xấu", null, 12L, true, true, true);
        mailer.failed(order, "Drone lỗi", null, null, false, false, false);
        mailer.parcelDropReported(order, "DPI-7", at, "10.84110, 106.80970", "drone đang trên đường", true);
        mailer.parcelRecoveryVerified(order, "DPI-7", true, "còn nguyên vẹn", "Station 2", at, true);
        mailer.parcelRecoveryVerified(order, "DPI-7", false, null, null, at, true);
        mailer.deliveryPaused(order, "Thời tiết xấu", null, true);
        mailer.deliveryResumed(order, "Thời tiết xấu", true);
        mailer.incidentProposal(order, "DPI-7", "Giao lại miễn phí", "hư hại nhẹ", "50000đ", null);
        mailer.incidentResolutionAccepted(order, "DPI-7", "Giao lại miễn phí", "ORD-99", true);
        mailer.incidentResolutionAccepted(order, "DPI-7", "Bồi thường", null, true);

        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient, org.mockito.Mockito.atLeast(18)).notifyGuest(sent.capture());
        java.util.regex.Pattern vietnamese = java.util.regex.Pattern.compile(
                "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
        for (GuestNotification.Request mail : sent.getAllValues()) {
            for (String english : List.of(
                    com.huynqb.laundrylocker.common.i18n.ServerText.toEnglish(mail.subject()),
                    com.huynqb.laundrylocker.common.i18n.ServerText.toEnglish(mail.emailBody()),
                    com.huynqb.laundrylocker.common.i18n.ServerText.htmlToEnglish(mail.emailHtml()))) {
                // "50000đ": ký hiệu tiền giữ nguyên như trong app.
                List<String> left = english.lines()
                        .filter(line -> vietnamese.matcher(line.replaceAll("\\d[\\d.,]*\\s?đ", "")).find())
                        .toList();
                assertEquals(List.of(), left);
            }
        }
    }

    private static UserSummary user(Long id, String language) {
        return new UserSummary(id, null, null, null, "ACTIVE", java.util.Set.of(), null, language);
    }

    private static LockerOrder order() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setDestinationLockerId(7L);
        order.setReservedBoxId(301L);
        order.setSenderName("Nguyễn Văn Gửi");
        order.setSenderPhone("0901000111");
        order.setSenderEmail("gui@gmail.com");
        order.setReceiverName("Trần Thị Nhận");
        order.setReceiverPhone("0902000222");
        order.setReceiverEmail("nhan@gmail.com");
        order.setParcelWeightGrams(1200);
        return order;
    }
}
