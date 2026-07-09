package com.innowise.paymentservice.exception;

import com.innowise.paymentservice.DisablePaymentMigrations;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.config.JwtAuthenticationEntryPoint;
import com.innowise.paymentservice.config.SecurityConfig;
import com.innowise.paymentservice.controller.PaymentController;
import com.innowise.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@DisablePaymentMigrations
@StubJwksUri
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getById_returnsNotFoundWithErrorResponseWhenPaymentMissing() throws Exception {
        when(paymentService.getById("payment-404", "user-1", false))
                .thenThrow(new PaymentNotFoundException("Payment not found: payment-404"));

        mockMvc.perform(get("/api/v1/payments/payment-404")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found: payment-404"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-404"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getById_returnsForbiddenWithErrorResponseWhenAccessDenied() throws Exception {
        when(paymentService.getById("payment-1", "user-2", false))
                .thenThrow(new PaymentAccessDeniedException("Access denied to payment payment-1"));

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .with(jwt().jwt(j -> j.claim("sub", "user-2").claim("role", "USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied to payment payment-1"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getById_returnsInternalServerErrorWithErrorResponseForUnexpectedException() throws Exception {
        when(paymentService.getById("payment-1", "user-1", false))
                .thenThrow(new IllegalStateException("boom - some unexpected internal detail"));

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void list_returnsBadRequestWithErrorResponseWhenStatusParamInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "NOT_A_REAL_STATUS")
                        .with(jwt().jwt(j -> j.claim("sub", "user-1").claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
