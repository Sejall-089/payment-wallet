package com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = "com.wallet")
public class PaymentWalletApplication {
    public static void main(String[] args) {
        System.out.println(">>> Starting application");
        SpringApplication.run(PaymentWalletApplication.class, args);
    }
}