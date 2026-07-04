package com.innowise.paymentservice.config;

import com.innowise.paymentservice.StubExternalPaymentApi;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@StubExternalPaymentApi
@StubJwksUri
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_shouldBePublicAndContainPaymentContract() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payments']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments/users/{userId}/summary']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments/summary']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }
}
