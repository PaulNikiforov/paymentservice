package com.innowise.paymentservice.config;

import com.innowise.paymentservice.event.PaymentCompletedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Strongly-typed {@code KafkaTemplate<String, PaymentCompletedEvent>} built on Spring Boot's
 * auto-configured {@link ProducerFactory} (serializers/bootstrap-servers come from
 * {@code application.yaml}'s {@code spring.kafka.*}). Declaring the bean explicitly lets the
 * outbox publisher depend on a typed template and makes the auto-configured
 * {@code KafkaTemplate<?,?>} back off.
 *
 * <p>This relies on there being exactly one auto-configured {@code ProducerFactory} bean in the
 * context, resolved here by raw type regardless of its declared generic parameters. If a second
 * producer/event type is ever introduced, this method's parameter will need an explicit
 * {@code @Qualifier} (or a dedicated, non-autoconfigured {@code ProducerFactory} bean) to avoid
 * ambiguity.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate(
            ProducerFactory<String, PaymentCompletedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
