package com.innowise.paymentservice.service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void isValid_whenOrderIdAndPositiveAmountProvided() {
        PaymentRequest request = new PaymentRequest("order-1", new BigDecimal("10.00"));

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void isInvalid_whenOrderIdBlank() {
        PaymentRequest request = new PaymentRequest("", new BigDecimal("10.00"));

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("orderId");
    }

    @Test
    void isInvalid_whenPaymentAmountNull() {
        PaymentRequest request = new PaymentRequest("order-1", null);

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("paymentAmount");
    }

    @Test
    void isInvalid_whenPaymentAmountNotPositive() {
        PaymentRequest request = new PaymentRequest("order-1", BigDecimal.ZERO);

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("paymentAmount");
    }
}
