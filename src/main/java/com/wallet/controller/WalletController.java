package com.wallet.controller;

import com.wallet.dto.request.TransferRequest;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletResponse;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    // temporary: userId passed as header until JWT is wired on Day 3
    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(walletService.getBalance(userId));
    }

    @PostMapping("/credit")
    public ResponseEntity<TransactionResponse> credit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(
                walletService.credit(userId, amount, description));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(
                walletService.transfer(userId, request, idempotencyKey));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getHistory(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(walletService.getHistory(userId));
    }
}