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
        return toTransactionResponse(txn, wallet.getId());
    }

    @Transactional
    public TransactionResponse transfer(UUID senderUserId,
                                        TransferRequest request,
                                        String idempotencyKey) {

        // 1. idempotency check — must happen before anything else
        // but senderWallet doesn't exist yet, so we find the wallet separately
        // just to get its ID for the response mapping
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            // we need the caller's wallet ID for direction mapping
            // so look it up here — it's a simple read, no lock needed
            Wallet callerWallet = getWalletByUserId(senderUserId);
            return toTransactionResponse(existing.get(), callerWallet.getId());
        }

        // 2. resolve sender and recipient users
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

        // 3. get wallet IDs first — then lock in consistent UUID order
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

        // 4. balance check — after lock, so balance is guaranteed current
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new WalletException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }

        // 5. debit sender, credit recipient
        senderWallet.setBalance(
                senderWallet.getBalance().subtract(request.getAmount()));
        recipientWallet.setBalance(
                recipientWallet.getBalance().add(request.getAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(recipientWallet);

        // 6. record the transaction
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

        // senderWallet.getId() — correct variable, exists at this point
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
                // caller sent money — counterparty is the recipient
                counterpartyName  = txn.getToWallet().getUser().getName();
                counterpartyEmail = txn.getToWallet().getUser().getEmail();
                direction = "SENT";
            } else {
                // caller received money — counterparty is the sender
                counterpartyName  = txn.getFromWallet().getUser().getName();
                counterpartyEmail = txn.getFromWallet().getUser().getEmail();
                direction = "RECEIVED";
            }
        } else if (txn.getType() == TransactionType.CREDIT) {
            direction = "RECEIVED";
            counterpartyName = "System";   // credited by the system, no real counterparty
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
}