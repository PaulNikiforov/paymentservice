package com.innowise.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.paymentservice.DisableMongock;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@DisableMongock
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createPayment_returnsAcceptedWithPendingPayment() throws Exception {
        PaymentRequest request = new PaymentRequest("order-1", new BigDecimal("10.00"));
        PaymentResponse response = new PaymentResponse(
                "payment-1",
                "order-1",
                "user-1",
                PaymentStatus.PENDING,
                new BigDecimal("10.00"),
                Instant.parse("2026-07-03T00:00:00Z")
        );

        when(paymentService.create(any(PaymentRequest.class), eq("user-1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("payment-1"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentAmount").value(10.00));
    }

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

        when(paymentService.getById(eq("payment-1"), eq("user-1"), eq(false))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
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
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
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
                eq("user-1"),
                eq(Instant.parse("2024-01-01T00:00:00Z")),
                eq(Instant.parse("2024-12-31T23:59:59Z")),
                eq("user-1"),
                eq(false)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/users/user-1/summary")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
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
                eq(Instant.parse("2024-01-01T00:00:00Z")),
                eq(Instant.parse("2024-12-31T23:59:59Z")),
                eq(true)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/summary")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(5000.00))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void createPayment_returnsBadRequestWhenOrderIdBlank() throws Exception {
        String requestBody = "{\"orderId\":\"\",\"paymentAmount\":10.00}";

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).create(any(), any());
    }
}
