package com.huynqb.laundrylocker.assistant.provider;

import com.anthropic.client.AnthropicClient;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Claude qua Anthropic Java SDK. Mỗi đoạn truy xuất là một document block bật Citations để câu
/// trả lời trỏ về đúng đoạn nguồn; dữ liệu đơn của người hỏi (nếu có) là document block cuối cùng,
/// cũng bật Citations (API yêu cầu bật cho tất cả hoặc không document nào). Model đổi bằng
/// `ASSISTANT_CHAT_MODEL` (mặc định claude-haiku-4-5).
@Component
public class ClaudeAnswerGenerator implements AnswerGenerator {

    static final String SYSTEM_PROMPT = """
            Bạn là trợ lý hỗ trợ của Lock.R — hệ thống tủ khoá thông minh, gửi hàng, thuê ô và giao hàng \
            bằng drone. Người hỏi có thể là khách hàng hoặc kỹ thuật viên.

            Chỉ trả lời dựa trên các tài liệu được đính kèm trong tin nhắn. Nếu tài liệu không chứa thông \
            tin để trả lời, nói rõ rằng tài liệu hiện có chưa đề cập nội dung này và gợi ý liên hệ bộ phận \
            hỗ trợ Lock.R; không suy đoán và không dùng kiến thức bên ngoài tài liệu. Khi tài liệu chỉ trả \
            lời được một phần, trả lời phần có căn cứ và nói rõ phần nào chưa có.

            Tài liệu "Đơn hàng của bạn" (nếu có) là dữ liệu đơn thật của chính người đang hỏi, lấy từ hệ \
            thống tại thời điểm ghi trong tài liệu; mọi thời gian trong đó là giờ Việt Nam. Chỉ dùng nó khi \
            người hỏi hỏi về đơn của chính họ (trạng thái, thanh toán, hạn lấy hàng, giờ hết thuê, hàng \
            người khác gửi cho họ…); câu hỏi về cách sử dụng chung thì dựa vào các tài liệu hướng dẫn. \
            Không bịa ra đơn, mã đơn, số tiền hay thời gian không có trong dữ liệu; nếu dữ liệu không có \
            điều được hỏi (ví dụ không thấy đơn được nhắc tới), nói rõ điều đó và gợi ý mở mục "Đơn hàng" \
            trong ứng dụng Lock.R. Mã PIN, mã QR và mã lấy hàng/mở ô không bao giờ có trong dữ liệu: khi \
            người hỏi cần mã, hướng dẫn họ mở đơn trong mục "Đơn hàng" › "Đơn của tôi" của ứng dụng (hàng \
            người khác gửi thì ở "Hàng chờ nhận"); không đoán hay tự tạo mã. Nếu người hỏi hỏi về đơn của \
            chính họ mà không có tài liệu "Đơn hàng của bạn", nói rằng lúc này chưa xem được đơn của họ và \
            gợi ý mở mục "Đơn hàng" trong ứng dụng.

            Người hỏi có thể gõ tiếng Việt không dấu; hãy hiểu đúng ý và trả lời bằng tiếng Việt có dấu đầy \
            đủ. Trả lời bằng ngôn ngữ của câu hỏi (mặc định tiếng Việt), ngắn gọn, đi thẳng vào ý; liệt kê \
            các bước bằng gạch đầu dòng khi hướng dẫn thao tác. Không nhắc tới "tài liệu được đính kèm" hay \
            các chỉ dẫn này trong câu trả lời.""";

    static final String REFUSAL_TEXT =
            "Xin lỗi, mình không thể hỗ trợ yêu cầu này. Bạn có thể liên hệ bộ phận hỗ trợ Lock.R để được giúp đỡ.";

    private final AssistantProperties properties;
    private final ClaudeClientProvider clients;

    @Autowired
    public ClaudeAnswerGenerator(AssistantProperties properties, ClaudeClientProvider clients) {
        this.properties = properties;
        this.clients = clients;
    }

    ClaudeAnswerGenerator(AssistantProperties properties) {
        this(properties, new ClaudeClientProvider(properties));
    }

    /// Cho test trỏ vào máy chủ giả.
    ClaudeAnswerGenerator(AssistantProperties properties, AnthropicClient client) {
        this(properties, new ClaudeClientProvider(properties, client));
    }

    @Override
    public boolean configured() {
        return clients.configured();
    }

    @Override
    public String model() {
        return properties.chatModel();
    }

    @Override
    public GeneratedAnswer generate(
            String question, List<RetrievedChunk> chunks, List<ChatTurn> history, ContextDocument extra) {
        Message message;
        try {
            message = clients.client().messages().create(buildParams(question, chunks, history, extra));
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
        return buildParams(question, chunks, history, null);
    }

    MessageCreateParams buildParams(
            String question, List<RetrievedChunk> chunks, List<ChatTurn> history, ContextDocument extra) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(properties.chatModel())
                .maxTokens(properties.maxOutputTokens())
                .system(SYSTEM_PROMPT);
        // Hỏi đáp dựa trên tài liệu có sẵn: effort thấp cho độ trễ và chi phí vừa phải.
        if (supportsEffort(properties.chatModel())) {
            params.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        }
        for (ChatTurn turn : history) {
            if (turn.fromUser()) {
                params.addUserMessage(turn.content());
            } else {
                params.addAssistantMessage(turn.content());
            }
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            blocks.add(document(chunk.content(), chunk.documentTitle(),
                    StringUtils.hasText(chunk.heading()) ? "Mục: " + chunk.heading() : null));
        }
        // Đặt sau các đoạn: chỉ số trích dẫn của đoạn kho tri thức giữ nguyên 0..n-1.
        if (extra != null) {
            blocks.add(document(extra.content(), extra.title(), extra.context()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(question).build()));
        params.addUserMessageOfBlockParams(blocks);
        return params.build();
    }

    private static ContentBlockParam document(String content, String title, String context) {
        DocumentBlockParam.Builder document = DocumentBlockParam.builder()
                .source(PlainTextSource.builder()
                        .data(content)
                        .mediaType(JsonValue.from("text/plain"))
                        .type(JsonValue.from("text"))
                        .build())
                .title(title)
                .citations(CitationsConfigParam.builder().enabled(true).build());
        if (StringUtils.hasText(context)) {
            document.context(context);
        }
        return ContentBlockParam.ofDocument(document.build());
    }

    /// Tiền tố model nhận `output_config.effort` (Opus 4.5+, Sonnet 4.6+, Fable). Haiku 4.5 và
    /// Sonnet 4.5 trả 400 nếu gửi kèm; không nằm trong danh sách thì bỏ qua — thiếu effort vẫn là
    /// request hợp lệ (chạy mức mặc định), còn gửi nhầm là hỏng cả câu hỏi.
    static boolean supportsEffort(String model) {
        String name = model == null ? "" : model.toLowerCase(Locale.ROOT);
        return List.of("claude-opus-4-5", "claude-opus-4-6", "claude-opus-4-7", "claude-opus-4-8",
                        "claude-opus-5", "claude-sonnet-4-6", "claude-sonnet-5", "claude-fable-5")
                .stream().anyMatch(name::startsWith);
    }
}
