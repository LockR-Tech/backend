package com.huynqb.laundrylocker.assistant.knowledge;

import com.huynqb.laundrylocker.assistant.knowledge.DocumentParser.Section;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    void detectsFormatByExtensionThenMime() {
        assertEquals(DocumentParser.Format.MARKDOWN, DocumentParser.detect("terms.MD", null));
        assertEquals(DocumentParser.Format.PDF, DocumentParser.detect("blob", "application/pdf"));
        assertEquals(DocumentParser.Format.DOCX, DocumentParser.detect("quy-trinh.docx", "application/octet-stream"));
        assertNull(DocumentParser.detect("photo.png", "image/png"));
    }

    @Test
    void markdownHeadingsBecomeSectionPaths() {
        String markdown = """
                # Điều khoản
                Mở đầu.
                ## 1. Phí
                Phí gửi 15.000đ.
                ```
                # không phải tiêu đề trong khối code
                ```
                ## 2. Hoàn tiền
                Hoàn trong 7 ngày.
                """;

        List<Section> sections = parser.parse("terms.md", null, markdown.getBytes(StandardCharsets.UTF_8));

        assertEquals(3, sections.size());
        assertEquals("Điều khoản", sections.get(0).heading());
        assertEquals("Điều khoản › 1. Phí", sections.get(1).heading());
        assertTrue(sections.get(1).text().contains("# không phải tiêu đề"));
        assertEquals("Điều khoản › 2. Hoàn tiền", sections.get(2).heading());
    }

    @Test
    void htmlKeepsOnlyOutermostBlocksAndDropsChrome() {
        String html = """
                <html><head><title>Chính sách</title><style>p{}</style></head><body>
                <nav>Menu</nav>
                <h2>Dữ liệu thu thập</h2>
                <ul><li><p>Số điện thoại</p></li><li>Email</li></ul>
                <script>alert(1)</script>
                </body></html>
                """;

        List<Section> sections = parser.parse("privacy.html", "text/html", html.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, sections.size());
        assertEquals("Dữ liệu thu thập", sections.get(0).heading());
        String text = sections.get(0).text();
        assertEquals(1, text.split("Số điện thoại", -1).length - 1);
        assertTrue(text.contains("- Email"));
        assertFalse(text.contains("Menu"));
        assertFalse(text.contains("alert"));
    }

    @Test
    void docxHeadingStylesAndParagraphsAreRead() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Quy trình bảo trì</w:t></w:r></w:p>
                <w:p><w:r><w:t xml:space="preserve">Kiểm tra </w:t></w:r><w:r><w:t>khoá điện.</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Bãi đáp</w:t></w:r></w:p>
                <w:p><w:r><w:t>Lau mặt đáp.</w:t></w:r></w:p>
                </w:body></w:document>
                """;

        List<Section> sections = parser.parse("sop.docx", null, docx(xml));

        assertEquals(2, sections.size());
        assertEquals("Quy trình bảo trì", sections.get(0).heading());
        assertEquals("Kiểm tra khoá điện.", sections.get(0).text().trim());
        assertEquals("Quy trình bảo trì › Bãi đáp", sections.get(1).heading());
    }

    @Test
    void docxWithExternalEntityIsNotResolved() throws IOException {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE w [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                <w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse("evil.docx", null, docx(xml)));
    }

    @Test
    void unsupportedFormatIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("a.png", "image/png", new byte[] {1}));
    }

    private static byte[] docx(String documentXml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
