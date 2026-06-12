package com.fandrops.user.api.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalLocalDateTimeDeserializerTest {

    OptionalLocalDateTimeDeserializer deserializer;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deserializer = new OptionalLocalDateTimeDeserializer();
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("JSON null → Optional.empty() (상시 배너 전환 신호)")
    void getNullValue_returnsOptionalEmpty() {
        Optional<LocalDateTime> result = deserializer.getNullValue(null);
        assertEquals(Optional.empty(), result);
    }

    @Test
    @DisplayName("ISO 날짜 문자열 → Optional.of(LocalDateTime)")
    void deserialize_validIsoString_returnsOptionalOf() throws Exception {
        try (JsonParser parser = mapper.createParser("\"2025-06-01T12:30:00\"")) {
            parser.nextToken();
            Optional<LocalDateTime> result = deserializer.deserialize(parser, mapper.getDeserializationContext());
            assertTrue(result.isPresent());
            assertEquals(LocalDateTime.of(2025, 6, 1, 12, 30, 0), result.get());
        }
    }

    @Test
    @DisplayName("초 없는 ISO 문자열(yyyy-MM-dd'T'HH:mm)도 파싱 성공")
    void deserialize_isoStringWithoutSeconds_returnsOptionalOf() throws Exception {
        try (JsonParser parser = mapper.createParser("\"2025-06-01T12:30\"")) {
            parser.nextToken();
            Optional<LocalDateTime> result = deserializer.deserialize(parser, mapper.getDeserializationContext());
            assertTrue(result.isPresent());
            assertEquals(LocalDateTime.of(2025, 6, 1, 12, 30, 0), result.get());
        }
    }
}
