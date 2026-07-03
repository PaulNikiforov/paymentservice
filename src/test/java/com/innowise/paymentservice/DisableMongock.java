package com.innowise.paymentservice;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @EnableMongock} sits on {@link PaymentserviceApplication} itself, so any test that uses
 * it as the {@code @SpringBootConfiguration} — including slice tests like {@code @WebMvcTest}
 * that never touch MongoDB — still triggers Mongock's {@code ConnectionDriver} bean resolution
 * and fails without a real Mongo connection. Apply this to any test that doesn't need Mongock.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "mongock.enabled=false")
public @interface DisableMongock {
}
