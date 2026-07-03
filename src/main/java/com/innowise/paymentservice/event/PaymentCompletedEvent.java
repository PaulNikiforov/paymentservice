package com.innowise.paymentservice.event;

import com.innowise.paymentservice.document.PaymentStatus;

// Outbox event payload published to the payment-events topic for Order Service (task-14).
// The publish-then-mark ordering invariant lives in PaymentOutboxPublisher's Javadoc.
public record PaymentCompletedEvent(String orderId, PaymentStatus status) {
}
