package com.fandrops.order.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.order.application.dto.ProductListItemResponse;
import com.fandrops.order.application.dto.ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCacheAdapter 단위 테스트")
class ProductCacheAdapterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private ProductCacheAdapter sut;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        sut = new ProductCacheAdapter(redisTemplate, objectMapper);
    }

    private ProductListResponse sampleResponse() {
        ProductListItemResponse item = new ProductListItemResponse(
                1L, 10L, "테스트 상품", BigDecimal.valueOf(10000), "ON_SALE", 100, 100, null);
        return new ProductListResponse(List.of(item), null);
    }

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("캐시 히트 시 Optional로 응답 반환")
        void get_cacheHit_returnsResponse() throws Exception {
            String json = objectMapper.writeValueAsString(sampleResponse());
            given(valueOps.get(anyString())).willReturn(json);

            Optional<ProductListResponse> result = sut.get("regular", null, null, 20);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().getItems().size());
        }

        @Test
        @DisplayName("캐시 미스 시 Optional.empty() 반환")
        void get_cacheMiss_returnsEmpty() {
            given(valueOps.get(anyString())).willReturn(null);

            assertTrue(sut.get("regular", null, null, 20).isEmpty());
        }

        @Test
        @DisplayName("Redis 장애 시 Optional.empty() 반환 — fail-open")
        void get_redisError_returnsEmpty() {
            given(valueOps.get(anyString())).willThrow(new RuntimeException("Redis down"));

            assertDoesNotThrow(() -> {
                Optional<ProductListResponse> result = sut.get("regular", null, null, 20);
                assertTrue(result.isEmpty());
            });
        }
    }

    @Nested
    @DisplayName("put()")
    class Put {

        @Test
        @DisplayName("응답을 JSON 직렬화 후 TTL과 함께 Redis에 저장")
        void put_serializesAndStores() {
            sut.put("regular", null, null, 20, sampleResponse());

            verify(valueOps).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("키에 type·artistId·cursor·size 포함")
        void put_keyContainsParams() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            sut.put("drops", 10L, 5L, 10, sampleResponse());

            verify(valueOps).set(keyCaptor.capture(), anyString(), any());
            String key = keyCaptor.getValue();
            assertTrue(key.contains("drops"));
            assertTrue(key.contains("10"));
            assertTrue(key.contains("5"));
        }
    }
}
