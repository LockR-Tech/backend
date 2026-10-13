package com.huynqb.laundrylocker.assistant.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.*;
import com.huynqb.laundrylocker.assistant.chat.ConversationRepository.ConversationRow;
import com.huynqb.laundrylocker.assistant.chat.ConversationRepository.MessageRow;
import com.huynqb.laundrylocker.assistant.knowledge.ChunkRepository;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import com.huynqb.laundrylocker.assistant.knowledge.RoleSet;
import com.huynqb.laundrylocker.assistant.orders.OrderContextService;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ChatTurn;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.GeneratedAnswer;
import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import com.huynqb.laundrylocker.assistant.provider.ProviderException;
import com.huynqb.laundrylocker.assistant.query.OrderIntent;
import com.huynqb.laundrylocker.assistant.query.QuestionPreparer;
import com.huynqb.laundrylocker.assistant.query.QuestionPreparer.PreparedQuestion;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Hỏi đáp RAG: chuẩn bị câu hỏi (thêm dấu nếu gõ không dấu) → nhúng → tìm đoạn tài liệu người hỏi
/// được đọc → nếu câu hỏi nói về đơn của chính người hỏi thì kèm dữ liệu đơn thật của họ → không có
/// đoạn nào đạt ngưỡng và không có dữ liệu đơn thì trả lời "tài liệu chưa đề cập" mà không gọi mô
/// hình → ngược lại Claude trả lời kèm trích dẫn.
@Slf4j
@Service
public class AssistantService {

    static final String NO_ANSWER_TEXT = "Tài liệu hiện có chưa đề cập nội dung này. Bạn có thể diễn đạt lại câu "
            + "hỏi, hoặc liên hệ bộ phận hỗ trợ Lock.R để được giúp đỡ.";
    /// Nguồn "Đơn hàng của bạn" không thuộc kho tri thức ⇒ documentId/chunkId = 0 (id thật bắt đầu từ 1).
    static final long ORDER_SOURCE_ID = 0;

    private final ConversationRepository conversations;
    private final ChunkRepository chunks;
    private final EmbeddingProvider embeddings;
    private final AnswerGenerator generator;
    private final AssistantRules rules;
    private final QuestionPreparer questions;
    private final OrderContextService orderContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AssistantService(ConversationRepository conversations, ChunkRepository chunks, EmbeddingProvider embeddings,
                            AnswerGenerator generator, AssistantRules rules, QuestionPreparer questions,
                            OrderContextService orderContext, ObjectMapper objectMapper) {
        this(conversations, chunks, embeddings, generator, rules, questions, orderContext, objectMapper,
                Clock.systemDefaultZone());
    }

