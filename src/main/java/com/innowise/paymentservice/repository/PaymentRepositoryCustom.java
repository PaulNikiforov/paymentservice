package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentRepositoryCustom {

    /**
     * Atomically returns the existing {@code PENDING} payment for {@code orderId} if one already
     * exists, or creates and returns a new one otherwise — a single {@code findAndModify} upsert,
     * not a check-then-act read followed by a write. This closes the race that a
     * {@code findByOrderId} + {@code save} pair would otherwise leave open between two concurrent
     * deliveries of the same {@code CREATE_ORDER} event (Kafka's at-least-once redelivery, or a
     * future consumer rebalance/scale-out).
     */
    PaymentDocument findOrCreatePending(String orderId, String userId, BigDecimal amount);

    Page<PaymentDocument> findByFilters(String userId, String orderId, PaymentStatus status, Pageable pageable);

    BigDecimal sumSuccessfulPaymentsForUser(String userId, Instant from, Instant to);

    BigDecimal sumSuccessfulPaymentsForAllUsers(Instant from, Instant to);
}
