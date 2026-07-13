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

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    public static final String TOPIC = "order-events";

    private final PaymentService paymentService;
    private final Validator validator;

    @KafkaListener(topics = TOPIC, groupId = "paymentservice")
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
