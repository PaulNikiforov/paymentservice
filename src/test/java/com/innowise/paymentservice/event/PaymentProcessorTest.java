package com.innowise.paymentservice.event;

import com.innowise.paymentservice.client.ExternalPaymentClient;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentGatewayException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExternalPaymentClient externalPaymentClient;

    private PaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentProcessor(paymentRepository, externalPaymentClient);
    }

    @Test
    @DisplayName("happy path: external API resolves SUCCESS -> status and timestamp persisted")
    void processPending_whenChargeSucceeds_savesResolvedStatus() {
        PaymentDocument payment = pending("order-1");
        stubPage(payment);
        when(externalPaymentClient.charge(payment)).thenReturn(PaymentStatus.SUCCESS);

        processor.processPending();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTimestamp()).isAfter(NOW);
        InOrder order = inOrder(externalPaymentClient, paymentRepository);
        order.verify(externalPaymentClient).charge(payment);
        order.verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("real rejection: external API resolves FAILED -> status persisted as FAILED, not retried as PENDING")
    void processPending_whenChargeResolvesFailed_savesFailedStatus() {
        PaymentDocument payment = pending("order-1");
        stubPage(payment);
        when(externalPaymentClient.charge(payment)).thenReturn(PaymentStatus.FAILED);

        processor.processPending();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("infra failure: PaymentGatewayException leaves the document PENDING and unsaved for retry next tick")
    void processPending_whenExternalApiUnavailable_leavesPendingAndDoesNotSave() {
        PaymentDocument payment = pending("order-1");
        stubPage(payment);
        when(externalPaymentClient.charge(payment))
                .thenThrow(new PaymentGatewayException("external payment API unavailable", null));

        processor.processPending();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("polls only PENDING payments, in batches of 50")
    void processPending_pollsPendingInBatchesOfFifty() {
        when(paymentRepository.findByStatus(any(), any()))
                .thenReturn(emptyPage());

        processor.processPending();

        ArgumentCaptor<PaymentStatus> status = ArgumentCaptor.forClass(PaymentStatus.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByStatus(status.capture(), pageable.capture());
        assertThat(status.getValue()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private void stubPage(PaymentDocument payment) {
        when(paymentRepository.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 50), 1));
    }

    private Page<PaymentDocument> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
    }

    private PaymentDocument pending(String orderId) {
        return new PaymentDocument("p1", orderId, "u1", PaymentStatus.PENDING,
                new BigDecimal("10.00"), false, NOW);
    }
}
