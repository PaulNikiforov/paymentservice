package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends MongoRepository<PaymentDocument, String>, PaymentRepositoryCustom {

    Page<PaymentDocument> findByStatusInAndEventPublishedFalse(List<PaymentStatus> statuses, Pageable pageable);

    Page<PaymentDocument> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant updatedAtBefore, Pageable pageable);
}
