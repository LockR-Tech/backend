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
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ChatTurn;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.GeneratedAnswer;
import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import com.huynqb.laundrylocker.assistant.provider.ProviderException;
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

/// Hỏi đáp RAG: nhúng câu hỏi → tìm đoạn tài liệu người hỏi được đọc → dưới ngưỡng thì trả lời
/// "tài liệu chưa đề cập" mà không gọi mô hình → trên ngưỡng thì Claude trả lời kèm trích dẫn.
@Slf4j
@Service
public class AssistantService {

    static final String NO_ANSWER_TEXT = "Tài liệu hiện có chưa đề cập nội dung này. Bạn có thể diễn đạt lại câu "
            + "hỏi, hoặc liên hệ bộ phận hỗ trợ Lock.R để được giúp đỡ.";

    private final ConversationRepository conversations;
    private final ChunkRepository chunks;
    private final EmbeddingProvider embeddings;
    private final AnswerGenerator generator;
    private final AssistantRules rules;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AssistantService(ConversationRepository conversations, ChunkRepository chunks, EmbeddingProvider embeddings,
                            AnswerGenerator generator, AssistantRules rules, ObjectMapper objectMapper) {
        this(conversations, chunks, embeddings, generator, rules, objectMapper, Clock.systemDefaultZone());
    }

    AssistantService(ConversationRepository conversations, ChunkRepository chunks, EmbeddingProvider embeddings,
                     AnswerGenerator generator, AssistantRules rules, ObjectMapper objectMapper, Clock clock) {
        this.conversations = conversations;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.generator = generator;
        this.rules = rules;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AssistantStatus status() {
        boolean embedding = embeddings.configured();
        boolean chat = generator.configured();
        return new AssistantStatus(rules.enabled(), embedding && chat, embedding, chat);
    }

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

        List<RetrievedChunk> relevant;
        double topScore;
        try {
            float[] query = embeddings.embed(List.of(retrievalQuery(history, question)),
                    EmbeddingProvider.InputType.QUERY).get(0);
            List<RetrievedChunk> found = chunks.search(
                    query, roles.contains(RoleSet.ADMIN) ? null : RoleSet.readerRoles(roles), rules.topK());
            topScore = found.isEmpty() ? 0 : found.get(0).score();
            double minScore = rules.minScore();
            relevant = found.stream().filter(chunk -> chunk.score() >= minScore).toList();
        } catch (ProviderException ex) {
            throw unavailable(ex);
        }

        String answerText;
        boolean refused;
        List<SourceView> sources;
        String model = null;
        Long inputTokens = null;
        Long outputTokens = null;
        if (relevant.isEmpty()) {
            answerText = NO_ANSWER_TEXT;
            refused = true;
            sources = List.of();
        } else {
            GeneratedAnswer generated;
            try {
                generated = generator.generate(question, relevant, history);
            } catch (ProviderException ex) {
                throw unavailable(ex);
            }
            answerText = generated.text().isBlank() ? NO_ANSWER_TEXT : generated.text();
            refused = generated.refused();
            sources = sources(generated, relevant);
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

    /// Mỗi trích dẫn trỏ về một đoạn đã gửi; gộp trích dẫn trùng đoạn + câu.
    static List<SourceView> sources(GeneratedAnswer generated, List<RetrievedChunk> relevant) {
        Map<String, SourceView> unique = new LinkedHashMap<>();
        for (AnswerGenerator.Citation citation : generated.citations()) {
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
