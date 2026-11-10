package com.huynqb.laundrylocker.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.huynqb.laundrylocker.common.i18n.ServerText;
import org.junit.jupiter.api.Test;

class LockREmailTemplateTest {

    private static final String PICKUP_BODY = "Xin chào Vi,\n\n"
            + "Kiện hàng giao bằng drone của bạn đã nằm trong tủ Lock.R.\n\n"
            + "Mã đơn: ORD-1\n"
            + "Lấy tại: Tủ TU01 · Ô số 3\n"
            + "Mã mở ô: 468438\n"
            + "Đơn hàng được lưu trữ 24 giờ.\n\n"
            + "Nhập mã này trên màn hình tủ để mở ô và lấy hàng. Không chia sẻ mã cho người khác.\n";

    @Test
    void pickupCodeLineBecomesLargeCodeBox() {
        String html = LockREmailTemplate.render("Mã mở tủ Lock.R cho đơn ORD-1", PICKUP_BODY);

        assertThat(html)
                .contains("cid:lockr-logo", "MÃ MỞ Ô · CODE", "font-size:36px", ">468438<", "Lấy tại: Tủ TU01")
                .doesNotContain("Mã mở ô: 468438");
        // Mã đứng sau thông tin đơn, trước lời dặn.
        assertThat(html.indexOf("Lấy tại")).isLessThan(html.indexOf(">468438<"));
        assertThat(html.indexOf(">468438<")).isLessThan(html.indexOf("Nhập mã này"));
    }

    @Test
    void bodyWithoutCodeStaysPlainText() {
        String html = LockREmailTemplate.render("Thông báo", "Dòng 1\nDòng <2>");

        assertThat(html).contains("Dòng 1<br>Dòng &lt;2&gt;").doesNotContain("font-size:36px");
    }

    @Test
    void englishEmailKeepsCodeBoxWithTranslatedLabel() {
        String body = ServerText.toEnglish(PICKUP_BODY);
        String html = ServerText.htmlToEnglish(LockREmailTemplate.render("Lock.R locker code for order ORD-1", body));

        assertThat(html).contains("BOX CODE", ">468438<", "font-size:36px").doesNotContain("MÃ MỞ");
    }
}
