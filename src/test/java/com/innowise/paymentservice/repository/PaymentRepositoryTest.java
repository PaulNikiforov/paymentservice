package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.MongoTestcontainersConfiguration;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestcontainersConfiguration.class)
@TestPropertySource(properties = "mongock.enabled=false")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanCollection() {
        paymentRepository.deleteAll();
    }

    private static PaymentDocument payment(String orderId, String userId, PaymentStatus status,
                                            boolean eventPublished, BigDecimal amount, Instant createdAt) {
        return new PaymentDocument(null, orderId, userId, status, amount, eventPublished, createdAt, createdAt);
    }

    private static PaymentDocument payment(String orderId, String userId, PaymentStatus status,
                                            boolean eventPublished) {
        return payment(orderId, userId, status, eventPublished, new BigDecimal("10.00"), Instant.now());
    }

    private static PaymentDocument paymentAt(String orderId, String userId, PaymentStatus status,
                                              BigDecimal amount, Instant createdAt) {
        return payment(orderId, userId, status, false, amount, createdAt);
    }

    @Test
    void findOrCreatePending_whenNoPaymentExistsForOrderId_createsNewPendingPayment() {
        PaymentDocument created = paymentRepository.findOrCreatePending("order-new", "user-1", new BigDecimal("42.50"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getOrderId()).isEqualTo("order-new");
        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getPaymentAmount()).isEqualByComparingTo("42.50");
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.isEventPublished()).isFalse();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void findOrCreatePending_whenPaymentAlreadyExistsForOrderId_returnsExistingWithoutDuplicating() {
        PaymentDocument first = paymentRepository.findOrCreatePending("order-dup", "user-1", new BigDecimal("10.00"));

        PaymentDocument second = paymentRepository.findOrCreatePending("order-dup", "user-1", new BigDecimal("999.00"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getPaymentAmount()).isEqualByComparingTo("10.00");
        assertThat(paymentRepository.findByStatus(PaymentStatus.PENDING, PageRequest.of(0, 50)).getContent())
                .filteredOn(p -> p.getOrderId().equals("order-dup"))
                .hasSize(1);
    }

    @Test
    void paymentAmountIsStoredAsDecimal128NotString() {
        PaymentDocument saved = paymentRepository.save(payment("order-x", "user-x", PaymentStatus.SUCCESS, false));

        Document raw = mongoTemplate.getCollection(mongoTemplate.getCollectionName(PaymentDocument.class))
                .find(new Document("_id", new ObjectId(saved.getId())))
                .first();

        assertThat(raw.get("paymentAmount")).isInstanceOf(Decimal128.class);
    }

    @Test
    void shouldSaveAndFindPaymentById() {
        PaymentDocument document = payment("order-1", "user-1", PaymentStatus.PENDING, false);

        PaymentDocument saved = paymentRepository.save(document);

        assertThat(paymentRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByStatusInAndEventPublishedFalse_returnsOnlyUnpublishedResolvedPayments() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-1", PaymentStatus.FAILED, false),
                payment("order-3", "user-1", PaymentStatus.SUCCESS, true),
                payment("order-4", "user-1", PaymentStatus.PENDING, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByStatusInAndEventPublishedFalse(
                List.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED), PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(PaymentDocument::getOrderId)
                .containsExactlyInAnyOrder("order-1", "order-2");
    }

    @Test
    void findByFilters_filtersByUserIdOnly() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-2", PaymentStatus.SUCCESS, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByFilters("user-1", null, null, PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(PaymentDocument::getOrderId)
                .containsExactly("order-1");
    }

    @Test
    void findByFilters_filtersByOrderIdOnly() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-1", PaymentStatus.SUCCESS, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByFilters(null, "order-2", null, PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(PaymentDocument::getOrderId)
                .containsExactly("order-2");
    }

    @Test
    void findByFilters_filtersByStatusOnly() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-1", PaymentStatus.FAILED, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByFilters(null, null, PaymentStatus.FAILED,
                PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(PaymentDocument::getOrderId)
                .containsExactly("order-2");
    }

    @Test
    void findByFilters_combinesUserIdAndStatus() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-1", PaymentStatus.FAILED, false),
                payment("order-3", "user-2", PaymentStatus.SUCCESS, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByFilters("user-1", null, PaymentStatus.SUCCESS,
                PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(PaymentDocument::getOrderId)
                .containsExactly("order-1");
    }

    @Test
    void findByFilters_withNoFiltersReturnsAllPaginated() {
        paymentRepository.saveAll(List.of(
                payment("order-1", "user-1", PaymentStatus.SUCCESS, false),
                payment("order-2", "user-2", PaymentStatus.FAILED, false)
        ));

        Page<PaymentDocument> result = paymentRepository.findByFilters(null, null, null, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void sumSuccessfulPaymentsForUser_sumsOnlySuccessWithinRangeForGivenUser() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        Instant inRange = Instant.parse("2026-01-15T00:00:00Z");
        Instant outOfRange = Instant.parse("2026-02-01T00:00:00Z");

        paymentRepository.saveAll(List.of(
                paymentAt("order-1", "user-1", PaymentStatus.SUCCESS, new BigDecimal("100.00"), inRange),
                paymentAt("order-2", "user-1", PaymentStatus.SUCCESS, new BigDecimal("50.50"), inRange),
                paymentAt("order-3", "user-1", PaymentStatus.FAILED, new BigDecimal("999.00"), inRange),
                paymentAt("order-4", "user-2", PaymentStatus.SUCCESS, new BigDecimal("30.00"), inRange),
                paymentAt("order-5", "user-1", PaymentStatus.SUCCESS, new BigDecimal("999.00"), outOfRange)
        ));

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsForUser("user-1", from, to);

        assertThat(total).isEqualByComparingTo(new BigDecimal("150.50"));
    }

    @Test
    void sumSuccessfulPaymentsForUser_returnsZeroWhenNoMatches() {
        BigDecimal total = paymentRepository.sumSuccessfulPaymentsForUser("user-1", Instant.EPOCH, Instant.now());

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumSuccessfulPaymentsForAllUsers_sumsAcrossAllUsersWithinRangeIgnoringOtherStatuses() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        Instant inRange = Instant.parse("2026-01-15T00:00:00Z");
        Instant outOfRange = Instant.parse("2026-02-01T00:00:00Z");

        paymentRepository.saveAll(List.of(
                paymentAt("order-1", "user-1", PaymentStatus.SUCCESS, new BigDecimal("100.00"), inRange),
                paymentAt("order-2", "user-2", PaymentStatus.SUCCESS, new BigDecimal("25.25"), inRange),
                paymentAt("order-3", "user-1", PaymentStatus.PENDING, new BigDecimal("999.00"), inRange),
                paymentAt("order-4", "user-2", PaymentStatus.SUCCESS, new BigDecimal("999.00"), outOfRange)
        ));

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsForAllUsers(from, to);

        assertThat(total).isEqualByComparingTo(new BigDecimal("125.25"));
    }

    @Test
    void sumSuccessfulPaymentsForAllUsers_returnsZeroWhenNoMatches() {
        BigDecimal total = paymentRepository.sumSuccessfulPaymentsForAllUsers(Instant.EPOCH, Instant.now());

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
