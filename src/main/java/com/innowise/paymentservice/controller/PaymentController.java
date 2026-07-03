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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request,
                                   @RequestHeader("X-User-Id") String callerUserId) {
        return paymentService.create(request, callerUserId);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable String id,
                                    @RequestHeader("X-User-Id") String callerUserId,
                                    @RequestHeader("X-User-Role") String role) {
        return paymentService.getById(id, callerUserId, isAdmin(role));
    }

    @GetMapping
    public Page<PaymentResponse> list(@RequestParam(required = false) String orderId,
                                       @RequestParam(required = false) PaymentStatus status,
                                       @RequestParam(required = false) String userId,
                                       @RequestHeader("X-User-Id") String callerUserId,
                                       @RequestHeader("X-User-Role") String role,
                                       Pageable pageable) {
        PaymentFilter filter = new PaymentFilter(orderId, status, userId);
        return paymentService.list(filter, callerUserId, isAdmin(role), pageable);
    }

    @GetMapping("/users/{userId}/summary")
    public PaymentSummaryResponse userSummary(@PathVariable String userId,
                                               @RequestParam Instant from,
                                               @RequestParam Instant to,
                                               @RequestHeader("X-User-Id") String callerUserId,
                                               @RequestHeader("X-User-Role") String role) {
        return paymentService.userSummary(userId, from, to, callerUserId, isAdmin(role));
    }

    @GetMapping("/summary")
    public PaymentSummaryResponse platformSummary(@RequestParam Instant from,
                                                   @RequestParam Instant to,
                                                   @RequestHeader("X-User-Role") String role) {
        return paymentService.platformSummary(from, to, isAdmin(role));
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equals(role);
    }
}
