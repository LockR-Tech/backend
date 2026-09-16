package com.huynqb.laundrylocker.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    @DisplayName("số nội địa bắt đầu bằng 0 đổi sang +84")
    void convertsLocalNumber() {
        assertThat(PhoneNumbers.toE164("0901234567")).isEqualTo("+84901234567");
    }

    @Test
    @DisplayName("bỏ khoảng trắng, dấu chấm, gạch ngang và ngoặc người dùng gõ")
    void stripsSeparators() {
        assertThat(PhoneNumbers.toE164(" 090 123-4567 ")).isEqualTo("+84901234567");
        assertThat(PhoneNumbers.toE164("(090) 123.4567")).isEqualTo("+84901234567");
    }

    @Test
    @DisplayName("số đã ở dạng E.164 giữ nguyên, kể cả số nước ngoài")
    void keepsE164() {
        assertThat(PhoneNumbers.toE164("+84901234567")).isEqualTo("+84901234567");
        assertThat(PhoneNumbers.toE164("+14155552671")).isEqualTo("+14155552671");
    }

    @Test
    @DisplayName("mã quốc gia viết không dấu cộng vẫn ra đúng một kết quả")
    void normalisesCountryCodeWithoutPlus() {
        assertThat(PhoneNumbers.toE164("84901234567")).isEqualTo("+84901234567");
        assertThat(PhoneNumbers.toE164("0084901234567")).isEqualTo("+84901234567");
    }

    @Test
    @DisplayName("chuỗi rỗng hoặc không có chữ số nào trả null")
    void returnsNullWhenUnusable() {
        assertThat(PhoneNumbers.toE164(null)).isNull();
        assertThat(PhoneNumbers.toE164("   ")).isNull();
        assertThat(PhoneNumbers.toE164("không-phải-số")).isNull();
    }
}