    AssistantService(ConversationRepository conversations, ChunkRepository chunks, EmbeddingProvider embeddings,
                     AnswerGenerator generator, AssistantRules rules, QuestionPreparer questions,
                     OrderContextService orderContext, ObjectMapper objectMapper, Clock clock) {
        this.conversations = conversations;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.generator = generator;
        this.rules = rules;
        this.questions = questions;
        this.orderContext = orderContext;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AssistantStatus status() {
        boolean embedding = embeddings.configured();
        boolean chat = generator.configured();
        return new AssistantStatus(rules.enabled(), embedding && chat, embedding, chat);
    }

    /// `userId` là X-User-Id gateway gắn sau khi kiểm JWT — dữ liệu đơn chỉ lấy theo id này.
    public AskResponse ask(long userId, List<String> roles, AskRequest request) {
        if (!rules.enabled()) {
            throw new BusinessException(
                    "ASSISTANT_DISABLED", "Trợ lý hỏi đáp đang tạm tắt", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!embeddings.configured() || !generator.configured()) {
            throw new BusinessException(
                    "ASSISTANT_NOT_CONFIGURED", "Trợ lý hỏi đáp chưa được cấu hình trên máy chủ",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        String question = request.question().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        int limit = rules.questionsPerHour();
        if (conversations.countQuestionsSince(userId, now.minusHours(1)) >= limit) {
            throw new BusinessException(
                    "ASSISTANT_RATE_LIMITED",
                    "Bạn đã hỏi " + limit + " câu trong một giờ qua, vui lòng thử lại sau",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        ConversationRow conversation = request.conversationId() == null
                ? null
                : ownedConversation(request.conversationId(), userId);
        List<ChatTurn> history = conversation == null ? List.of() : history(conversation.id(), rules.historyTurns());

        // Câu gõ không dấu ⇒ bản đã thêm dấu dùng để tìm tài liệu, nhận diện ý định và gửi mô hình;
        // hội thoại vẫn lưu nguyên câu người dùng gõ.
        PreparedQuestion prepared = questions.prepare(question);
        String effective = prepared.text();

        List<RetrievedChunk> relevant;
        double topScore;
        try {
            float[] query = embeddings.embed(List.of(retrievalQuery(history, effective)),
                    EmbeddingProvider.InputType.QUERY).get(0);
            List<RetrievedChunk> found = chunks.search(
                    query, roles.contains(RoleSet.ADMIN) ? null : RoleSet.readerRoles(roles), rules.topK());
            topScore = found.isEmpty() ? 0 : found.get(0).score();
            double minScore = rules.minScore();
            relevant = found.stream().filter(chunk -> chunk.score() >= minScore).toList();
        } catch (ProviderException ex) {
            throw unavailable(ex);
        }

        ContextDocument orders = orderContext(userId, question, effective, history);

        String answerText;
        boolean refused;
        List<SourceView> sources;
        String model = null;
        Long inputTokens = null;
        Long outputTokens = null;
        if (relevant.isEmpty() && orders == null) {
            answerText = NO_ANSWER_TEXT;
            refused = true;
            sources = List.of();
        } else {
            // Có dữ liệu đơn thì luôn để mô hình quyết định, kể cả khi không đoạn tài liệu nào đạt ngưỡng.
            GeneratedAnswer generated;
            try {
                generated = generator.generate(effective, relevant, history, orders);
            } catch (ProviderException ex) {
                throw unavailable(ex);
            }
            answerText = generated.text().isBlank() ? NO_ANSWER_TEXT : generated.text();
            refused = generated.refused();
            sources = sources(generated, relevant, orders);
            model = generator.model();
            inputTokens = generated.inputTokens();
            outputTokens = generated.outputTokens();
        }

        long conversationId = conversation != null ? conversation.id() : conversations.create(userId, title(question));
        conversations.addMessage(conversationId, ConversationRepository.USER, question, null, false, null, null,
                null, null);
        long messageId = conversations.addMessage(conversationId, ConversationRepository.ASSISTANT, answerText,
                toJson(sources), refused, topScore, model, inputTokens, outputTokens);
        conversations.touch(conversationId);
        return new AskResponse(conversationId, messageId, answerText, refused, sources, now);
    }

    public List<ConversationView> myConversations(long userId) {
        return conversations.listByUser(userId, 50).stream().map(AssistantService::view).toList();
    }

    public ConversationDetail myConversation(long id, long userId) {
        return detail(ownedConversation(id, userId));
    }

    public void deleteConversation(long id, long userId) {
        if (!conversations.delete(id, userId)) {
            throw new NotFoundException("Conversation", id);
        }
    }

    /// Admin xem hội thoại để kiểm tra chất lượng trả lời.
    public List<ConversationView> recentConversations(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return conversations.listRecent(userId, safeSize, Math.max(page, 0) * safeSize).stream()
                .map(AssistantService::view).toList();
    }

    public ConversationDetail conversation(long id) {
        return detail(conversations.find(id).orElseThrow(() -> new NotFoundException("Conversation", id)));
    }

    /// Dữ liệu đơn của người hỏi khi câu này — hoặc một câu trước trong hội thoại đang gửi kèm — nói
    /// về đơn của họ. Không tải được ⇒ null (trả lời như chưa có tính năng này).
    private ContextDocument orderContext(long userId, String question, String effective, List<ChatTurn> history) {
        if (!rules.orderContextEnabled()) {
            return null;
        }
        boolean asked = OrderIntent.isOrderQuestion(effective) || OrderIntent.isOrderQuestion(question)
                || history.stream().anyMatch(turn -> turn.fromUser() && OrderIntent.isOrderQuestion(turn.content()));
        if (!asked) {
            return null;
        }
        // Mã đơn nhắc ở câu này hoặc câu trước được ưu tiên đưa vào tài liệu.
        StringBuilder codeSource = new StringBuilder(question);
        history.stream().filter(ChatTurn::fromUser).forEach(turn -> codeSource.append('\n').append(turn.content()));
        return orderContext.load(userId, codeSource.toString()).orElse(null);
    }

    private ConversationRow ownedConversation(long id, long userId) {
        return conversations.find(id)
                .filter(row -> row.userId() == userId)
                .orElseThrow(() -> new NotFoundException("Conversation", id));
    }

    private ConversationDetail detail(ConversationRow row) {
        List<MessageView> messages = conversations.messages(row.id()).stream()
                .map(message -> new MessageView(
                        message.id(), message.role(), message.content(), message.refused(),
                        fromJson(message.citationsJson()), message.topScore(), message.createdAt()))
                .toList();
        return new ConversationDetail(view(row), messages);
    }

    /// `turns` cặp hỏi–đáp gần nhất, bắt đầu bằng câu hỏi để Claude nhận đúng thứ tự vai.
    private List<ChatTurn> history(long conversationId, int turns) {
        if (turns <= 0) {
            return List.of();
        }
        List<MessageRow> rows = conversations.lastMessages(conversationId, turns * 2);
        List<ChatTurn> history = new ArrayList<>();
        for (MessageRow row : rows) {
            boolean fromUser = ConversationRepository.USER.equals(row.role());
            boolean expectUser = history.size() % 2 == 0;
            if (fromUser != expectUser) {
                continue;
            }
            history.add(new ChatTurn(fromUser, row.content()));
        }
        if (history.size() % 2 == 1) {
            history.remove(history.size() - 1);
        }
        return history;
    }

    /// Câu hỏi nối tiếp ("còn phí thì sao?") được tìm cùng câu hỏi trước để không lạc ngữ cảnh.
    static String retrievalQuery(List<ChatTurn> history, String question) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).fromUser()) {
                return history.get(i).content() + "\n" + question;
            }
        }
        return question;
    }

    /// Mỗi trích dẫn trỏ về một đoạn đã gửi; gộp trích dẫn trùng đoạn + câu. Trích dẫn vào dữ liệu đơn
    /// gộp thành một nguồn "Đơn hàng của bạn" không kèm câu trích — câu trả lời đã nói đủ, và không lưu
    /// lại bản sao dữ liệu đơn trong lịch sử hội thoại.
    static List<SourceView> sources(GeneratedAnswer generated, List<RetrievedChunk> relevant, ContextDocument orders) {
        Map<String, SourceView> unique = new LinkedHashMap<>();
        for (AnswerGenerator.Citation citation : generated.citations()) {
            if (orders != null && citation.chunkIndex() == relevant.size()) {
                unique.putIfAbsent("orders", new SourceView(
                        ORDER_SOURCE_ID, orders.title(), ORDER_SOURCE_ID, orders.context(), ""));
                continue;
            }
            if (citation.chunkIndex() < 0 || citation.chunkIndex() >= relevant.size()) {
                continue;
            }
            RetrievedChunk chunk = relevant.get(citation.chunkIndex());
            String cited = citation.citedText() == null ? "" : citation.citedText().trim();
            unique.putIfAbsent(chunk.chunkId() + "|" + cited, new SourceView(
                    chunk.documentId(), chunk.documentTitle(), chunk.chunkId(), chunk.heading(), cited));
        }
        return List.copyOf(unique.values());
    }

    private static String title(String question) {
        String oneLine = question.replaceAll("\\s+", " ");
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 77) + "...";
    }

    private static ConversationView view(ConversationRow row) {
        return new ConversationView(
                row.id(), row.userId(), row.title(), row.messageCount(), row.createdAt(), row.updatedAt());
    }

    private static BusinessException unavailable(ProviderException ex) {
        log.warn("Assistant provider failure: {}", ex.getMessage());
        return new BusinessException(
                "ASSISTANT_UNAVAILABLE", "Trợ lý đang bận hoặc gặp sự cố, vui lòng thử lại sau",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String toJson(List<SourceView> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise sources", ex);
        }
    }

    private List<SourceView> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("Unreadable citations JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}
