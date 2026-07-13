package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentRepositoryCustom {

    PaymentDocument findOrCreatePending(String orderId, String userId, BigDecimal amount);

    Page<PaymentDocument> findByFilters(String userId, String orderId, PaymentStatus status, Pageable pageable);

    BigDecimal sumSuccessfulPaymentsForUser(String userId, Instant from, Instant to);

    BigDecimal sumSuccessfulPaymentsForAllUsers(Instant from, Instant to);
}
