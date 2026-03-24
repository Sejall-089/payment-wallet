package com.wallet.dto.response;

import com.wallet.entity.enums.TransactionStatus;
import com.wallet.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private LocalDateTime createdAt;

    private String counterpartyName;   // their display name
    private String counterpartyEmail;  // their email
    private String direction;          // "SENT" or "RECEIVED" — from caller's perspective
}