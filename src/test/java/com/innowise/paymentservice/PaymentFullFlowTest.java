package com.innowise.paymentservice;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.event.PaymentCompletedEvent;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@StubJwksUri
class PaymentFullFlowTest {

    private static final String TOPIC = "payment-events";

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("payment.external-api.url", () -> "http://localhost:" + wireMock.port());
        registry.add("payment.processor.poll-interval-ms", () -> "200");
        registry.add("payment.outbox.poll-interval-ms", () -> "200");
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void createThenGetById_persistsAndReturnsSamePayment() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okForContentType("text/plain", "42\n")));

        PaymentRequest request = new PaymentRequest("order-flow-1", new BigDecimal("42.50"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().jwt(j -> j.claim("sub", "user-flow-1").claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PaymentResponse.class);
        assertThat(created.status().name()).isEqualTo("PENDING");

        awaitStatus(created.id(), PaymentStatus.SUCCESS);
    }

    /**
     * Verifies the real shape of {@code payment-events} messages this service produces.
     *
     * <p><b>Manual sync note:</b> orderservice's consumer-side test
     * ({@code orderservice/src/test/java/com/innowise/orderservice/kafka/PaymentEventListenerIntegrationTest})
     * hand-writes a JSON literal mirroring {@link PaymentCompletedEvent} instead of consuming a
     * message actually produced here — there is no automated contract between the two. If this
     * test's assertions on {@link PaymentCompletedEvent}'s shape ever change, update that JSON
     * literal to match.
     */
    @Test
    void createThenResolve_publishesPaymentEventToKafka() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okForContentType("text/plain", "42\n")));

        PaymentRequest request = new PaymentRequest("order-flow-2", new BigDecimal("10.00"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().jwt(j -> j.claim("sub", "user-flow-2").claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PaymentResponse.class);

        awaitStatus(created.id(), PaymentStatus.SUCCESS);

        try (Consumer<String, PaymentCompletedEvent> consumer = createEventConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            ConsumerRecord<String, PaymentCompletedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo("order-flow-2");
            assertThat(record.value()).isEqualTo(new PaymentCompletedEvent("order-flow-2", PaymentStatus.SUCCESS));
        }
    }

    private void awaitStatus(String paymentId, PaymentStatus expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var doc = paymentRepository.findById(paymentId).orElseThrow();
            if (doc.getStatus() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment " + paymentId + " did not reach status " + expected
                + " within timeout; current status: "
                + paymentRepository.findById(paymentId).orElseThrow().getStatus());
    }

    private Consumer<String, PaymentCompletedEvent> createEventConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafkaContainer.getBootstrapServers(), "payment-full-flow-test", "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.innowise.paymentservice.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentCompletedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<String, PaymentCompletedEvent>(props).createConsumer();
    }
}
