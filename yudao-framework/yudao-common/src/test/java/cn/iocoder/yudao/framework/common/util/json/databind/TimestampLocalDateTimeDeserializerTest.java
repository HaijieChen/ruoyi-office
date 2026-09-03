package cn.iocoder.yudao.framework.common.util.json.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimestampLocalDateTimeDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
        objectMapper.registerModule(module);
    }

    @Test
    void shouldDeserializeEpochMillisNumber() throws Exception {
        long epoch = LocalDateTime.of(2026, 7, 29, 10, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        LocalDateTime actual = objectMapper.readValue(String.valueOf(epoch), LocalDateTime.class);
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()), actual);
    }

    @Test
    void shouldDeserializeEpochMillisString() throws Exception {
        long epoch = LocalDateTime.of(2026, 7, 29, 10, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        LocalDateTime actual = objectMapper.readValue("\"" + epoch + "\"", LocalDateTime.class);
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()), actual);
    }

    @Test
    void shouldDeserializeCommonDateTimeStringInsteadOfZero() throws Exception {
        LocalDateTime actual = objectMapper.readValue("\"2026-07-29 10:00:00\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 7, 29, 10, 0, 0), actual);
    }

    @Test
    void shouldDeserializeIsoLocalDateTimeString() throws Exception {
        LocalDateTime actual = objectMapper.readValue("\"2026-07-29T10:00:00\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 7, 29, 10, 0, 0), actual);
    }

    @Test
    void shouldDeserializeIsoInstantWithZ() throws Exception {
        String iso = "2026-09-03T02:19:16.940Z";
        LocalDateTime actual = objectMapper.readValue("\"" + iso + "\"", LocalDateTime.class);
        assertEquals(LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault()), actual);
    }

    @Test
    void shouldRejectInvalidDateString() {
        assertThrows(Exception.class,
                () -> objectMapper.readValue("\"not-a-date\"", LocalDateTime.class));
    }

}
