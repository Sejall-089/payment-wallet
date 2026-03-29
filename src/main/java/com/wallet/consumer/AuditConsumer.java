package com.wallet.consumer;

import com.wallet.config.KafkaConfig;
import com.wallet.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditConsumer {

    @KafkaListener(
            topics = KafkaConfig.TRANSACTION_TOPIC,
            groupId = "audit-group"
    )
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("--- AUDIT LOG ---");
        log.info("TxnId: {} | Type: {} | Amount: ₹{} | From: {} | To: {} | At: {}",
                event.getTransactionId(),
                event.getType(),
                event.getAmount(),
                event.getSenderEmail(),
                event.getRecipientEmail(),
                event.getOccurredAt());
    }
}