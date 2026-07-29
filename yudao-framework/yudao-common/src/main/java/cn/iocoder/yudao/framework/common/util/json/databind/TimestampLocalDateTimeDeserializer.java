package cn.iocoder.yudao.framework.common.util.json.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * LocalDateTime 反序列化器：兼容 epoch 毫秒时间戳，以及常见日期字符串。
 * <p>
 * 历史实现仅调用 {@link JsonParser#getValueAsLong()}，在请求体为
 * {@code "2026-07-29 10:00:00"} 这类字符串时会静默得到 0，导致入库为 epoch 0。
 */
public class TimestampLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    public static final TimestampLocalDateTimeDeserializer INSTANCE = new TimestampLocalDateTimeDeserializer();

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return fromEpochMilli(p.getLongValue());
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        String text = p.getValueAsString();
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return null;
        }
        // epoch 毫秒字符串
        if (isAllDigits(text)) {
            try {
                return fromEpochMilli(Long.parseLong(text));
            } catch (NumberFormatException ex) {
                throw InvalidFormatException.from(p, "Invalid epoch millis for LocalDateTime: " + text,
                        text, LocalDateTime.class);
            }
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw InvalidFormatException.from(p,
                "Cannot deserialize value of type `java.time.LocalDateTime` from String \"" + text
                        + "\": expected epoch millis or yyyy-MM-dd HH:mm:ss",
                text, LocalDateTime.class);
    }

    private static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private static boolean isAllDigits(String text) {
        int start = text.charAt(0) == '-' ? 1 : 0;
        if (start >= text.length()) {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
