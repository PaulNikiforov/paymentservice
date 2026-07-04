package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_ROLE = "role";
    private static final String ADMIN_ROLE = "ADMIN";

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return paymentService.create(request, callerUserId(jwt));
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable String id,
                                    @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getById(id, callerUserId(jwt), isAdmin(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Page<PaymentResponse> list(@RequestParam(required = false) String orderId,
                                       @RequestParam(required = false) PaymentStatus status,
                                       @RequestParam(required = false) String userId,
                                       @AuthenticationPrincipal Jwt jwt,
                                       Pageable pageable) {
        PaymentFilter filter = new PaymentFilter(orderId, status, userId);
        return paymentService.list(filter, callerUserId(jwt), isAdmin(jwt), pageable);
    }

    @GetMapping("/users/{userId}/summary")
    public PaymentSummaryResponse userSummary(@PathVariable String userId,
                                               @RequestParam Instant from,
                                               @RequestParam Instant to,
                                               @AuthenticationPrincipal Jwt jwt) {
        return paymentService.userSummary(userId, from, to, callerUserId(jwt), isAdmin(jwt));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
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
