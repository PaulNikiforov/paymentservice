package com.innowise.paymentservice.event;

import com.innowise.paymentservice.document.PaymentStatus;

public record PaymentCompletedEvent(String orderId, PaymentStatus status) {
}
