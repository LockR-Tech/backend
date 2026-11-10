package com.huynqb.laundrylocker.notification.channel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Branded fallback template for email notifications that only provide plain text. */
final class LockREmailTemplate {

    /// Dòng mã mở tủ trong thư chữ trơn ("Mã mở ô: 468438", hoặc bản đã dịch "Box code: 468438"): tách ra
    /// thành ô mã to giống thư OTP để người nhận đọc và gõ trên màn hình tủ cho dễ.
    private static final Pattern CODE_LINE = Pattern.compile(
            "^\\s*(Mã mở ô|Mã mở tủ|Box code|Locker code)\\s*:\\s*([A-Za-z0-9]{4,12})\\s*$");

    private LockREmailTemplate() {}

    static String render(String subject, String body) {
        String content = content(body);
        return """
                <!doctype html>
                <html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#F2F5F9;font-family:Arial,Helvetica,sans-serif;color:#172B45">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F2F5F9;padding:28px 12px">
                    <tr><td align="center"><table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#FFFFFF;border:1px solid #E4EAF1;border-radius:18px;overflow:hidden">
                      <tr><td style="padding:22px 26px;background:#0B2545;color:#FFFFFF"><table role="presentation" cellspacing="0" cellpadding="0"><tr>
                        <td><img src="cid:lockr-logo" width="48" height="48" alt="Lock.R" style="display:block;width:48px;height:48px;border-radius:10px"></td>
                        <td style="padding-left:12px"><div style="font-size:20px;font-weight:700">Lock.R</div><div style="margin-top:3px;color:#D8E5F3;font-size:11px;letter-spacing:1.5px">SMART LOCKER · DRONE DELIVERY</div></td>
                      </tr></table></td></tr>
                      <tr><td style="padding:28px;color:#344B63;font-size:15px;line-height:1.8"><h1 style="margin:0 0 20px;color:#10243E;font-size:23px;line-height:1.3">%s</h1>%s</td></tr>
                      <tr><td style="padding:18px 28px;background:#F8FAFC;border-top:1px solid #E7ECF2;color:#718096;font-size:12px;line-height:1.6">Email tự động từ Lock.R.<br><span style="color:#A0AEC0">Vui lòng không trả lời email này.</span></td></tr>
                    </table></td></tr>
                  </table>
                </body></html>
                """.formatted(escape(subject), escape(subject), content);
    }

    /// Nội dung thư: các đoạn chữ thường giữ xuống dòng; dòng mã mở tủ thành ô mã riêng.
    private static String content(String body) {
        String normalized = (body == null ? "" : body).replace("\r\n", "\n");
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            Matcher m = CODE_LINE.matcher(line);
            if (m.matches()) {
                flush(html, text);
                html.append(codeBox(codeLabel(m.group(1)), m.group(2)));
            } else {
                if (!text.isEmpty()) text.append("<br>");
                text.append(escape(line));
            }
        }
        flush(html, text);
        return html.toString();
    }

    private static void flush(StringBuilder html, StringBuilder text) {
        // Bỏ <br> thừa ở hai đầu đoạn (dòng trống quanh ô mã) để khoảng cách do ô mã quyết định.
        String paragraph = text.toString().replaceAll("^(<br>)+|(<br>)+$", "");
        if (!paragraph.isEmpty()) html.append("<div>").append(paragraph).append("</div>");
        text.setLength(0);
    }

    /// Nhãn tách riêng thành một đoạn chữ để bản tiếng Anh dịch được qua bảng server-text.
    private static String codeLabel(String label) {
        return switch (label) {
            case "Mã mở ô" -> "MÃ MỞ Ô · CODE";
            case "Mã mở tủ" -> "MÃ MỞ TỦ · CODE";
            case "Box code" -> "BOX CODE";
            default -> "LOCKER CODE";
        };
    }

    private static String codeBox(String label, String code) {
        return """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:18px 0;background:#EEF5FC;border:2px solid #0077B6;border-radius:12px">
                  <tr><td align="center" style="padding:22px 12px">
                    <div style="font-size:12px;font-weight:700;letter-spacing:2px;color:#0077B6">%s</div>
                    <div style="margin-top:10px;font-family:'Courier New',Courier,monospace;font-size:36px;font-weight:700;letter-spacing:8px;color:#0B2545;white-space:nowrap">%s</div>
                  </td></tr>
                </table>""".formatted(label, escape(code));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
