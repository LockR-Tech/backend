package com.huynqb.laundrylocker.assistant.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VietnameseTextTest {

    @Test
    void foldRemovesDiacriticsCaseAndPunctuation() {
        assertEquals("don hang cua toi", VietnameseText.fold("Đơn hàng CỦA tôi?"));
        assertEquals("don hang cua toi", VietnameseText.fold("don hang cua toi"));
        assertEquals("ord 20261013 ab12 da thanh toan chua",
                VietnameseText.fold("ORD-20261013-AB12 đã thanh toán chưa?"));
        // Dạng tổ hợp rời (NFD) cũng bỏ được dấu.
        assertEquals("tieng viet", VietnameseText.fold("tiếng việt"));
        assertEquals("", VietnameseText.fold(null));
    }

    @Test
    void detectsDiacritics() {
        assertTrue(VietnameseText.hasDiacritics("Tủ tôi thuê"));
        assertTrue(VietnameseText.hasDiacritics("dđ"));
        assertTrue(VietnameseText.hasDiacritics("Đ"));
        assertFalse(VietnameseText.hasDiacritics("tu toi thue con bao lau"));
        assertFalse(VietnameseText.hasDiacritics("ORD-20261013-AB12 123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lam sao de gui hang cho nguoi khac qua tu?",
            "don hang cua toi da thanh toan chua",
            "tu toi thue con bao lau het gio",
            "co hang ai gui cho toi khong",
            "khong mo duoc o",
            "Nguoi nhan lay ma mo tu o dau?",
            "thue o theo gio thi co mo lai nhieu lan duoc khong",
            "phi gui hang bao nhieu",
            "xoa tai khoan nhu the nao",
            "Don ORD-20261013-AB12 da thanh toan chua?"
    })
    void recognisesUnaccentedVietnamese(String text) {
        assertTrue(VietnameseText.isUnaccentedVietnamese(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Làm sao để gửi hàng cho người khác qua tủ?",
            "Đơn hàng của tôi đã thanh toán chưa?",
            "Tủ tôi thuê còn bao lâu hết giờ?",
            "Lam sao để gui hang",
            "How do I send a package to my friend?",
            "Where is my parcel?",
            "Can I pay with cash on delivery?",
            "The GUI hangs when I log in",
            "What is the refund policy?",
            "ORD-20261013-AB12",
            "12345",
            "   ",
            "Lock.R"
    })
    void rejectsAccentedEnglishAndNonText(String text) {
        assertFalse(VietnameseText.isUnaccentedVietnamese(text), text);
    }

    @Test
    void sameTextIgnoringDiacriticsAcceptsOnlyAddedAccents() {
        assertTrue(VietnameseText.sameTextIgnoringDiacritics(
                "lam sao de gui hang cho nguoi khac qua tu?", "Làm sao để gửi hàng cho người khác qua tủ?"));
        assertTrue(VietnameseText.sameTextIgnoringDiacritics(
                "don ORD-20261013-AB12 da thanh toan chua", "Đơn ORD-20261013-AB12 đã thanh toán chưa"));
        // Đổi số, đổi mã hay trả lời thay vì thêm dấu ⇒ không nhận.
        assertFalse(VietnameseText.sameTextIgnoringDiacritics(
                "don ORD-20261013-AB12 da thanh toan chua", "Đơn ORD-20261014-XY99 đã thanh toán chưa"));
        // Một chữ số khác cũng không được.
        assertFalse(VietnameseText.sameTextIgnoringDiacritics(
                "don ORD-20261013-AB12 da thanh toan chua", "Đơn ORD-20261014-AB12 đã thanh toán chưa"));
        assertFalse(VietnameseText.sameTextIgnoringDiacritics("thue o 3 gio", "Thuê ô 4 giờ"));
        assertFalse(VietnameseText.sameTextIgnoringDiacritics(
                "phi gui hang bao nhieu", "Phí gửi hàng là 15.000đ cho mỗi đơn."));
        assertFalse(VietnameseText.sameTextIgnoringDiacritics("thue tu", ""));
    }
}
