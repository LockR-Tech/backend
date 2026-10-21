package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
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
    private final DroneDeliveryMailer mailer =
            new DroneDeliveryMailer(notificationClient, lockerLookupClient, lockerCellClient);

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
