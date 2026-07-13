package com.innowise.paymentservice.service;

import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Payment operations: creating payments, retrieving them with ownership checks,
 * paginated listing filtered by user role, and aggregated success-payment summaries
 * for a single user or the whole platform.
 *
 * <p>Authorization is role-based: regular users can only access their own payments
 * and their own summary, while admins can access any payment and the platform-wide
 * summary. Access checks are enforced before any data is returned to the caller — in
 * {@link #getById(String, String, boolean)} a not-found check precedes the access check,
 * since ownership cannot be verified for a payment that does not exist (404 before 403).
 */
public interface PaymentService {

    /**
     * Creates a new payment in {@code PENDING} status for the given user, or returns the
     * existing payment if one already exists for {@code request.orderId()}. Idempotent by
     * {@code orderId}: the Kafka {@code CREATE_ORDER} consumer may redeliver the same order
     * at-least-once, and this must not create a second payment for it.
     *
     * @param request the payment creation payload
     * @param userId  the identifier of the user owning the payment
     * @return the created (or pre-existing) payment representation
     */
    PaymentResponse create(PaymentRequest request, String userId);

    /**
     * Returns a single payment by id, enforcing ownership unless the caller is an admin.
     *
     * @param id     the payment identifier
     * @param userId the identifier of the calling user
     * @param admin  whether the caller has the admin role
     * @return the payment representation
     * @throws com.innowise.paymentservice.exception.PaymentNotFoundException        if no payment exists for the id
     * @throws com.innowise.paymentservice.exception.PaymentAccessDeniedException   if a non-admin caller requests another user's payment
     */
    PaymentResponse getById(String id, String userId, boolean admin);

    /**
     * Lists payments matching the filter. Non-admin callers are always scoped to their
     * own payments regardless of the {@code userId} provided in the filter; admins use
     * the filter's {@code userId} (which may be {@code null} to list all payments).
     *
     * @param filter   the filtering criteria
     * @param userId   the identifier of the calling user
     * @param admin    whether the caller has the admin role
     * @param pageable the pagination parameters
     * @return a page of payment representations
     */
    Page<PaymentResponse> list(PaymentFilter filter, String userId, boolean admin, Pageable pageable);

    /**
     * Returns the total amount of successful payments for a target user within a time range.
     *
     * @param targetUserId the user whose payments are summarized
     * @param from         the inclusive start of the range
     * @param to           the inclusive end of the range
     * @param callerUserId the identifier of the calling user
     * @param admin        whether the caller has the admin role
     * @return the summary for the target user
     * @throws com.innowise.paymentservice.exception.PaymentAccessDeniedException if a non-admin caller requests another user's summary
     */
    PaymentSummaryResponse userSummary(String targetUserId, Instant from, Instant to, String callerUserId, boolean admin);

    /**
     * Returns the total amount of successful payments across all users within a time range.
     *
     * @param from  the inclusive start of the range
     * @param to    the inclusive end of the range
     * @param admin whether the caller has the admin role
     * @return the platform-wide summary
     * @throws com.innowise.paymentservice.exception.PaymentAccessDeniedException if the caller is not an admin
     */
    PaymentSummaryResponse platformSummary(Instant from, Instant to, boolean admin);
}
