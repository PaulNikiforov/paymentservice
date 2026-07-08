package com.innowise.paymentservice.service.dto;

import com.innowise.paymentservice.document.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        String userId,
        PaymentStatus status,
        BigDecimal paymentAmount,
        Instant timestamp
) {
}
