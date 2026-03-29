package com.wallet.controller;

import com.wallet.dto.request.TransferRequest;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletResponse;
import com.wallet.service.WalletService;
import com.wallet.util.CacheConstants;
import com.wallet.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/redis-ping")
    public ResponseEntity<String> redisPing() {
        try {
            redisTemplate.opsForValue().set("ping", "pong");
            String val = redisTemplate.opsForValue().get("ping");
            return ResponseEntity.ok("Redis working: " + val);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Redis error: " + e.getMessage());
        }
    }

    // temporary: userId passed as header until JWT is wired on Day 3
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
            @RequestHeader(value = "Idempotency-Key")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                walletService.transfer(userId, request, idempotencyKey));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(walletService.getHistory(userId));
    }
}
