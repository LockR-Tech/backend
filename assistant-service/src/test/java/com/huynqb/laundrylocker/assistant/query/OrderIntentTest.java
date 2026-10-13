package com.huynqb.laundrylocker.assistant.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderIntentTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Đơn gửi hàng hôm qua của tôi đã có người lấy chưa?",
            "don gui hang hom qua cua toi da co nguoi lay chua",
            "Tủ tôi thuê còn bao lâu hết giờ?",
            "tu toi thue con bao lau het gio",
            "Đơn ORD-20261013-AB12 đã thanh toán chưa?",
            "ord-20261013-ab12 thanh toan chua",
            "Có hàng ai gửi cho tôi không?",
            "co hang ai gui cho toi khong",
            "Đơn hàng của tôi",
            "don hang cua toi",
            "Hàng của mình đến chưa?",
            "Drone giao tới đâu rồi?",
            "Mình đã thanh toán chưa nhỉ",
            "Tôi vừa đặt thuê ô, bao giờ hết hạn?",
            "Tôi có bao nhiêu đơn?",
            "Ô của tôi ở tủ nào?",
            "Mã PIN của tôi là gì?",
            "Vì sao kiosk báo đơn của tôi chưa thanh toán?",
            "Đơn gần nhất đã hoàn tiền chưa?",
            "Tôi đã gửi hàng nhưng người nhận chưa thấy mã",
            "What is the status of my order?"
    })
    void detectsQuestionsAboutTheAskersOwnOrders(String question) {
        assertTrue(OrderIntent.isOrderQuestion(question), question);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Làm sao để gửi hàng?",
            "lam sao de gui hang?",
            "Làm sao để gửi hàng cho người khác qua tủ?",
            "Hoàn tiền trong bao lâu?",
            "Tính cả cuối tuần không?",
            "Phí gửi hàng bao nhiêu?",
            "Làm sao để gia hạn thuê ô?",
            "Hết giờ thuê mà đồ vẫn còn trong ô thì sao?",
            "Người nhận chưa có tài khoản thì sao?",
            "Người nhận lấy mã mở tủ ở đâu?",
            "Lock.R thu thập những thông tin gì của tôi?",
            "Tôi muốn xoá tài khoản và dữ liệu cá nhân thì làm sao?",
            "Tôi có thể trả tiền mặt trong ứng dụng không?",
            "Cách đơn giản nhất để tôi gửi hàng là gì?",
            "Kiểm tra khoá điện và cảm biến cửa thế nào?",
            "Giá vàng hôm nay bao nhiêu?",
            "How do I rent a locker?",
            ""
    })
    void ignoresGeneralHowToQuestions(String question) {
        assertFalse(OrderIntent.isOrderQuestion(question), question);
    }

    @Test
    void nullIsNotAnOrderQuestion() {
        assertFalse(OrderIntent.isOrderQuestion(null));
    }

    @Test
    void extractsOrderCodesUppercased() {
        assertEquals(List.of("ORD-20261013-AB12", "ORD-20261012-Z9"),
                List.copyOf(OrderIntent.orderCodes("so sánh ord-20261013-ab12 với ORD-20261012-Z9 và ORD-2026")));
        assertTrue(OrderIntent.orderCodes("không có mã").isEmpty());
    }
}
