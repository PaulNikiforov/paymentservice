package com.innowise.paymentservice.event;

import java.math.BigDecimal;

public record CreateOrderEvent(String orderId, String userId, BigDecimal amount) {
}
