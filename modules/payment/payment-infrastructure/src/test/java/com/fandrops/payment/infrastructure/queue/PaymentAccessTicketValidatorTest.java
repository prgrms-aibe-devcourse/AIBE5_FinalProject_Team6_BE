package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.AccessTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentAccessTicketValidatorTest {

    @Mock
    private AccessTicketRepository accessTicketRepository;

    private PaymentAccessTicketValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentAccessTicketValidator(accessTicketRepository);
    }

    @Test
    @DisplayName("isValid — repository가 true 반환하면 true")
    void isValid_returnsTrue_whenRepositoryReturnsTrue() {
        given(accessTicketRepository.isValid("token", 1L, 2L)).willReturn(true);

        assertThat(validator.isValid("token", 1L, 2L)).isTrue();
    }

    @Test
    @DisplayName("isValid — repository가 false 반환하면 false")
    void isValid_returnsFalse_whenRepositoryReturnsFalse() {
        given(accessTicketRepository.isValid("token", 1L, 2L)).willReturn(false);

        assertThat(validator.isValid("token", 1L, 2L)).isFalse();
    }
}
