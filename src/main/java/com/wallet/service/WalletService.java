package com.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.request.TransferRequest;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.entity.enums.TransactionStatus;
import com.wallet.entity.enums.TransactionType;
import com.wallet.event.TransactionEvent;
import com.wallet.exception.WalletException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher eventPublisher;
    private final Counter transferSuccessCounter;


    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public WalletResponse getBalance(UUID userId) {
        String cacheKey = "walletBalance::" + userId.toString();

        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for balance | userId: {}", userId);
                return objectMapper.readValue(cached, WalletResponse.class);
            }
        } catch (Exception e) {
            log.warn("Cache read failed for userId: {} | reason: {}", userId, e.getMessage());
        }

        Wallet wallet = getWalletByUserId(userId);
        WalletResponse response = toWalletResponse(wallet);

        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(cacheKey, json, 10, TimeUnit.MINUTES);
            log.debug("Balance cached for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Cache write failed for userId: {} | reason: {}", userId, e.getMessage());
        }

        return response;
    }

    @Transactional
    public TransactionResponse credit(UUID userId, java.math.BigDecimal amount,
                                      String description) {
        Wallet wallet = walletRepository
                .findByIdWithLock(getWalletByUserId(userId).getId())
                .orElseThrow(() -> new WalletException("Wallet not found",
                        HttpStatus.NOT_FOUND));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .toWallet(wallet)
                .amount(amount)
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build();

        transactionRepository.save(txn);
        evictBalanceCache(userId);

        log.info("Credit completed | userId: {} | amount: {} | txnId: {}",
                userId, amount, txn.getId());

        return toTransactionResponse(txn, wallet.getId());
    }

    @Transactional
    public TransactionResponse transfer(UUID senderUserId,
                                        TransferRequest request,
                                        String idempotencyKey) {

        // 1. idempotency check
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate transfer request | idempotencyKey: {} | returning existing txn",
                    idempotencyKey);
            Wallet callerWallet = getWalletByUserId(senderUserId);
            return toTransactionResponse(existing.get(), callerWallet.getId());
        }

        log.info("Transfer initiated | sender: {} | recipient: {} | amount: {}",
                senderUserId, request.getToEmail(), request.getAmount());

        // 2. resolve sender and recipient
        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new WalletException("Sender not found",
                        HttpStatus.NOT_FOUND));

        User recipient = userRepository.findByEmail(request.getToEmail())
                .orElseThrow(() -> new WalletException("Recipient not found",
                        HttpStatus.NOT_FOUND));

        if (sender.getId().equals(recipient.getId())) {
            throw new WalletException("Cannot transfer to yourself",
                    HttpStatus.BAD_REQUEST);
        }

        // 3. lock in consistent UUID order
        UUID senderWalletId    = getWalletByUserId(sender.getId()).getId();
        UUID recipientWalletId = getWalletByUserId(recipient.getId()).getId();

        Wallet senderWallet, recipientWallet;
        if (senderWalletId.compareTo(recipientWalletId) < 0) {
            senderWallet    = walletRepository.findByIdWithLock(senderWalletId).orElseThrow();
            recipientWallet = walletRepository.findByIdWithLock(recipientWalletId).orElseThrow();
        } else {
            recipientWallet = walletRepository.findByIdWithLock(recipientWalletId).orElseThrow();
            senderWallet    = walletRepository.findByIdWithLock(senderWalletId).orElseThrow();
        }

        // 4. balance check
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance | userId: {} | balance: {} | requested: {}",
                    senderUserId, senderWallet.getBalance(), request.getAmount());
            throw new WalletException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }

        // 5. debit and credit
        senderWallet.setBalance(
                senderWallet.getBalance().subtract(request.getAmount()));
        recipientWallet.setBalance(
                recipientWallet.getBalance().add(request.getAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(recipientWallet);

        // 6. record transaction
        Transaction txn = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromWallet(senderWallet)
                .toWallet(recipientWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription())
                .build();

        transactionRepository.save(txn);

        // 7. evict cache for both wallets
        evictBalanceCache(senderUserId, recipient.getId());

        // 8. publish Kafka event
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(txn.getId())
                .senderUserId(senderUserId)
                .senderEmail(sender.getEmail())
                .recipientUserId(recipient.getId())
                .recipientEmail(recipient.getEmail())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .description(request.getDescription())
                .occurredAt(txn.getCreatedAt())
                .build();

        eventPublisher.publishTransactionEvent(event);

        // 9. increment success metric
        transferSuccessCounter.increment();

        log.info("Transfer completed | txnId: {} | amount: {} | from: {} | to: {}",
                txn.getId(), request.getAmount(),
                sender.getEmail(), recipient.getEmail());

        return toTransactionResponse(txn, senderWallet.getId());
    }

    public List<TransactionResponse> getHistory(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return transactionRepository
                .findByWalletIdWithDetails(wallet.getId())
                .stream()
                .map(txn -> toTransactionResponse(txn, wallet.getId()))
                .collect(Collectors.toList());
    }

    // --- private helpers ---

    private Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletException("Wallet not found",
                        HttpStatus.NOT_FOUND));
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

    private TransactionResponse toTransactionResponse(Transaction txn,
                                                      UUID callerWalletId) {
        String counterpartyName  = null;
        String counterpartyEmail = null;
        String direction         = null;

        if (txn.getType() == TransactionType.TRANSFER) {
            boolean isSender = txn.getFromWallet().getId().equals(callerWalletId);
            if (isSender) {
                counterpartyName  = txn.getToWallet().getUser().getName();
                counterpartyEmail = txn.getToWallet().getUser().getEmail();
                direction = "SENT";
            } else {
                counterpartyName  = txn.getFromWallet().getUser().getName();
                counterpartyEmail = txn.getFromWallet().getUser().getEmail();
                direction = "RECEIVED";
            }
        } else if (txn.getType() == TransactionType.CREDIT) {
            direction = "RECEIVED";
            counterpartyName = "System";
        } else if (txn.getType() == TransactionType.DEBIT) {
            direction = "SENT";
            counterpartyName = "System";
        }

        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .counterpartyName(counterpartyName)
                .counterpartyEmail(counterpartyEmail)
                .direction(direction)
                .build();
    }

    private void evictBalanceCache(UUID... userIds) {
        for (UUID userId : userIds) {
            stringRedisTemplate.delete("walletBalance::" + userId.toString());
            log.debug("Balance cache evicted for userId: {}", userId);
        }
    }
}