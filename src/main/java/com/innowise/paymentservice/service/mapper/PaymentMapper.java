package com.innowise.paymentservice.service.mapper;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = PaymentStatus.class)
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(PaymentStatus.PENDING)")
    @Mapping(target = "eventPublished", expression = "java(false)")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", ignore = true)
    PaymentDocument toPendingDocument(PaymentRequest request, String userId);

    PaymentResponse toResponse(PaymentDocument document);

    @AfterMapping
    default void copyCreatedAtToUpdatedAt(@MappingTarget PaymentDocument document) {
        document.setUpdatedAt(document.getCreatedAt());
    }
}
