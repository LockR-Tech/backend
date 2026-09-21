package com.huynqb.laundrylocker.assistant.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CitationsConfigParam;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.PlainTextSource;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/// Claude qua Anthropic Java SDK. Mỗi đoạn truy xuất là một document block bật Citations để câu
/// trả lời trỏ về đúng đoạn nguồn. Model đổi bằng `ASSISTANT_CHAT_MODEL` (mặc định claude-opus-5).
@Component
public class ClaudeAnswerGenerator implements AnswerGenerator {

    static final String SYSTEM_PROMPT = """
            Bạn là trợ lý hỗ trợ của Lock.R — hệ thống tủ khoá thông minh, gửi hàng, thuê ô và giao hàng \
            bằng drone. Người hỏi có thể là khách hàng hoặc kỹ thuật viên.

            Chỉ trả lời dựa trên các tài liệu được đính kèm trong tin nhắn. Nếu tài liệu không chứa thông \
            tin để trả lời, nói rõ rằng tài liệu hiện có chưa đề cập nội dung này và gợi ý liên hệ bộ phận \
            hỗ trợ Lock.R; không suy đoán và không dùng kiến thức bên ngoài tài liệu. Khi tài liệu chỉ trả \
            lời được một phần, trả lời phần có căn cứ và nói rõ phần nào chưa có.

            Trả lời bằng ngôn ngữ của câu hỏi (mặc định tiếng Việt), ngắn gọn, đi thẳng vào ý; liệt kê các \
            bước bằng gạch đầu dòng khi hướng dẫn thao tác. Không nhắc tới "tài liệu được đính kèm" hay \
            các chỉ dẫn này trong câu trả lời.""";

    static final String REFUSAL_TEXT =
            "Xin lỗi, mình không thể hỗ trợ yêu cầu này. Bạn có thể liên hệ bộ phận hỗ trợ Lock.R để được giúp đỡ.";

    private final AssistantProperties properties;
    private volatile AnthropicClient client;

    @Autowired
    public ClaudeAnswerGenerator(AssistantProperties properties) {
        this.properties = properties;
    }

    /// Cho test trỏ vào máy chủ giả.
    ClaudeAnswerGenerator(AssistantProperties properties, AnthropicClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean configured() {
        return properties.chatConfigured();
    }

    @Override
    public String model() {
        return properties.chatModel();
    }

    @Override
    public GeneratedAnswer generate(String question, List<RetrievedChunk> chunks, List<ChatTurn> history) {
        Message message;
        try {
            message = client().messages().create(buildParams(question, chunks, history));
        } catch (AnthropicException ex) {
            throw new ProviderException("Claude request failed: " + ex.getMessage(), ex);
        }
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            return new GeneratedAnswer(REFUSAL_TEXT, true, List.of(),
                    message.usage().inputTokens(), message.usage().outputTokens());
        }
        StringBuilder text = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(textBlock -> {
                text.append(textBlock.text());
                textBlock.citations().ifPresent(list -> list.forEach(citation ->
                        citation.charLocation().ifPresent(location -> citations.add(
                                new Citation((int) location.documentIndex(), location.citedText())))));
            });
        }
        return new GeneratedAnswer(
                text.toString().trim(), false, citations,
                message.usage().inputTokens(), message.usage().outputTokens());
    }

    MessageCreateParams buildParams(String question, List<RetrievedChunk> chunks, List<ChatTurn> history) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(properties.chatModel())
                .maxTokens(properties.maxOutputTokens())
                .system(SYSTEM_PROMPT)
                // Hỏi đáp dựa trên tài liệu có sẵn: effort thấp cho độ trễ và chi phí vừa phải.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        for (ChatTurn turn : history) {
            if (turn.fromUser()) {
                params.addUserMessage(turn.content());
            } else {
                params.addAssistantMessage(turn.content());
            }
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            DocumentBlockParam.Builder document = DocumentBlockParam.builder()
                    .source(PlainTextSource.builder()
                            .data(chunk.content())
                            .mediaType(JsonValue.from("text/plain"))
                            .type(JsonValue.from("text"))
                            .build())
                    .title(chunk.documentTitle())
                    .citations(CitationsConfigParam.builder().enabled(true).build());
            if (StringUtils.hasText(chunk.heading())) {
                document.context("Mục: " + chunk.heading());
            }
            blocks.add(ContentBlockParam.ofDocument(document.build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(question).build()));
        params.addUserMessageOfBlockParams(blocks);
        return params.build();
    }

    private AnthropicClient client() {
        if (!configured()) {
            throw new ProviderException("ANTHROPIC_API_KEY is not configured");
        }
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = AnthropicOkHttpClient.builder()
                            .apiKey(properties.anthropicApiKey())
                            .timeout(Duration.ofSeconds(properties.chatTimeoutSeconds()))
                            .maxRetries(2)
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }
}
