package com.innowise.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.external-api")
public record ExternalApiProperties(String url, long connectTimeoutMs, long readTimeoutMs) {
}
