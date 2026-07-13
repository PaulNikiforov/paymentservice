package com.innowise.paymentservice.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderEvent(
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotNull @Positive BigDecimal amount
) {
}
