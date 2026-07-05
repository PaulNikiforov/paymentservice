package com.innowise.paymentservice.service.mapper;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void toPendingDocument_setsPendingStatusAndUnpublishedEvent() {
        PaymentRequest request = new PaymentRequest("order-1", new BigDecimal("25.50"));

        PaymentDocument document = mapper.toPendingDocument(request, "user-1");

        assertThat(document.getId()).isNull();
        assertThat(document.getOrderId()).isEqualTo("order-1");
        assertThat(document.getUserId()).isEqualTo("user-1");
        assertThat(document.getPaymentAmount()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(document.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(document.isEventPublished()).isFalse();
        assertThat(document.getCreatedAt()).isNotNull();
        assertThat(document.getUpdatedAt()).isNotNull();
    }

    @Test
    void toResponse_mapsAllFields() {
        Instant createdAt = Instant.now();
        PaymentDocument document = new PaymentDocument("id-1", "order-1", "user-1", PaymentStatus.SUCCESS,
                new BigDecimal("99.99"), true, createdAt, createdAt);

        PaymentResponse response = mapper.toResponse(document);

        assertThat(response.id()).isEqualTo("id-1");
        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.paymentAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
