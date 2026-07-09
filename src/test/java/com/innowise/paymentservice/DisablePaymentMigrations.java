package com.innowise.paymentservice;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code LiquibaseMongoMigrationRunner} needs a real {@code spring.data.mongodb.uri} to resolve —
 * any test whose context loads it (including slice tests like {@code @WebMvcTest} that never touch
 * MongoDB themselves) would otherwise fail without a real Mongo connection. Apply this to any test
 * that doesn't need migrations to run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "payment.migrations.enabled=false")
public @interface DisablePaymentMigrations {
}
