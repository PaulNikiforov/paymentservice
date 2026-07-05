package com.innowise.paymentservice.event;

import com.innowise.paymentservice.client.ExternalPaymentClient;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentGatewayException;
import com.innowise.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled poller that resolves {@link PaymentStatus#PENDING} payments by calling the external
 * payment API and persisting the resulting {@link PaymentStatus#SUCCESS}/{@link PaymentStatus#FAILED}
 * status. This is the only place in the codebase allowed to call {@link ExternalPaymentClient#charge}.
 *
 * <p>Infrastructure failure (open circuit, timeout — surfaced as {@link PaymentGatewayException})
 * is not a payment rejection: the document is left untouched and retried on a later tick. Only a
 * real answer from the external API (even/odd) resolves a payment to a terminal status.
 *
 * <p>Publishing the resulting terminal status to Kafka is a separate concern, handled by
 * {@link PaymentOutboxPublisher} — this class never touches {@code eventPublished}.
 */
@Slf4j
@Component
public class PaymentProcessor {

    private static final int BATCH_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final ExternalPaymentClient externalPaymentClient;
    private final long minAgeMs;

    public PaymentProcessor(PaymentRepository paymentRepository,
                             ExternalPaymentClient externalPaymentClient,
                             @Value("${payment.processor.min-age-ms:250}") long minAgeMs) {
        this.paymentRepository = paymentRepository;
        this.externalPaymentClient = externalPaymentClient;
        this.minAgeMs = minAgeMs;
    }

    @Scheduled(fixedDelayString = "${payment.processor.poll-interval-ms:2000}")
    public void processPending() {
        Instant updatedBefore = Instant.now().minusMillis(minAgeMs);
        Page<PaymentDocument> pending = paymentRepository.findByStatusAndUpdatedAtBefore(
                PaymentStatus.PENDING, updatedBefore, PageRequest.of(0, BATCH_SIZE));
        for (PaymentDocument payment : pending) {
            processOne(payment);
        }
    }

    private void processOne(PaymentDocument payment) {
        PaymentStatus result;
        try {
            result = externalPaymentClient.charge(payment);
        } catch (PaymentGatewayException e) {
            log.warn("External payment API unavailable for paymentId={}; will retry on the next poll tick",
                    payment.getId(), e);
            return;
        }
        payment.setStatus(result);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
    }
}
