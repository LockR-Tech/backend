package com.huynqb.laundrylocker.common.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Iterator;
import java.util.Map;

/// App gửi `X-App-Language: en` ⇒ mọi chuỗi tiếng Việt trong JSON trả về (lỗi, thông báo, ghi chú…)
/// được dịch qua [ServerText] ngay trước khi ghi ra. Chỉ đụng response HTTP: tin RabbitMQ, lời gọi giữa
/// service và WebSocket vẫn tiếng Việt, dữ liệu lưu trong DB không đổi.
@RestControllerAdvice
@RequiredArgsConstructor
public class EnglishResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || !ServerText.wantsEnglish(request.getHeaders().getFirst(ServerText.LANGUAGE_HEADER))) {
            return body;
        }
        JsonNode tree = objectMapper.valueToTree(body);
        return translate(tree);
    }

    static JsonNode translate(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.textValue();
            String translated = ServerText.toEnglish(text);
            return translated.equals(text) ? node : TextNode.valueOf(translated);
        }
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode translated = translate(field.getValue());
                if (translated != field.getValue()) {
                    field.setValue(translated);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode translated = translate(array.get(i));
                if (translated != array.get(i)) {
                    array.set(i, translated);
                }
            }
        }
        return node;
    }
}
