/*
 * Copyright (c) 2024  Vladimir Marianciuc. All Rights Reserved.
 *
 * Project: STREAMING SERVICE APP
 * File: KafkaPaymentConsumer.java
 *
 */

package io.github.marianciuc.streamingservice.payment.kafka;

import io.github.marianciuc.streamingservice.payment.kafka.messages.InitializePaymentMessage;
import io.github.marianciuc.streamingservice.payment.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPaymentConsumer {

    private final TransactionService transactionService;

    @KafkaListener(topics = "start-payment-processing" , groupId = "spring.kafka.consumer.group-id")
    public void consume(InitializePaymentMessage message) {
        try {
            log.info("Consuming payment initialization message for order: {}", message.getOrderId());
            transactionService.initializeTransaction(message);
            log.info("Successfully processed payment initialization for order: {}", message.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process payment initialization message for order: {}. Error: {}", 
                    message.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Payment processing failed for order: " + message.getOrderId(), e);
        }
    }
}
