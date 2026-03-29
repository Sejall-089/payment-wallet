package com.wallet.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TRANSACTION_TOPIC = "wallet.transaction.events";

    @Bean
    public NewTopic transactionTopic() {
        return TopicBuilder.name(TRANSACTION_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}