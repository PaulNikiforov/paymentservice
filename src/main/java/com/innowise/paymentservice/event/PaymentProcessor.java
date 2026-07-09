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
