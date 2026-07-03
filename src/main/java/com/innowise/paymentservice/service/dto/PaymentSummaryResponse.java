package com.innowise.paymentservice.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSummaryResponse(
        String userId,
        BigDecimal totalAmount,
        Instant from,
        Instant to
) {
}
