package com.innowise.paymentservice.event;

import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private PaymentService paymentService;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(paymentService);
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
}
