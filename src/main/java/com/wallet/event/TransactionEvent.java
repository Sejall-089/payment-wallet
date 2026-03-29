package com.wallet.event;

import com.wallet.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    private UUID transactionId;
    private UUID senderUserId;
    private String senderEmail;
    private UUID recipientUserId;
    private String recipientEmail;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private LocalDateTime occurredAt;
}