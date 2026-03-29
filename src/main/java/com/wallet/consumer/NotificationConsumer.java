package com.wallet.consumer;

import com.wallet.config.KafkaConfig;
import com.wallet.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationConsumer {

    @KafkaListener(
            topics = KafkaConfig.TRANSACTION_TOPIC,
            groupId = "notification-group"
    )
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("--- NOTIFICATION CONSUMER ---");
        log.info("Sender: {} | sent ₹{} | to: {}",
                event.getSenderEmail(),
                event.getAmount(),
                event.getRecipientEmail());
        log.info("Description: {}", event.getDescription());
        log.info("SMS to {}: Your transfer of ₹{} was successful",
                event.getSenderEmail(), event.getAmount());
        log.info("SMS to {}: You received ₹{} from {}",
                event.getRecipientEmail(),
                event.getAmount(),
                event.getSenderEmail());
    }
}