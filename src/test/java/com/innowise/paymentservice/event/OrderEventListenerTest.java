package com.innowise.paymentservice.event;

import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private PaymentService paymentService;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(paymentService, VALIDATOR);
    }

    @Test
    @DisplayName("listen: maps CreateOrderEvent to PaymentRequest and delegates to PaymentService.create")
    void listen_delegatesToPaymentServiceCreate() {
        CreateOrderEvent event = new CreateOrderEvent("order-1", "user-1", new BigDecimal("42.50"));

        listener.listen(event);

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService).create(captor.capture(), eq("user-1"));
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().paymentAmount()).isEqualByComparingTo("42.50");
    }

    @Test
    @DisplayName("listen: an event with a blank orderId fails validation and is never delegated to PaymentService")
    void listen_whenOrderIdBlank_throwsConstraintViolationExceptionWithoutCreatingPayment() {
        CreateOrderEvent event = new CreateOrderEvent("", "user-1", new BigDecimal("42.50"));

        assertThatThrownBy(() -> listener.listen(event))
                .isInstanceOf(ConstraintViolationException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("listen: an event with a non-positive amount fails validation and is never delegated to PaymentService")
    void listen_whenAmountNotPositive_throwsConstraintViolationExceptionWithoutCreatingPayment() {
        CreateOrderEvent event = new CreateOrderEvent("order-1", "user-1", BigDecimal.ZERO);

        assertThatThrownBy(() -> listener.listen(event))
                .isInstanceOf(ConstraintViolationException.class);

        verifyNoInteractions(paymentService);
    }
}
