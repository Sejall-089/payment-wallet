package com.wallet.service;

import com.wallet.dto.request.TransferRequest;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.entity.enums.TransactionStatus;
import com.wallet.entity.enums.TransactionType;
import com.wallet.exception.WalletException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletResponse getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return toWalletResponse(wallet);
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
        return toTransactionResponse(txn);
    }

    @Transactional
    public TransactionResponse transfer(UUID senderUserId,
                                        TransferRequest request,
                                        String idempotencyKey) {
        // 1. idempotency check — if we've seen this key, return the original result
        if (idempotencyKey != null) {
            var existing = transactionRepository
                    .findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toTransactionResponse(existing.get());
            }
        }

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

        // 3. lock both wallets — always lock in consistent ID order to prevent deadlock
        UUID senderId = getWalletByUserId(sender.getId()).getId();
        UUID recipientId = getWalletByUserId(recipient.getId()).getId();

        Wallet senderWallet, recipientWallet;
        if (senderId.compareTo(recipientId) < 0) {
            senderWallet = walletRepository.findByIdWithLock(senderId)
                    .orElseThrow();
            recipientWallet = walletRepository.findByIdWithLock(recipientId)
                    .orElseThrow();
        } else {
            recipientWallet = walletRepository.findByIdWithLock(recipientId)
                    .orElseThrow();
            senderWallet = walletRepository.findByIdWithLock(senderId)
                    .orElseThrow();
        }

        // 4. balance check
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
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
        return toTransactionResponse(txn);
    }

    public List<TransactionResponse> getHistory(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return transactionRepository
                .findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        wallet.getId(), wallet.getId())
                .stream()
                .map(this::toTransactionResponse)
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

    private TransactionResponse toTransactionResponse(Transaction txn) {
        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}