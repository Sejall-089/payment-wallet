package com.wallet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter transferSuccessCounter(MeterRegistry registry) {
        return Counter.builder("wallet.transfer.success")
                .description("Number of successful transfers")
                .register(registry);
    }

    @Bean
    public Counter transferFailureCounter(MeterRegistry registry) {
        return Counter.builder("wallet.transfer.failure")
                .description("Number of failed transfers")
                .register(registry);
    }

    @Bean
    public Counter rateLimitHitCounter(MeterRegistry registry) {
        return Counter.builder("wallet.rate_limit.hit")
                .description("Number of requests rejected by rate limiter")
                .register(registry);
    }
}