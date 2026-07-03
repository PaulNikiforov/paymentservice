package com.innowise.paymentservice.document;

import com.innowise.paymentservice.MongoTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestcontainersConfiguration.class)
@TestPropertySource(properties = "mongock.enabled=false")
class PaymentDocumentPersistenceTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldPersistAndReadPaymentDocumentPreservingFieldsAndDecimalPrecision() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentDocument document = new PaymentDocument(
                null,
                "order-123",
                "user-456",
                PaymentStatus.PENDING,
                new BigDecimal("99.99"),
                true,
                createdAt,
                createdAt
        );

        PaymentDocument saved = mongoTemplate.save(document);
        PaymentDocument found = mongoTemplate.findById(saved.getId(), PaymentDocument.class);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isNotNull().isInstanceOf(String.class);
        assertThat(found.getOrderId()).isEqualTo("order-123");
        assertThat(found.getUserId()).isEqualTo("user-456");
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.getPaymentAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(found.isEventPublished()).isTrue();
        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldDefaultEventPublishedToFalseWhenCreatedViaNoArgsConstructor() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentDocument document = new PaymentDocument();
        document.setOrderId("order-789");
        document.setUserId("user-321");
        document.setStatus(PaymentStatus.PENDING);
        document.setPaymentAmount(new BigDecimal("50.00"));
        document.setCreatedAt(createdAt);
        document.setUpdatedAt(createdAt);

        PaymentDocument saved = mongoTemplate.save(document);
        PaymentDocument found = mongoTemplate.findById(saved.getId(), PaymentDocument.class);

        assertThat(found).isNotNull();
        assertThat(found.isEventPublished()).isFalse();
    }
}
