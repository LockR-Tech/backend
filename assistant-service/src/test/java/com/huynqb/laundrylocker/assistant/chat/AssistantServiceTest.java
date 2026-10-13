package com.huynqb.laundrylocker.assistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.AskRequest;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.AskResponse;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.SourceView;
import com.huynqb.laundrylocker.assistant.chat.ConversationRepository.ConversationRow;
import com.huynqb.laundrylocker.assistant.chat.ConversationRepository.MessageRow;
import com.huynqb.laundrylocker.assistant.knowledge.ChunkRepository;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import com.huynqb.laundrylocker.assistant.orders.OrderContextService;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ChatTurn;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.Citation;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.GeneratedAnswer;
import com.huynqb.laundrylocker.assistant.provider.DiacriticsRestorer;
import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import com.huynqb.laundrylocker.assistant.query.QuestionPreparer;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import com.huynqb.laundrylocker.assistant.settings.AssistantSettingsCatalog;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Các nhánh của `ask`: có/không dữ liệu đơn, order-service lỗi, câu gõ không dấu.
class AssistantServiceTest {

    private static final long USER = 42L;
    private static final ContextDocument ORDERS = new ContextDocument(
            "Đơn hàng của bạn", "Dữ liệu lúc 14:05 13/10/2026 (giờ Việt Nam)", "1. Mã đơn ORD-20261012-AB12 …");
    private static final RetrievedChunk GUIDE = new RetrievedChunk(
            11, 1, "Hướng dẫn sử dụng Lock.R cho khách hàng", 0, "Gửi hàng", "Mở ứng dụng, chọn Gửi hàng.", 0.8);

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ChunkRepository chunks = mock(ChunkRepository.class);
    private final EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
    private final AnswerGenerator generator = mock(AnswerGenerator.class);
    private final DiacriticsRestorer restorer = mock(DiacriticsRestorer.class);
    private final OrderContextService orderContext = mock(OrderContextService.class);
    private final Map<String, Object> settings = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(embeddings.configured()).thenReturn(true);
        when(embeddings.embed(anyList(), any())).thenReturn(List.of(new float[1024]));
        when(generator.configured()).thenReturn(true);
        when(generator.model()).thenReturn("claude-haiku-4-5");
        when(restorer.configured()).thenReturn(true);
        when(conversations.countQuestionsSince(anyLong(), any())).thenReturn(0L);
        when(conversations.create(anyLong(), anyString())).thenReturn(5L);
        when(conversations.addMessage(anyLong(), anyString(), anyString(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(100L);
    }

    @Test
    void orderQuestionWithoutMatchingDocumentsStillAsksTheModelWithTheOrderData() {
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of());
        when(orderContext.load(eq(USER), anyString())).thenReturn(Optional.of(ORDERS));
        when(generator.generate(anyString(), anyList(), anyList(), any())).thenReturn(new GeneratedAnswer(
                "Đơn ORD-20261012-AB12 đang nằm trong ô, chưa có người lấy.", false,
                List.of(new Citation(0, "Trạng thái: Hàng đang nằm trong ô")), 500, 40));

        AskResponse response = service().ask(USER, List.of("CUSTOMER"),
                new AskRequest(null, "Đơn gửi hàng hôm qua của tôi đã có người lấy chưa?"));

        assertFalse(response.refused());
        assertEquals("Đơn ORD-20261012-AB12 đang nằm trong ô, chưa có người lấy.", response.answer());
        verify(generator).generate(eq("Đơn gửi hàng hôm qua của tôi đã có người lấy chưa?"), eq(List.of()),
                eq(List.of()), eq(ORDERS));
        // Nguồn "Đơn hàng của bạn" không có id tài liệu và không kèm bản sao dữ liệu đơn.
        assertEquals(List.of(new SourceView(0, "Đơn hàng của bạn", 0, ORDERS.context(), "")), response.sources());
        ArgumentCaptor<String> citations = ArgumentCaptor.forClass(String.class);
        verify(conversations).addMessage(eq(5L), eq(ConversationRepository.ASSISTANT), anyString(),
                citations.capture(), eq(false), any(), eq("claude-haiku-4-5"), eq(500L), eq(40L));
        assertFalse(citations.getValue().contains("ORD-20261012-AB12"));
    }

    @Test
    void orderServiceFailureWithoutDocumentsRefusesWithoutCallingTheModel() {
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of());
        when(orderContext.load(anyLong(), anyString())).thenReturn(Optional.empty());

        AskResponse response = service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, "Đơn hàng của tôi đâu?"));

        assertTrue(response.refused());
        assertEquals(AssistantService.NO_ANSWER_TEXT, response.answer());
        verify(generator, never()).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void orderServiceFailureWithDocumentsAnswersFromDocumentsOnly() {
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of(GUIDE));
        when(orderContext.load(anyLong(), anyString())).thenReturn(Optional.empty());
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Mở ứng dụng.", false, List.of(new Citation(0, "Mở ứng dụng")), 1, 1));

        AskResponse response = service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, "Đơn hàng của tôi đâu?"));

        assertFalse(response.refused());
        verify(generator).generate(anyString(), eq(List.of(GUIDE)), anyList(), isNull());
        assertEquals(1L, response.sources().get(0).documentId());
    }

    @Test
    void generalQuestionNeverLoadsOrders() {
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of(GUIDE));
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Mở ứng dụng.", false, List.of(), 1, 1));

        service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, "Làm sao để gửi hàng?"));

        verifyNoInteractions(orderContext);
        verify(generator).generate(anyString(), anyList(), anyList(), isNull());
    }

    @Test
    void orderContextSettingOffNeverLoadsOrders() {
        settings.put(AssistantSettingsCatalog.ORDER_CONTEXT_ENABLED, false);
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of());

        AskResponse response = service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, "Đơn hàng của tôi đâu?"));

        assertTrue(response.refused());
        verifyNoInteractions(orderContext);
    }

    /// "Còn hạn lấy thì sao?" không tự nói về đơn, nhưng câu trước trong hội thoại thì có.
    @Test
    void followUpToAnOrderQuestionKeepsTheOrderData() {
        when(conversations.find(9L)).thenReturn(Optional.of(new ConversationRow(
                9L, USER, "Đơn của tôi", LocalDateTime.now(), LocalDateTime.now(), 2)));
        when(conversations.lastMessages(9L, 6)).thenReturn(List.of(
                new MessageRow(1, 9, ConversationRepository.USER, "Đơn ORD-20261012-AB12 thanh toán chưa?", null,
                        false, null, null, LocalDateTime.now()),
                new MessageRow(2, 9, ConversationRepository.ASSISTANT, "Đã thanh toán.", null, false, 0.1, null,
                        LocalDateTime.now())));
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of());
        when(orderContext.load(eq(USER), anyString())).thenReturn(Optional.of(ORDERS));
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Hạn lấy là 16:12 14/10.", false, List.of(), 1, 1));

        AskResponse response = service().ask(USER, List.of("CUSTOMER"), new AskRequest(9L, "Còn hạn lấy thì sao?"));

        assertFalse(response.refused());
        ArgumentCaptor<String> codeSource = ArgumentCaptor.forClass(String.class);
        verify(orderContext).load(eq(USER), codeSource.capture());
        assertTrue(codeSource.getValue().contains("ORD-20261012-AB12"), "earlier order code is kept in focus");
        verify(generator).generate(eq("Còn hạn lấy thì sao?"), eq(List.of()),
                eq(List.of(new ChatTurn(true, "Đơn ORD-20261012-AB12 thanh toán chưa?"),
                        new ChatTurn(false, "Đã thanh toán."))), eq(ORDERS));
    }

    @Test
    void unaccentedQuestionIsRestoredForRetrievalAndTheModelButStoredAsTyped() {
        String typed = "lam sao de gui hang cho nguoi khac qua tu?";
        String restored = "Làm sao để gửi hàng cho người khác qua tủ?";
        when(restorer.restore(typed)).thenReturn(restored);
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of(GUIDE));
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Mở ứng dụng.", false, List.of(), 1, 1));

        service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, typed));

        verify(embeddings).embed(List.of(restored), EmbeddingProvider.InputType.QUERY);
        verify(generator).generate(eq(restored), anyList(), anyList(), isNull());
        verify(conversations).create(USER, typed);
        verify(conversations).addMessage(5L, ConversationRepository.USER, typed, null, false, null, null, null, null);
    }

    @Test
    void unaccentedOrderQuestionIsDetectedAfterRestoration() {
        String typed = "tu toi thue con bao lau het gio";
        when(restorer.restore(typed)).thenReturn("Tủ tôi thuê còn bao lâu hết giờ");
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of());
        when(orderContext.load(eq(USER), anyString())).thenReturn(Optional.of(ORDERS));
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Còn 2 giờ.", false, List.of(), 1, 1));

        AskResponse response = service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, typed));

        assertFalse(response.refused());
        verify(generator).generate(eq("Tủ tôi thuê còn bao lâu hết giờ"), anyList(), anyList(), eq(ORDERS));
    }

    @Test
    void restorationFailureUsesTheTypedQuestion() {
        String typed = "lam sao de gui hang cho nguoi khac qua tu?";
        when(restorer.restore(typed)).thenThrow(new RuntimeException("timeout"));
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of(GUIDE));
        when(generator.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(new GeneratedAnswer("Mở ứng dụng.", false, List.of(), 1, 1));

        service().ask(USER, List.of("CUSTOMER"), new AskRequest(null, typed));

        verify(embeddings).embed(List.of(typed), EmbeddingProvider.InputType.QUERY);
        verify(generator).generate(eq(typed), anyList(), anyList(), isNull());
    }

    @Test
    void citationIndexPastTheChunksMapsToTheOrderSourceOnlyWhenOrdersWereSent() {
        GeneratedAnswer answer = new GeneratedAnswer("x", false,
                List.of(new Citation(0, "Mở ứng dụng"), new Citation(1, "Trạng thái"), new Citation(1, "Thanh toán")),
                1, 1);

        List<SourceView> withOrders = AssistantService.sources(answer, List.of(GUIDE), ORDERS);
        List<SourceView> withoutOrders = AssistantService.sources(answer, List.of(GUIDE), null);

        assertEquals(2, withOrders.size());
        assertEquals("Đơn hàng của bạn", withOrders.get(1).documentTitle());
        assertEquals(0, withOrders.get(1).documentId());
        assertEquals(1, withoutOrders.size());
    }

    private AssistantService service() {
        AssistantRules rules = new AssistantRules(
                BusinessSettings.inMemory("assistant", new AssistantSettingsCatalog(), settings));
        return new AssistantService(conversations, chunks, embeddings, generator, rules,
                new QuestionPreparer(restorer, rules), orderContext, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-13T07:05:00Z"), ZoneOffset.UTC));
    }
}
