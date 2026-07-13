package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.DisablePaymentMigrations;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.config.JwtAuthenticationEntryPoint;
import com.innowise.paymentservice.config.SecurityConfig;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@DisablePaymentMigrations
@StubJwksUri
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getById_returnsOkWithPayment() throws Exception {
        PaymentResponse response = new PaymentResponse(
                "payment-1",
                "order-1",
                "user-1",
                PaymentStatus.SUCCESS,
                new BigDecimal("10.00"),
                Instant.parse("2026-07-03T00:00:00Z")
        );

        when(paymentService.getById("payment-1", "user-1", false)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("payment-1"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentAmount").value(10.00));
    }

    @Test
    void list_returnsOkWithPageOfPayments() throws Exception {
        PaymentResponse response = new PaymentResponse(
                "payment-1",
                "order-1",
                "user-1",
                PaymentStatus.SUCCESS,
                new BigDecimal("10.00"),
                Instant.parse("2026-07-03T00:00:00Z")
        );

        when(paymentService.list(
                eq(new PaymentFilter("order-1", PaymentStatus.SUCCESS, null)),
                eq("user-1"),
                eq(false),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", "order-1")
                        .param("status", "SUCCESS")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("payment-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void userSummary_returnsOkWithSummaryForOwnUser() throws Exception {
        PaymentSummaryResponse response = new PaymentSummaryResponse(
                "user-1",
                new BigDecimal("150.00"),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z")
        );

        when(paymentService.userSummary(
                "user-1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z"),
                "user-1",
                false))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/users/user-1/summary")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.totalAmount").value(150.00));
    }

    @Test
    void platformSummary_returnsOkWithSummaryForAdmin() throws Exception {
        PaymentSummaryResponse response = new PaymentSummaryResponse(
                null,
                new BigDecimal("5000.00"),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z")
        );

        when(paymentService.platformSummary(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z"),
                true))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/summary")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z")
                        .with(jwt().jwt(j -> j.claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(5000.00))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

}
