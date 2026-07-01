package com.innowise.paymentservice;

import org.springframework.boot.SpringApplication;

public class TestPaymentserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
