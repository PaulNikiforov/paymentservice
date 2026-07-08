package com.innowise.paymentservice.event;

import com.innowise.paymentservice.client.ExternalPaymentClient;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentGatewayException;
import com.innowise.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
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
 *
 * <p>Runs as a single scheduled task on a single-instance deployment, so a plain
 * {@code findByStatus(PENDING, ...)} poll is sufficient: each tick issues exactly one query for a
 * fixed page and there is no concurrent tick that could re-fetch an in-flight document. Distributed
 * locking is a distinct, deliberately deferred concern (YAGNI) if the service is ever scaled out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final ExternalPaymentClient externalPaymentClient;

    @Value("${payment.processor.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${payment.processor.poll-interval-ms:2000}")
    public void processPending() {
        Page<PaymentDocument> pending = paymentRepository.findByStatus(
                PaymentStatus.PENDING, PageRequest.of(0, batchSize));
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
        payment.setTimestamp(Instant.now());
        paymentRepository.save(payment);
    }
}
