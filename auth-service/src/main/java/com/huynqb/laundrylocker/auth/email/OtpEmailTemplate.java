package com.huynqb.laundrylocker.auth.email;

/// Thư mã OTP (đăng nhập bằng email, đặt lại mật khẩu, 2FA admin).
///
/// Nền sáng, bảng + style inline, màu đặc (không gradient, không `rgba`, không `<style>`): Gmail trên điện
/// thoại và Outlook bỏ thẻ `<style>` và gradient, còn chế độ tối của app mail tự đảo màu — mẫu nền tối cũ vì
/// thế ra chữ tối trên nền tối, không đọc được mã. Khai `color-scheme: light` để app nào tôn trọng thì giữ
/// nguyên màu; app vẫn đảo thì nền sáng đảo thành tối nhưng chữ đảo theo nên vẫn đủ tương phản.
public final class OtpEmailTemplate {

    private OtpEmailTemplate() {}

    public record Content(String subject, String html) {}

    public static Content render(String purpose, String code, int expirySeconds) {
        String[] title = title(purpose);
        String html = """
                <!doctype html>
                <html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light"></head>
                <body style="margin:0;padding:0;background:#F2F5F9;font-family:Arial,Helvetica,sans-serif;color:#172B45">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s — %s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F2F5F9;padding:28px 12px">
                    <tr><td align="center"><table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#FFFFFF;border:1px solid #E4EAF1;border-radius:16px">
                      <tr><td style="padding:20px 26px;background:#0B2545;border-radius:16px 16px 0 0"><table role="presentation" cellspacing="0" cellpadding="0"><tr>
                        <td style="vertical-align:middle"><img src="cid:lockr-logo" width="48" height="48" alt="Lock.R" style="display:block;width:48px;height:48px;border-radius:10px"></td>
                        <td style="padding-left:12px;vertical-align:middle">
                          <div style="font-size:22px;font-weight:700;color:#FFFFFF;letter-spacing:1px">Lock<span style="color:#5CC8FF">.</span>R</div>
                          <div style="margin-top:3px;font-size:11px;letter-spacing:1.5px;color:#D8E5F3">SMART LOCKER · DRONE DELIVERY</div>
                        </td>
                      </tr></table></td></tr>
                      <tr><td style="padding:28px 26px 8px">
                        <h1 style="margin:0 0 6px;font-size:22px;line-height:1.3;color:#10243E">%s</h1>
                        <div style="font-size:13px;color:#718096">%s</div>
                        <p style="margin:18px 0 0;font-size:15px;line-height:1.7;color:#344B63">Nhập mã dưới đây vào ứng dụng hoặc trang Lock.R để tiếp tục.</p>
                      </td></tr>
                      <tr><td style="padding:18px 26px">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#EEF5FC;border:2px solid #0077B6;border-radius:12px">
                          <tr><td align="center" style="padding:22px 12px">
                            <div style="font-size:12px;font-weight:700;letter-spacing:2px;color:#0077B6">MÃ XÁC THỰC · CODE</div>
                            <div style="margin-top:10px;font-family:'Courier New',Courier,monospace;font-size:36px;font-weight:700;letter-spacing:8px;color:#0B2545;white-space:nowrap">%s</div>
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td style="padding:4px 26px 24px;font-size:14px;line-height:1.7;color:#344B63">
                        Mã hết hạn sau <strong style="color:#B91C1C">%s</strong> và chỉ dùng được một lần.<br>
                        <span style="font-size:13px;color:#718096">Expires in %s · single use.</span>
                      </td></tr>
                      <tr><td style="padding:16px 26px;background:#F8FAFC;border-top:1px solid #E7ECF2;border-radius:0 0 16px 16px;font-size:12px;line-height:1.6;color:#718096">
                        Không phải bạn yêu cầu? Bỏ qua thư này và đổi mật khẩu nếu thấy đáng ngờ. Lock.R không bao giờ hỏi mã này qua điện thoại.<br>
                        <span style="color:#A0AEC0">Email tự động từ Lock.R — vui lòng không trả lời.</span>
                      </td></tr>
                    </table></td></tr>
                  </table>
                </body></html>
                """.formatted(title[0], expiryText(expirySeconds), title[0], title[1], code,
                expiryText(expirySeconds), expiryTextEn(expirySeconds));
        return new Content(title[0] + " | Lock.R", html);
    }

    /// Tiêu đề tiếng Việt và dòng tiếng Anh theo mục đích của mã.
    static String[] title(String purpose) {
        return switch (purpose == null ? "" : purpose) {
            case "EMAIL_LOGIN" -> new String[] {"Mã đăng nhập Lock.R", "Your Lock.R sign-in code"};
            case "PASSWORD_RESET" -> new String[] {"Mã đặt lại mật khẩu", "Your password reset code"};
            case "ADMIN_2FA" -> new String[] {"Mã xác thực quản trị", "Your admin verification code"};
            default -> new String[] {"Mã xác thực Lock.R", "Your Lock.R verification code"};
        };
    }

    /// "5 phút" khi chia hết cho 60, ngược lại "90 giây".
    public static String expiryText(int seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + " phút" : seconds + " giây";
    }

    /// "5 minutes" / "1 minute" / "90 seconds".
    public static String expiryTextEn(int seconds) {
        if (seconds % 60 == 0) {
            long minutes = seconds / 60L;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + " seconds";
    }
}
