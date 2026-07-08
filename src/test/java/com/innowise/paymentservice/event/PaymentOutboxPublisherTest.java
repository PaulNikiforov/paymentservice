package com.innowise.paymentservice.event;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final String TOPIC = "payment-events";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    private PaymentOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentOutboxPublisher(paymentRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("happy path: successful send marks eventPublished=true and persists, send before save")
    void publishPending_whenSendSucceeds_marksPublishedAndSaves() {
        PaymentDocument payment = pending("order-1", PaymentStatus.SUCCESS);
        stubPage(payment);

        when(kafkaTemplate.send(eq(TOPIC), eq("order-1"), any(PaymentCompletedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishPending();

        assertThat(payment.isEventPublished()).isTrue();
        ArgumentCaptor<PaymentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        InOrder order = inOrder(kafkaTemplate, paymentRepository);
        order.verify(kafkaTemplate).send(eq(TOPIC), eq("order-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new PaymentCompletedEvent("order-1", PaymentStatus.SUCCESS));
        order.verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("failure path: send fails -> eventPublished stays false and document is not saved")
    void publishPending_whenSendFails_leavesEventPublishedFalseAndDoesNotSave() {
        PaymentDocument payment = pending("order-1", PaymentStatus.FAILED);
        stubPage(payment);

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka broker down")));

        publisher.publishPending();

        assertThat(payment.isEventPublished()).isFalse();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("polls only resolved statuses (SUCCESS, FAILED) in batches of 50")
    void publishPending_pollsResolvedStatusesInBatchesOfFifty() {
        when(paymentRepository.findByStatusInAndEventPublishedFalse(any(), any()))
                .thenReturn(emptyPage());

        publisher.publishPending();

        ArgumentCaptor<List<PaymentStatus>> statuses = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByStatusInAndEventPublishedFalse(statuses.capture(), pageable.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(PaymentStatus.SUCCESS, PaymentStatus.FAILED);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private void stubPage(PaymentDocument payment) {
        when(paymentRepository.findByStatusInAndEventPublishedFalse(any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 50), 1));
    }

    private Page<PaymentDocument> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
    }

    private PaymentDocument pending(String orderId, PaymentStatus status) {
        return new PaymentDocument("p1", orderId, "u1", status,
                new BigDecimal("10.00"), false, NOW);
    }

    private static SendResult<String, PaymentCompletedEvent> sendResult() {
        return new SendResult<>(null, null);
    }
}
