package com.innowise.paymentservice.config;

import com.innowise.paymentservice.event.PaymentCompletedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    @Bean
    KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate(
            ProducerFactory<String, PaymentCompletedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
