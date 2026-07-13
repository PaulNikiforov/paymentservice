package com.innowise.paymentservice.service.mapper;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toResponse(PaymentDocument document);
}
