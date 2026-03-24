package com.wallet.repository;

import com.wallet.entity.Transaction;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
            UUID fromWalletId, UUID toWalletId
    );

    @Query("SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromWallet fw " +
            "LEFT JOIN FETCH fw.user " +
            "LEFT JOIN FETCH t.toWallet tw " +
            "LEFT JOIN FETCH tw.user " +
            "WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId) " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByWalletIdWithDetails(@Param("walletId") UUID walletId);
}