package com.innowise.paymentservice.event;

import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Consumes {@code CREATE_ORDER} events published by orderservice and turns each into a
 * {@code PENDING} payment via {@link PaymentService#create(PaymentRequest, String)} — the sole
 * way a payment now comes into existence (the former {@code POST /api/v1/payments} endpoint was
 * removed, see FIX-01). {@code userId} comes from the event, not a JWT: there is no request/token
 * in a Kafka consumer, and Order Service has already authorized the caller when it created the
 * order.
 *
 * <p>{@link CreateOrderEvent}'s bean-validation constraints are checked explicitly against the
 * injected {@link Validator} rather than via {@code @Validated}/{@code @Valid} on the listener
 * method: method-level validation relies on a CGLIB proxy intercepting the call, and
 * {@code @KafkaListener} invocation ordering does not reliably go through that proxy, so the
 * constraint would silently never fire. An explicit check has no such ordering dependency. A
 * violation throws {@link jakarta.validation.ConstraintViolationException}, which
 * {@link KafkaConsumerConfig}'s error handler treats the same as any other processing failure
 * (bounded retry, then DLT).
 *
 * <p>{@link PaymentService#create} is idempotent by {@code orderId}, so a redelivered event
 * (at-least-once Kafka delivery) does not create a duplicate payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final PaymentService paymentService;
    private final Validator validator;

    @KafkaListener(topics = "order-events", groupId = "paymentservice")
    public void listen(CreateOrderEvent event) {
        try {
            Set<ConstraintViolation<CreateOrderEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            paymentService.create(new PaymentRequest(event.orderId(), event.amount()), event.userId());
        } catch (Exception ex) {
            log.error("Failed to process CREATE_ORDER event for orderId={}, userId={}",
                    event.orderId(), event.userId(), ex);
            throw ex;
        }
    }
}
