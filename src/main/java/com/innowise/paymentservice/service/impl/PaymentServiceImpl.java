package com.innowise.paymentservice.service.impl;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.exception.PaymentAccessDeniedException;
import com.innowise.paymentservice.exception.PaymentNotFoundException;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import com.innowise.paymentservice.service.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponse create(PaymentRequest request, String userId) {
        PaymentDocument pending = paymentMapper.toPendingDocument(request, userId);
        PaymentDocument saved = paymentRepository.save(pending);
        return paymentMapper.toResponse(saved);
    }

    @Override
    public PaymentResponse getById(String id, String userId, boolean admin) {
        PaymentDocument doc = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
        requireAdminOr(admin, doc.getUserId().equals(userId), "Access denied: payment " + doc.getId());
        return paymentMapper.toResponse(doc);
    }

    @Override
    public Page<PaymentResponse> list(PaymentFilter filter, String userId, boolean admin, Pageable pageable) {
        String effectiveUserId = admin ? filter.userId() : userId;
        Page<PaymentDocument> docs = paymentRepository.findByFilters(
                effectiveUserId, filter.orderId(), filter.status(), pageable);
        return docs.map(paymentMapper::toResponse);
    }

    @Override
    public PaymentSummaryResponse userSummary(String targetUserId, Instant from, Instant to,
                                              String callerUserId, boolean admin) {
        requireAdminOr(admin, targetUserId.equals(callerUserId),
                "Access denied: summary for user " + targetUserId);
        var total = paymentRepository.sumSuccessfulPaymentsForUser(targetUserId, from, to);
        return new PaymentSummaryResponse(targetUserId, total, from, to);
    }

    @Override
    public PaymentSummaryResponse platformSummary(Instant from, Instant to, boolean admin) {
        requireAdminOr(admin, false,
                "Access denied: platform summary requires admin role");
        var total = paymentRepository.sumSuccessfulPaymentsForAllUsers(from, to);
        return new PaymentSummaryResponse(null, total, from, to);
    }

    private void requireAdminOr(boolean admin, boolean allowed, String message) {
        if (!admin && !allowed) {
            throw new PaymentAccessDeniedException(message);
        }
    }
}
