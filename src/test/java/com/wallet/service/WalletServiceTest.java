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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // ✅ fix unnecessary stubbing
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;

    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @InjectMocks private WalletService walletService;

    private User sender;
    private User recipient;
    private Wallet senderWallet;
    private Wallet recipientWallet;

    @BeforeEach
    void setUp() {
        // ✅ FIX: mock cache manager + evict
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        doNothing().when(cache).evict(any());

        sender = User.builder()
                .id(UUID.randomUUID())
                .name("Rahul")
                .email("rahul@test.com")
                .password("hashed")
                .build();

        recipient = User.builder()
                .id(UUID.randomUUID())
                .name("Priya")
                .email("priya@test.com")
                .password("hashed")
                .build();

        senderWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(sender)
                .balance(new BigDecimal("1000.0000"))
                .currency("INR")
                .build();

        recipientWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(recipient)
                .balance(new BigDecimal("500.0000"))
                .currency("INR")
                .build();
    }

    // --- getBalance ---

    @Test
    void getBalance_returnsCorrectBalance() {
        when(walletRepository.findByUserId(sender.getId()))
                .thenReturn(Optional.of(senderWallet));

        WalletResponse response = walletService.getBalance(sender.getId());

        assertThat(response.getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(response.getCurrency()).isEqualTo("INR");
    }

    @Test
    void getBalance_throwsWhenWalletNotFound() {
        when(walletRepository.findByUserId(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(UUID.randomUUID()))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("Wallet not found");
    }

    // --- transfer ---

    @Test
    void transfer_successfullyMovesMoneyBetweenWallets() {
        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("300.0000"));
        request.setDescription("test transfer");

        when(transactionRepository.findByIdempotencyKey("key-001"))
                .thenReturn(Optional.empty());

        when(userRepository.findById(sender.getId()))
                .thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("priya@test.com"))
                .thenReturn(Optional.of(recipient));

        when(walletRepository.findByUserId(sender.getId()))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(recipient.getId()))
                .thenReturn(Optional.of(recipientWallet));

        when(walletRepository.findByIdWithLock(senderWallet.getId()))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(recipientWallet.getId()))
                .thenReturn(Optional.of(recipientWallet));

        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            return Transaction.builder()
                    .id(UUID.randomUUID())
                    .fromWallet(t.getFromWallet())
                    .toWallet(t.getToWallet())
                    .amount(t.getAmount())
                    .type(t.getType())
                    .status(t.getStatus())
                    .idempotencyKey(t.getIdempotencyKey())
                    .description(t.getDescription())
                    .build();
        });

        TransactionResponse response = walletService.transfer(
                sender.getId(), request, "key-001");

        assertThat(senderWallet.getBalance())
                .isEqualByComparingTo("700.0000");
        assertThat(recipientWallet.getBalance())
                .isEqualByComparingTo("800.0000");

        verify(walletRepository, times(2)).save(any());
        verify(transactionRepository).save(any());
    }

    @Test
    void transfer_throwsWhenInsufficientBalance() {
        when(walletRepository.findByIdWithLock(senderWallet.getId()))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdWithLock(recipientWallet.getId()))
                .thenReturn(Optional.of(recipientWallet));

        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("9999.0000"));

        when(transactionRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(sender.getId()))
                .thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("priya@test.com"))
                .thenReturn(Optional.of(recipient));
        when(walletRepository.findByUserId(sender.getId()))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(recipient.getId()))
                .thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() ->
                walletService.transfer(sender.getId(), request, "key-002"))
                .isInstanceOf(WalletException.class);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void transfer_throwsWhenTransferToSelf() {
        TransferRequest request = new TransferRequest();
        request.setToEmail(sender.getEmail());
        request.setAmount(new BigDecimal("100"));

        when(transactionRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(sender.getId()))
                .thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(sender.getEmail()))
                .thenReturn(Optional.of(sender));

        assertThatThrownBy(() ->
                walletService.transfer(sender.getId(), request, "key-003"))
                .isInstanceOf(WalletException.class);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void transfer_returnsExistingTransactionForDuplicateIdempotencyKey() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .fromWallet(senderWallet)
                .toWallet(recipientWallet)
                .amount(new BigDecimal("100"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey("duplicate-key")
                .build();

        when(transactionRepository.findByIdempotencyKey("duplicate-key"))
                .thenReturn(Optional.of(existing));
        when(walletRepository.findByUserId(sender.getId()))
                .thenReturn(Optional.of(senderWallet));

        TransactionResponse response = walletService.transfer(
                sender.getId(), new TransferRequest(), "duplicate-key");

        assertThat(response.getTransactionId()).isEqualTo(existing.getId());

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}