package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.i18n.ServerText;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Thư/SMS gửi người nhận đơn gửi hàng qua tủ phải có bản tiếng Anh trọn câu (ADR-0010). Tên, địa chỉ mẫu không
/// dấu nên bản dịch không được còn ký tự tiếng Việt — trừ lời nhắn người gửi tự gõ, phải giữ nguyên.
class ReceiverMessageEnglishTest {

    private static final Pattern VIETNAMESE = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String NOTE = "Nhớ lấy trước 5 giờ chiều nhé";

    @Test
    void receiverEmailsTranslateFullyAndKeepTheSendersNote() {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-1");
        order.setReceiverName("Tran An");
        order.setPinCode("482913");
        order.setCustomerNote(NOTE);
        OrderService.PickupPlace place = new OrderService.PickupPlace("Library locker", "Alpha building", 4);

        for (String vi : List.of(
                OrderService.receiverEmailBody(order, "Nguyen Binh", place, "14:30 03/11/2026"),
                OrderService.receiverEmailBody(order, null, place, "14:30 03/11/2026"),
                OrderService.receiverOrderCreatedEmailBody(order, "Nguyen Binh", place, 24),
                OrderService.receiverOrderCreatedEmailBody(order, null, place, 24),
                OrderService.receiverOrderCanceledEmailBody(order, "Nguyen Binh"),
                OrderService.receiverOrderCanceledEmailBody(order, null),
                OrderService.receiverReadyMessage(order, place, "14:30 03/11/2026"))) {
            String english = ServerText.toEnglish(vi);
            List<String> left = english.lines()
                    .map(line -> line.replace(NOTE, ""))
                    .filter(line -> VIETNAMESE.matcher(line).find())
                    .toList();
            assertEquals(List.of(), left, english);
            if (vi.contains(NOTE)) {
                assertTrue(english.contains("Message from the sender: " + NOTE), english);
            }
        }
    }

    @Test
    void pickupSmsHasAnEnglishVersion() {
        OrderService.PickupPlace place = new OrderService.PickupPlace("Thư viện", null, 4);
        String sms = OrderService.pickupSms("ORD-1", place, "482913", "14:30 03/11/2026");
        assertEquals("Lock.R: You have parcel ORD-1 at Thu vien, box no. 4. Locker code: 482913. "
                + "Pick up by: 14:30 03/11/2026.", ServerText.smsToEnglish(sms));
    }
}
