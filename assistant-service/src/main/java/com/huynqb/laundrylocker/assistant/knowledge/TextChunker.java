package com.huynqb.laundrylocker.assistant.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/// Chia mục thành đoạn ~2000 ký tự, cắt theo đoạn văn → câu → cứng; đoạn sau lặp lại ~200 ký tự
/// cuối của đoạn trước để câu trả lời không mất ngữ cảnh ở ranh giới. Không đoạn nào vắt qua hai mục.
@Component
public class TextChunker {

    public static final int MAX_CHARS = 2000;
    public static final int OVERLAP_CHARS = 200;

    public record Chunk(String heading, String content) {
    }

    public List<Chunk> chunk(List<DocumentParser.Section> sections) {
        List<Chunk> chunks = new ArrayList<>();
        for (DocumentParser.Section section : sections) {
            chunkSection(section, chunks);
        }
        return chunks;
    }

    private void chunkSection(DocumentParser.Section section, List<Chunk> out) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : section.text().split("\\n\\s*\\n")) {
            String trimmed = collapse(paragraph);
            if (!trimmed.isEmpty()) {
                pieces.addAll(splitLong(trimmed));
            }
        }
        StringBuilder current = new StringBuilder();
        boolean hasFreshText = false;
        for (String piece : pieces) {
            int separator = current.isEmpty() ? 0 : 2;
            if (hasFreshText && current.length() + separator + piece.length() > MAX_CHARS) {
                String content = current.toString();
                out.add(new Chunk(section.heading(), content));
                current.setLength(0);
                current.append(overlapTail(content));
                hasFreshText = false;
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(piece);
            hasFreshText = true;
        }
        if (hasFreshText) {
            out.add(new Chunk(section.heading(), current.toString()));
        }
    }

    /// Đoạn văn dài hơn giới hạn: tách theo câu, câu vẫn quá dài thì cắt cứng ở khoảng trắng.
    private static List<String> splitLong(String paragraph) {
        int limit = MAX_CHARS - OVERLAP_CHARS - 2;
        if (paragraph.length() <= limit) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?…;])\\s+")) {
            for (String piece : hardSplit(sentence, limit)) {
                if (!current.isEmpty() && current.length() + 1 + piece.length() > limit) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(piece);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static List<String> hardSplit(String text, int limit) {
        List<String> parts = new ArrayList<>();
        String rest = text;
        while (rest.length() > limit) {
            int cut = rest.lastIndexOf(' ', limit);
            if (cut < limit / 2) {
                cut = limit;
            }
            parts.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    /// Tối đa OVERLAP_CHARS ký tự cuối, bắt đầu ở đầu một từ.
    private static String overlapTail(String content) {
        if (content.length() <= OVERLAP_CHARS) {
            return content;
        }
        String tail = content.substring(content.length() - OVERLAP_CHARS);
        int space = tail.indexOf(' ');
        return space >= 0 && space < tail.length() - 1 ? tail.substring(space + 1) : tail;
    }

    /// Gộp khoảng trắng thừa trong dòng nhưng giữ xuống dòng (danh sách, bảng).
    private static String collapse(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
            if (!trimmed.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(trimmed);
            }
        }
        return out.toString();
    }
}
