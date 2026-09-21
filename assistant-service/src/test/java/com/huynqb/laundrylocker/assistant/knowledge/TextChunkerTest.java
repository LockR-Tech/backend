package com.huynqb.laundrylocker.assistant.knowledge;

import com.huynqb.laundrylocker.assistant.knowledge.DocumentParser.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void shortSectionsStayWholeAndKeepTheirHeading() {
        List<TextChunker.Chunk> chunks = chunker.chunk(List.of(
                new Section("Phí", "Phí gửi 15.000đ.\n\nPhí thuê 5.000đ/giờ."),
                new Section("Hoàn tiền", "Hoàn trong 7 ngày.")));

        assertEquals(2, chunks.size());
        assertEquals("Phí", chunks.get(0).heading());
        assertEquals("Phí gửi 15.000đ.\n\nPhí thuê 5.000đ/giờ.", chunks.get(0).content());
        assertEquals("Hoàn tiền", chunks.get(1).heading());
    }

    @Test
    void longSectionsSplitWithinLimitAndOverlap() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            text.append("Đoạn số ").append(i).append(" mô tả một bước kiểm tra tủ khoá và ghi chú kèm theo. ");
            if (i % 5 == 4) {
                text.append("\n\n");
            }
        }

        List<TextChunker.Chunk> chunks = chunker.chunk(List.of(new Section("Quy trình", text.toString())));

        assertTrue(chunks.size() > 1);
        for (TextChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content().length() <= TextChunker.MAX_CHARS, "chunk too long: " + chunk.content().length());
        }
        String previousTail = chunks.get(0).content().substring(chunks.get(0).content().length() - 50);
        assertTrue(chunks.get(1).content().contains(previousTail.trim()), "next chunk repeats the previous tail");
    }

    @Test
    void oneHugeParagraphWithoutSpacesIsHardSplit() {
        String blob = "x".repeat(5000);

        List<TextChunker.Chunk> chunks = chunker.chunk(List.of(new Section(null, blob)));

        assertTrue(chunks.size() >= 3);
        chunks.forEach(chunk -> assertTrue(chunk.content().length() <= TextChunker.MAX_CHARS));
    }

    @Test
    void blankSectionsProduceNothing() {
        assertTrue(chunker.chunk(List.of(new Section("Trống", "  \n\n \t "))).isEmpty());
    }
}
