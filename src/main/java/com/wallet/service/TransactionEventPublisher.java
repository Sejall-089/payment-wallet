package com.wallet.service;

import com.wallet.config.KafkaConfig;
import com.wallet.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static com.wallet.config.KafkaConfig.TRANSACTION_TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void publishTransactionEvent(TransactionEvent event) {
        // use transactionId as the message key
        // this ensures all events for the same transaction
        // go to the same partition — ordering guaranteed
        String messageKey = event.getTransactionId().toString();

        CompletableFuture<SendResult<String, TransactionEvent>> future =
                kafkaTemplate.send(TRANSACTION_TOPIC, messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // publish failed — log it, don't throw
                // transfer already succeeded in DB — don't fail the response
                log.error("Failed to publish transaction event for txn: {} — {}",
                        event.getTransactionId(), ex.getMessage());
            } else {
                log.info("Published transaction event — txnId: {} | partition: {} | offset: {}",
                        event.getTransactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}