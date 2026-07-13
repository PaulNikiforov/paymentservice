package com.innowise.paymentservice.client;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentGatewayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ExternalPaymentClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "externalPaymentApi", fallbackMethod = "chargeFallback")
    public PaymentStatus charge(PaymentDocument payment) {
        String response = restClient.get()
                .uri("/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new")
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new PaymentGatewayException("External payment API returned an empty response", null);
        }

        int value = Integer.parseInt(response.trim());
        return value % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
    }

    private PaymentStatus chargeFallback(Throwable t) {
        throw new PaymentGatewayException("External payment API unavailable", t);
    }
}
