package com.innowise.paymentservice.event;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class PaymentOutboxPublisher {

    public static final String TOPIC = "payment-events";
    private static final long SEND_TIMEOUT_MS = 5000;
    private static final List<PaymentStatus> RESOLVED_STATUSES =
            List.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED);

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @Value("${payment.outbox.batch-size:50}")
    private int batchSize = 50;

    public PaymentOutboxPublisher(PaymentRepository paymentRepository,
                                  KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        Page<PaymentDocument> pending = paymentRepository.findByStatusInAndEventPublishedFalse(
                RESOLVED_STATUSES, PageRequest.of(0, batchSize));
        for (PaymentDocument payment : pending) {
            publishOne(payment);
        }
    }

    private void publishOne(PaymentDocument payment) {
        try {
            kafkaTemplate.send(TOPIC, payment.getOrderId(),
                    new PaymentCompletedEvent(payment.getOrderId(), payment.getStatus()))
                    .get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish payment event for orderId={}; will retry on the next poll tick",
                    payment.getOrderId(), e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing payment event for orderId={}; will retry on the next poll tick",
                    payment.getOrderId(), e);
            return;
        }
        payment.setEventPublished(true);
        paymentRepository.save(payment);
    }
}
