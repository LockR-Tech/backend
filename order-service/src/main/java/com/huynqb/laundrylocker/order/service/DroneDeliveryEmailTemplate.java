package com.huynqb.laundrylocker.order.service;

import java.util.List;

/** Branded, mobile-friendly HTML emails sent at the important drone delivery milestones. */
final class DroneDeliveryEmailTemplate {

    private DroneDeliveryEmailTemplate() {}

    static String launched(
            String recipientName,
            String orderCode,
            String headline,
            String introduction,
            List<Detail> details,
            String footer) {
        String rows = details.stream().map(DroneDeliveryEmailTemplate::row).reduce("", String::concat);
        return render(
                recipientName,
                orderCode,
                "DRONE ĐANG GIAO",
                headline,
                introduction,
                rows,
                footer,
                "#0B2545",
                "#20A486");
    }

    static String failed(
            String recipientName,
            String orderCode,
            String introduction,
            List<Detail> details,
            String footer) {
        String rows = details.stream().map(DroneDeliveryEmailTemplate::row).reduce("", String::concat);
        return render(
                recipientName,
                orderCode,
                "CẬP NHẬT QUAN TRỌNG",
                "Đơn hàng chưa thể giao hoàn tất",
                introduction,
                rows,
                footer,
                "#7C2D12",
                "#EA580C");
    }

    private static String render(
            String recipientName,
            String orderCode,
            String badge,
            String headline,
            String introduction,
            String rows,
            String footer,
            String headerColor,
            String accentColor) {
        return """
                <!doctype html>
                <html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#F2F5F9;font-family:Arial,Helvetica,sans-serif;color:#172B45">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s · Đơn %s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F2F5F9;padding:28px 12px">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#FFFFFF;border-radius:18px;overflow:hidden;border:1px solid #E4EAF1">
                        <tr><td style="padding:22px 26px;background:%s;color:#FFFFFF">
                          <table role="presentation" cellspacing="0" cellpadding="0"><tr>
                            <td style="vertical-align:middle"><img src="cid:lockr-logo" width="48" height="48" alt="Lock.R" style="display:block;width:48px;height:48px;border-radius:10px"></td>
                            <td style="padding-left:12px;vertical-align:middle"><div style="font-size:20px;font-weight:700;letter-spacing:.3px">Lock.R</div><div style="margin-top:3px;color:#D8E5F3;font-size:11px;letter-spacing:1.5px">SMART LOCKER · DRONE DELIVERY</div></td>
                          </tr></table>
                        </td></tr>
                        <tr><td style="padding:30px 28px 12px">
                          <span style="display:inline-block;padding:7px 11px;border-radius:20px;background:%s;color:#FFFFFF;font-size:11px;font-weight:700;letter-spacing:.5px">%s</span>
                          <h1 style="margin:18px 0 10px;color:#10243E;font-size:25px;line-height:1.3">%s</h1>
                          <p style="margin:0;color:#4B5F77;font-size:15px;line-height:1.7">Xin chào %s,</p>
                          <p style="margin:10px 0 22px;color:#4B5F77;font-size:15px;line-height:1.7">%s</p>
                          <div style="padding:14px 16px;border:1px solid #E2E8F0;border-radius:12px;background:#F8FAFC;color:#476078;font-size:13px">MÃ ĐƠN HÀNG <strong style="display:block;margin-top:5px;color:#10243E;font-size:17px">%s</strong></div>
                        </td></tr>
                        <tr><td style="padding:10px 28px 24px">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border:1px solid #E7ECF2;border-radius:12px">%s</table>
                          <p style="margin:20px 0 0;color:#4B5F77;font-size:14px;line-height:1.7">%s</p>
                        </td></tr>
                        <tr><td style="padding:18px 28px;background:#F8FAFC;border-top:1px solid #E7ECF2;color:#718096;font-size:12px;line-height:1.6">Email tự động từ Lock.R. Bạn có thể xem trạng thái và hành trình đơn trong ứng dụng Lock.R.<br><span style="color:#A0AEC0">Vui lòng không trả lời email này.</span></td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                escape(headline),
                escape(orderCode),
                headerColor,
                accentColor,
                escape(badge),
                escape(headline),
                escape(recipientName),
                escape(introduction),
                escape(orderCode),
                rows,
                escape(footer));
    }

    private static String row(Detail detail) {
        return """
                <tr><td style="padding:13px 15px;border-bottom:1px solid #E7ECF2;color:#718096;font-size:13px;width:34%%">%s</td>
                    <td style="padding:13px 15px;border-bottom:1px solid #E7ECF2;color:#21364F;font-size:14px;font-weight:600;line-height:1.5">%s</td></tr>
                """.formatted(escape(detail.label()), escape(detail.value()));
    }

    private static String escape(String value) {
        if (value == null) return "—";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    record Detail(String label, String value) {}
}
