package com.fandrops.user.api.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * JSON null vs 키 부재를 구분하기 위한 커스텀 역직렬화기.
 * - 키 부재    → Java null        (= "변경 없음")
 * - "key": null → Optional.empty() (= "값 클리어")
 * - "key": "값" → Optional.of(값)  (= "값 변경")
 */
public class OptionalLocalDateTimeDeserializer extends StdDeserializer<Optional<LocalDateTime>> {

    public OptionalLocalDateTimeDeserializer() {
        super(Optional.class);
    }

    @Override
    public Optional<LocalDateTime> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        return Optional.of(LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /** JSON에 명시적으로 null이 왔을 때 — Optional.empty()로 "클리어" 신호 */
    @Override
    public Optional<LocalDateTime> getNullValue(DeserializationContext ctxt) {
        return Optional.empty();
    }
}
