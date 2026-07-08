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
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentDocument document = new PaymentDocument(
                null,
                "order-123",
                "user-456",
                PaymentStatus.PENDING,
                new BigDecimal("99.99"),
                true,
                timestamp
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
        assertThat(found.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldDefaultEventPublishedToFalseWhenCreatedViaNoArgsConstructor() {
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentDocument document = new PaymentDocument();
        document.setOrderId("order-789");
        document.setUserId("user-321");
        document.setStatus(PaymentStatus.PENDING);
        document.setPaymentAmount(new BigDecimal("50.00"));
        document.setTimestamp(timestamp);

        PaymentDocument saved = mongoTemplate.save(document);
        PaymentDocument found = mongoTemplate.findById(saved.getId(), PaymentDocument.class);

        assertThat(found).isNotNull();
        assertThat(found.isEventPublished()).isFalse();
    }

    @Test
    void shouldPersistDocumentWithSnakeCaseBsonFieldNames() {
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PaymentDocument document = new PaymentDocument(null, "order-1", "user-1", PaymentStatus.PENDING,
                new BigDecimal("10.00"), false, timestamp);

        PaymentDocument saved = mongoTemplate.save(document);

        org.bson.Document raw = mongoTemplate.getCollection(mongoTemplate.getCollectionName(PaymentDocument.class))
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(saved.getId())))
                .first();

        assertThat(raw.keySet())
                .contains("order_id", "user_id", "payment_amount", "timestamp", "event_published")
                .doesNotContain("orderId", "userId", "paymentAmount", "createdAt", "updatedAt", "eventPublished");
    }
}
