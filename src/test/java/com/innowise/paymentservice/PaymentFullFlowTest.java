package com.innowise.paymentservice;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.event.PaymentCompletedEvent;
import com.innowise.paymentservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full flow, triggered the way production traffic actually arrives (FIX-01): a {@code CREATE_ORDER}
 * event, not a direct REST call — payment creation has no REST entry point anymore.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@StubJwksUri
class PaymentFullFlowTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";

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
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    private KafkaProducer<String, String> orderEventsProducer;

    @BeforeEach
    void setUpProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        orderEventsProducer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDownProducer() {
        if (orderEventsProducer != null) {
            orderEventsProducer.close();
        }
    }

    @Test
    void createOrderEvent_persistsPendingThenResolvesPayment() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okForContentType("text/plain", "42\n")));

        publishCreateOrderEvent("order-flow-1", "user-flow-1", new BigDecimal("42.50"));

        PaymentDocument created = awaitPaymentForOrder("order-flow-1");
        assertThat(created.getUserId()).isEqualTo("user-flow-1");
        assertThat(created.getPaymentAmount()).isEqualByComparingTo("42.50");

        awaitStatus(created.getId(), PaymentStatus.SUCCESS);
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
    void createOrderEvent_publishesPaymentEventToKafka() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okForContentType("text/plain", "42\n")));

        publishCreateOrderEvent("order-flow-2", "user-flow-2", new BigDecimal("10.00"));

        PaymentDocument created = awaitPaymentForOrder("order-flow-2");
        awaitStatus(created.getId(), PaymentStatus.SUCCESS);

        try (Consumer<String, PaymentCompletedEvent> consumer = createPaymentEventConsumer()) {
            consumer.subscribe(List.of(PAYMENT_EVENTS_TOPIC));
            ConsumerRecord<String, PaymentCompletedEvent> consumerRecord =
                    KafkaTestUtils.getSingleRecord(consumer, PAYMENT_EVENTS_TOPIC, Duration.ofSeconds(20));

            assertThat(consumerRecord.key()).isEqualTo("order-flow-2");
            assertThat(consumerRecord.value()).isEqualTo(new PaymentCompletedEvent("order-flow-2", PaymentStatus.SUCCESS));
        }
    }

    private void publishCreateOrderEvent(String orderId, String userId, BigDecimal amount) throws Exception {
        String json = """
                {"orderId":"%s","userId":"%s","amount":%s}
                """.formatted(orderId, userId, amount);
        orderEventsProducer.send(new ProducerRecord<>(ORDER_EVENTS_TOPIC, orderId, json)).get();
        orderEventsProducer.flush();
    }

    private PaymentDocument awaitPaymentForOrder(String orderId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> paymentRepository.findByOrderId(orderId).isPresent());
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    private void awaitStatus(String paymentId, PaymentStatus expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                        .isEqualTo(expected));
    }

    private Consumer<String, PaymentCompletedEvent> createPaymentEventConsumer() {
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
