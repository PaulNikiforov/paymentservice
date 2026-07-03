package com.innowise.paymentservice.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class PaymentDocument {

    @Id
    private String id;
    private String orderId;
    private String userId;
    private PaymentStatus status;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal paymentAmount;
    private boolean eventPublished;
    private Instant createdAt;
    private Instant updatedAt;
}
