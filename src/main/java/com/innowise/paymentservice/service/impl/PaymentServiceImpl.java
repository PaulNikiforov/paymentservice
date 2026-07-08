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
        return paymentRepository.findByOrderId(request.orderId())
                .map(paymentMapper::toResponse)
                .orElseGet(() -> {
                    PaymentDocument pending = paymentMapper.toPendingDocument(request, userId);
                    PaymentDocument saved = paymentRepository.save(pending);
                    return paymentMapper.toResponse(saved);
                });
    }

    @Override
    public PaymentResponse getById(String id, String userId, boolean admin) {
        PaymentDocument doc = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
        if (isAccessDenied(admin, doc.getUserId().equals(userId))) {
            throw new PaymentAccessDeniedException("Access denied: payment " + doc.getId());
        }
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
        if (isAccessDenied(admin, targetUserId.equals(callerUserId))) {
            throw new PaymentAccessDeniedException("Access denied: summary for user " + targetUserId);
        }
        var total = paymentRepository.sumSuccessfulPaymentsForUser(targetUserId, from, to);
        return new PaymentSummaryResponse(targetUserId, total, from, to);
    }

    @Override
    public PaymentSummaryResponse platformSummary(Instant from, Instant to, boolean admin) {
        if (!admin) {
            throw new PaymentAccessDeniedException("Access denied: platform summary requires admin role");
        }
        var total = paymentRepository.sumSuccessfulPaymentsForAllUsers(from, to);
        return new PaymentSummaryResponse(null, total, from, to);
    }

    private boolean isAccessDenied(boolean admin, boolean allowed) {
        return !admin && !allowed;
    }
}
