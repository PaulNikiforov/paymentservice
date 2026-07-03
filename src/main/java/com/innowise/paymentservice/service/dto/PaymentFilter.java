package com.innowise.paymentservice.service.dto;

import com.innowise.paymentservice.document.PaymentStatus;

public record PaymentFilter(
        String orderId,
        PaymentStatus status,
        String userId
) {
}
