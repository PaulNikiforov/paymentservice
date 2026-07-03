package com.innowise.paymentservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@StubExternalPaymentApi
class PaymentFullFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenGetById_persistsAndReturnsSamePayment() throws Exception {
        PaymentRequest request = new PaymentRequest("order-flow-1", new BigDecimal("42.50"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "user-flow-1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PaymentResponse.class);

        MvcResult getResult = mockMvc.perform(get("/api/v1/payments/{id}", created.id())
                        .header("X-User-Id", "user-flow-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andReturn();

        PaymentResponse fetched = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), PaymentResponse.class);

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.orderId()).isEqualTo("order-flow-1");
        assertThat(fetched.userId()).isEqualTo("user-flow-1");
        assertThat(fetched.paymentAmount()).isEqualByComparingTo("42.50");
        assertThat(fetched.status()).isEqualTo(created.status());
        assertThat(fetched.status().name()).isEqualTo("PENDING");
    }
}
