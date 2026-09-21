package com.huynqb.laundrylocker.assistant.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/// Tách tài liệu thành các mục (tiêu đề + văn bản) để chia đoạn. Hỗ trợ Markdown, văn bản thuần,
/// HTML, PDF và DOCX.
@Component
public class DocumentParser {

    public enum Format { MARKDOWN, TEXT, HTML, PDF, DOCX }

    public record Section(String heading, String text) {
    }

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final String HTML_BLOCKS = "h1,h2,h3,h4,h5,h6,p,li,pre,blockquote,dt,dd,th,td,caption";
    /// DOCX là zip: chặn file nén bất thường (zip bomb) trước khi đọc word/document.xml.
    private static final long MAX_DOCX_XML_BYTES = 50L * 1024 * 1024;

    /// Định dạng theo phần mở rộng, rồi tới MIME; null = không hỗ trợ.
    public static Format detect(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return Format.MARKDOWN;
        }
        if (name.endsWith(".txt")) {
            return Format.TEXT;
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return Format.HTML;
        }
        if (name.endsWith(".pdf")) {
            return Format.PDF;
        }
        if (name.endsWith(".docx")) {
            return Format.DOCX;
        }
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        return switch (mime) {
            case "text/markdown", "text/x-markdown" -> Format.MARKDOWN;
            case "text/plain" -> Format.TEXT;
            case "text/html" -> Format.HTML;
            case "application/pdf" -> Format.PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Format.DOCX;
            default -> null;
        };
    }

    public List<Section> parse(String fileName, String mimeType, byte[] content) {
        Format format = detect(fileName, mimeType);
        if (format == null) {
            throw new IllegalArgumentException("Định dạng tài liệu không được hỗ trợ");
        }
        List<Section> sections = switch (format) {
            case MARKDOWN -> markdown(new String(content, StandardCharsets.UTF_8));
            case TEXT -> List.of(new Section(null, new String(content, StandardCharsets.UTF_8)));
            case HTML -> html(new String(content, StandardCharsets.UTF_8));
            case PDF -> pdf(content);
            case DOCX -> docx(content);
        };
        return sections.stream().filter(section -> StringUtils.hasText(section.text())).toList();
    }

    List<Section> markdown(String text) {
        List<Section> sections = new ArrayList<>();
        String[] path = new String[6];
        StringBuilder body = new StringBuilder();
        String heading = null;
        boolean inFence = false;
        for (String line : text.split("\\r?\\n", -1)) {
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
            }
            Matcher matcher = inFence ? null : MARKDOWN_HEADING.matcher(line);
            if (matcher != null && matcher.matches()) {
                sections.add(new Section(heading, body.toString()));
                body.setLength(0);
                int level = matcher.group(1).length();
                path[level - 1] = matcher.group(2).trim();
                for (int i = level; i < path.length; i++) {
                    path[i] = null;
                }
                heading = joinPath(path);
            } else {
                body.append(line).append('\n');
            }
        }
        sections.add(new Section(heading, body.toString()));
        return sections;
    }

    List<Section> html(String html) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        document.select("script,style,nav,header,footer,noscript").remove();
        List<Section> sections = new ArrayList<>();
        String[] path = new String[6];
        String heading = document.title().isBlank() ? null : document.title().trim();
        StringBuilder body = new StringBuilder();
        for (Element element : document.body().select(HTML_BLOCKS)) {
            // Chỉ lấy khối ngoài cùng (li chứa p không bị lấy hai lần).
            if (element.parents().stream().anyMatch(parent -> parent.is(HTML_BLOCKS))) {
                continue;
            }
            String text = element.text().trim();
            if (text.isEmpty()) {
                continue;
            }
            String tag = element.tagName();
            if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
                sections.add(new Section(heading, body.toString()));
                body.setLength(0);
                int level = tag.charAt(1) - '0';
                path[level - 1] = text;
                for (int i = level; i < path.length; i++) {
                    path[i] = null;
                }
                heading = joinPath(path);
            } else {
                body.append("li".equals(tag) ? "- " : "").append(text).append("\n\n");
            }
        }
        sections.add(new Section(heading, body.toString()));
        return sections;
    }

    List<Section> pdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<Section> sections = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                sections.add(new Section("Trang " + page, stripper.getText(document)));
            }
            return sections;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không đọc được file PDF", ex);
        }
    }

    List<Section> docx(byte[] content) {
        byte[] xml = readZipEntry(content, "word/document.xml");
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        List<Section> sections = new ArrayList<>();
        String[] path = new String[6];
        String heading = null;
        StringBuilder body = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        int headingLevel = 0;
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            boolean inText = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("p".equals(name)) {
                        paragraph.setLength(0);
                        headingLevel = 0;
                    } else if ("pStyle".equals(name)) {
                        headingLevel = headingLevel(reader.getAttributeValue(null, "val"), reader);
                    } else if ("t".equals(name)) {
                        inText = true;
                    } else if ("tab".equals(name)) {
                        paragraph.append('\t');
                    } else if ("br".equals(name)) {
                        paragraph.append('\n');
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                    paragraph.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("p".equals(name)) {
                        String text = paragraph.toString().trim();
                        if (text.isEmpty()) {
                            continue;
                        }
                        if (headingLevel > 0) {
                            sections.add(new Section(heading, body.toString()));
                            body.setLength(0);
                            path[headingLevel - 1] = text;
                            for (int i = headingLevel; i < path.length; i++) {
                                path[i] = null;
                            }
                            heading = joinPath(path);
                        } else {
                            body.append(text).append("\n\n");
                        }
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw new IllegalArgumentException("Không đọc được file DOCX", ex);
        }
        sections.add(new Section(heading, body.toString()));
        return sections;
    }

    /// `Heading1`…`Heading6` (Word tiếng Anh) hoặc `Title`; trả 0 nếu là đoạn thường.
    private static int headingLevel(String style, XMLStreamReader reader) {
        if (style == null) {
            // Thuộc tính có namespace w: (w:val).
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                if ("val".equals(reader.getAttributeLocalName(i))) {
                    style = reader.getAttributeValue(i);
                }
            }
        }
        if (style == null) {
            return 0;
        }
        String normalized = style.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.equals("title")) {
            return 1;
        }
        if (normalized.startsWith("heading") && normalized.length() == 8 && Character.isDigit(normalized.charAt(7))) {
            int level = normalized.charAt(7) - '0';
            return level >= 1 && level <= 6 ? level : 0;
        }
        return 0;
    }

    private static byte[] readZipEntry(byte[] zip, String entryName) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return readLimited(input);
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không đọc được file DOCX", ex);
        }
        throw new IllegalArgumentException("File DOCX không có nội dung văn bản");
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_DOCX_XML_BYTES) {
                throw new IllegalArgumentException("Nội dung DOCX quá lớn");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String joinPath(String[] path) {
        List<String> parts = new ArrayList<>();
        for (String part : path) {
            if (part != null) {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(" › ", parts);
    }
}
