package com.innowise.paymentservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.TestcontainersConfiguration;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentGatewayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@StubJwksUri
class ExternalPaymentClientTest {

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("payment.external-api.url", () -> "http://localhost:" + wireMock.port());
        registry.add("payment.external-api.read-timeout-ms", () -> "2000");
    }

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Autowired
    private ExternalPaymentClient externalPaymentClient;

    @Test
    void charge_whenExternalApiReturnsEvenNumber_returnsSuccess() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(okForContentType("text/plain", "42\n")));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        PaymentStatus result = externalPaymentClient.charge(payment);

        assertThat(result).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void charge_whenExternalApiReturnsOddNumber_returnsFailed() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(okForContentType("text/plain", "43\n")));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        PaymentStatus result = externalPaymentClient.charge(payment);

        assertThat(result).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void charge_whenExternalApiReturnsServerError_throwsPaymentGatewayException() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(500)));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        assertThatThrownBy(() -> externalPaymentClient.charge(payment))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void charge_whenExternalApiIsSlow_throwsPaymentGatewayException() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(okForContentType("text/plain", "42\n").withFixedDelay(4000)));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        assertThatThrownBy(() -> externalPaymentClient.charge(payment))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void charge_whenExternalApiReturnsBlankBody_throwsPaymentGatewayException() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(okForContentType("text/plain", "   ")));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        assertThatThrownBy(() -> externalPaymentClient.charge(payment))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void charge_whenExternalApiReturnsEmptyBody_throwsPaymentGatewayException() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(200)));

        PaymentDocument payment = new PaymentDocument(null, "order-1", "user-1",
                PaymentStatus.PENDING, new BigDecimal("10.00"), false, Instant.now(), Instant.now());

        assertThatThrownBy(() -> externalPaymentClient.charge(payment))
                .isInstanceOf(PaymentGatewayException.class);
    }
}
