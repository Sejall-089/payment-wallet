package com.wallet.controller;

import com.wallet.dto.request.TransferRequest;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletResponse;
import com.wallet.exception.RateLimitException;
import com.wallet.exception.WalletException;
import com.wallet.service.RateLimiterService;
import com.wallet.service.WalletService;
import com.wallet.util.SecurityUtils;
import io.micrometer.core.instrument.Counter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final RateLimiterService rateLimiterService;
    private final Counter transferFailureCounter;
    private final Counter rateLimitHitCounter;

    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(walletService.getBalance(userId));
    }

    @PostMapping("/credit")
    public ResponseEntity<TransactionResponse> credit(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                walletService.credit(userId, amount, description));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        if (!rateLimiterService.isAllowed(userId)) {
            rateLimitHitCounter.increment();
            log.warn("Rate limit exceeded | userId: {}", userId);
            throw new RateLimitException(
                    "Transfer rate limit exceeded. Maximum 5 transfers per minute.");
        }

        try {
            return ResponseEntity.ok(
                    walletService.transfer(userId, request, idempotencyKey));
        } catch (WalletException e) {
            transferFailureCounter.increment();
            throw e;
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(walletService.getHistory(userId));
    }
}