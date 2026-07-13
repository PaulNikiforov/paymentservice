package com.innowise.paymentservice.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal paymentAmount
) {
}
