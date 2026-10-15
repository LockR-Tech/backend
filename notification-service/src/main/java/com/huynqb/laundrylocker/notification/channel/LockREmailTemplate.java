package com.huynqb.laundrylocker.notification.channel;

/** Branded fallback template for email notifications that only provide plain text. */
final class LockREmailTemplate {

    private LockREmailTemplate() {}

    static String render(String subject, String body) {
        String paragraphs = escape(body).replace("\r\n", "\n").replace("\n", "<br>");
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
                      <tr><td style="padding:28px;color:#344B63;font-size:15px;line-height:1.8"><h1 style="margin:0 0 20px;color:#10243E;font-size:23px;line-height:1.3">%s</h1><div>%s</div></td></tr>
                      <tr><td style="padding:18px 28px;background:#F8FAFC;border-top:1px solid #E7ECF2;color:#718096;font-size:12px;line-height:1.6">Email tự động từ Lock.R.<br><span style="color:#A0AEC0">Vui lòng không trả lời email này.</span></td></tr>
                    </table></td></tr>
                  </table>
                </body></html>
                """.formatted(escape(subject), escape(subject), paragraphs);
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
