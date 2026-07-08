package com.innowise.paymentservice.event;

import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code CREATE_ORDER} events published by orderservice and turns each into a
 * {@code PENDING} payment via {@link PaymentService#create(PaymentRequest, String)} — the sole
 * way a payment now comes into existence (the former {@code POST /api/v1/payments} endpoint was
 * removed, see FIX-01). {@code userId} comes from the event, not a JWT: there is no request/token
 * in a Kafka consumer, and Order Service has already authorized the caller when it created the
 * order.
 *
 * <p>{@link PaymentService#create} is idempotent by {@code orderId}, so a redelivered event
 * (at-least-once Kafka delivery) does not create a duplicate payment.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = "order-events", groupId = "paymentservice")
    public void listen(CreateOrderEvent event) {
        paymentService.create(new PaymentRequest(event.orderId(), event.amount()), event.userId());
    }
}
