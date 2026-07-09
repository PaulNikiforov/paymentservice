package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.DisablePaymentMigrations;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.config.JwtAuthenticationEntryPoint;
import com.innowise.paymentservice.config.SecurityConfig;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.service.PaymentService;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@DisablePaymentMigrations
@StubJwksUri
class PaymentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getById_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payment-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returnsUnauthorizedWhenAuthorizationHeaderHoldsMalformedJwt() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("Authorization", "Bearer not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returnsOkWhenAuthenticatedViaJwtWithoutHeaders() throws Exception {
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
                        .with(userJwt("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("payment-1"));
    }

    @Test
    void platformSummary_returnsForbiddenWhenCallerIsNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/payments/summary")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z")
                        .with(userJwt("user-1")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor userJwt(String userId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("sub", userId).claim("role", "USER"));
    }
}
