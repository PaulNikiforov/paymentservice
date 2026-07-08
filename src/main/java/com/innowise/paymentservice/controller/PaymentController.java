package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for payment retrieval and success-payment summaries. Payment creation is not part of
 * this API — a payment comes into existence only as a reaction to the {@code CREATE_ORDER} Kafka
 * event (see {@link com.innowise.paymentservice.event.OrderEventListener}, FIX-01).
 *
 * <p>Identity is read from the validated JWT (claims {@code sub}/{@code role}, see
 * {@link com.innowise.paymentservice.config.SecurityConfig}) — regular users may only access
 * their own payments/summary, admins may access any. Ownership checks that depend on the
 * fetched document (e.g. {@link #getById}) are enforced in the service layer, not here;
 * role-only checks that need no document data use {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment retrieval and success-payment summaries")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_ROLE = "role";
    private static final String ADMIN_ROLE = "ADMIN";

    private final PaymentService paymentService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment by id", description = "USER may only fetch their own payment; ADMIN may fetch any.")
    @ApiResponse(responseCode = "200", description = "Payment found")
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    @ApiResponse(responseCode = "403", description = "USER requesting another user's payment")
    @ApiResponse(responseCode = "404", description = "Payment id does not exist")
    public PaymentResponse getById(@PathVariable String id,
                                   @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getById(id, callerUserId(jwt), isAdmin(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "List payments", description = "USER is always scoped to their own payments regardless of the userId filter; ADMIN may filter by any userId or omit it for all.")
    @ApiResponse(responseCode = "200", description = "Paged list of payments")
    @ApiResponse(responseCode = "400", description = "Invalid status filter value")
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Page<PaymentResponse> list(@RequestParam(required = false)
                                      @Parameter(description = "Filter by order id") String orderId,
                                      @RequestParam(required = false)
                                      @Parameter(description = "Filter by payment status") PaymentStatus status,
                                      @RequestParam(required = false)
                                      @Parameter(description = "ADMIN-only: filter by owner user id") String userId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Pageable pageable) {
        PaymentFilter filter = new PaymentFilter(orderId, status, userId);
        return paymentService.list(filter, callerUserId(jwt), isAdmin(jwt), pageable);
    }

    @GetMapping("/users/{userId}/summary")
    @Operation(summary = "Get a user's success-payment summary", description = "Sum of SUCCESS payments for {userId} within [from, to]. USER may only request their own; ADMIN may request any.")
    @ApiResponse(responseCode = "200", description = "Summary computed")
    @ApiResponse(responseCode = "400", description = "Missing or invalid from/to parameters")
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    @ApiResponse(responseCode = "403", description = "USER requesting another user's summary")
    public PaymentSummaryResponse userSummary(@PathVariable String userId,
                                              @RequestParam Instant from,
                                              @RequestParam Instant to,
                                              @AuthenticationPrincipal Jwt jwt) {
        return paymentService.userSummary(userId, from, to, callerUserId(jwt), isAdmin(jwt));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get the platform-wide success-payment summary", description = "Sum of SUCCESS payments across all users within [from, to]. ADMIN only.")
    @ApiResponse(responseCode = "200", description = "Summary computed")
    @ApiResponse(responseCode = "400", description = "Missing or invalid from/to parameters")
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    public PaymentSummaryResponse platformSummary(@RequestParam Instant from,
                                                  @RequestParam Instant to,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return paymentService.platformSummary(from, to, isAdmin(jwt));
    }

    private String callerUserId(Jwt jwt) {
        return jwt.getClaimAsString(CLAIM_SUB);
    }

    private boolean isAdmin(Jwt jwt) {
        return ADMIN_ROLE.equals(jwt.getClaimAsString(CLAIM_ROLE));
    }
}
