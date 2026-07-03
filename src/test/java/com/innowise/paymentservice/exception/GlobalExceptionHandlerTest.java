package com.innowise.paymentservice.exception;

import com.innowise.paymentservice.DisableMongock;
import com.innowise.paymentservice.controller.PaymentController;
import com.innowise.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getById_returnsNotFoundWithErrorResponseWhenPaymentMissing() throws Exception {
        when(paymentService.getById(eq("payment-404"), eq("user-1"), eq(false)))
                .thenThrow(new PaymentNotFoundException("Payment not found: payment-404"));

        mockMvc.perform(get("/api/v1/payments/payment-404")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found: payment-404"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-404"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getById_returnsForbiddenWithErrorResponseWhenAccessDenied() throws Exception {
        when(paymentService.getById(eq("payment-1"), eq("user-2"), eq(false)))
                .thenThrow(new PaymentAccessDeniedException("Access denied to payment payment-1"));

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("X-User-Id", "user-2")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied to payment payment-1"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getById_returnsServiceUnavailableWithErrorResponseWhenGatewayFails() throws Exception {
        when(paymentService.getById(eq("payment-1"), eq("user-1"), eq(false)))
                .thenThrow(new PaymentGatewayException("External payment gateway unavailable",
                        new RuntimeException("timeout")));

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("External payment gateway unavailable"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createPayment_returnsBadRequestWithErrorResponseWhenOrderIdBlank() throws Exception {
        String requestBody = "{\"orderId\":\"\",\"paymentAmount\":10.00}";

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("orderId")))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(paymentService, never()).create(any(), any());
    }

    @Test
    void getById_returnsInternalServerErrorWithErrorResponseForUnexpectedException() throws Exception {
        when(paymentService.getById(eq("payment-1"), eq("user-1"), eq(false)))
                .thenThrow(new IllegalStateException("boom - some unexpected internal detail"));

        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createPayment_returnsBadRequestWithErrorResponseWhenBodyMalformed() throws Exception {
        String malformedJson = "{\"orderId\":\"order-1\", \"paymentAmount\":}";

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(paymentService, never()).create(any(), any());
    }

    @Test
    void getById_returnsBadRequestWithErrorResponseWhenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payment-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("X-User-Id")))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/payment-1"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(paymentService, never()).getById(any(), any(), anyBoolean());
    }

    @Test
    void list_returnsBadRequestWithErrorResponseWhenStatusParamInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "NOT_A_REAL_STATUS")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
