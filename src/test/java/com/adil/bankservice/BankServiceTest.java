package com.adil.bankservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class BankServiceTest {

    private BankService bankService;

    @BeforeEach
    void setUp() {
        bankService = new BankService();
        bankService.createAccount(1L, new BigDecimal("1000.00"));
        bankService.createAccount(2L, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Successful transfer updates both account balances correctly")
    void transfer_shouldUpdateBalances_whenValid() {
        bankService.transfer(UUID.randomUUID().toString(), 1L, 2L, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("900.00"), bankService.getAccountBalance(1L));
        assertEquals(new BigDecimal("1100.00"), bankService.getAccountBalance(2L));
    }

    @Test
    @DisplayName("Transfer rejects negative amount with IllegalArgumentException")
    void transfer_shouldThrow_whenAmountIsNegative() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bankService.transfer(UUID.randomUUID().toString(), 1L, 2L, new BigDecimal("-50")));
        assertTrue(e.getMessage().contains("positive"));
    }

    @Test
    @DisplayName("Transfer rejects same account transfers")
    void transfer_shouldThrow_whenSameAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> bankService.transfer(UUID.randomUUID().toString(), 1L, 1L, new BigDecimal("50")));
    }

    @Test
    @DisplayName("Transfer rejects non-existent account")
    void transfer_shouldThrow_whenAccountDoesNotExist() {
        assertThrows(IllegalArgumentException.class,
                () -> bankService.transfer(UUID.randomUUID().toString(), 1L, 999L, new BigDecimal("100")));
    }

    @Test
    @DisplayName("Transfer rejects when from-account has insufficient funds")
    void transfer_shouldThrow_whenInsufficientFunds() {
        assertThrows(InsufficientFundsException.class,
                () -> bankService.transfer(UUID.randomUUID().toString(), 1L, 2L, new BigDecimal("9999")));
    }

    @Test
    @DisplayName("Idempotency: same key twice does not double-charge")
    void transfer_shouldBeIdempotent_whenSameKeyUsedTwice() {
        String key = "duplicate-key";

        bankService.transfer(key, 1L, 2L, new BigDecimal("100.00"));
        bankService.transfer(key, 1L, 2L, new BigDecimal("100.00")); // duplicate

        assertEquals(new BigDecimal("900.00"), bankService.getAccountBalance(1L));
        assertEquals(new BigDecimal("1100.00"), bankService.getAccountBalance(2L));
    }

    @Test
    @DisplayName("Concurrent transfers preserve total balance")
    void transfer_shouldPreserveTotal_underConcurrency() throws InterruptedException {
        int threadCount = 10;
        int transfersPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < transfersPerThread; j++) {
                        long from = ThreadLocalRandom.current().nextInt(1, 3); // 1 or 2
                        long to = (from == 1L) ? 2L : 1L;
                        BigDecimal amount = new BigDecimal(ThreadLocalRandom.current().nextInt(1, 50));
                        try {
                            bankService.transfer(UUID.randomUUID().toString(), from, to, amount);
                        } catch (IllegalArgumentException | InsufficientFundsException ignored) {
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(new BigDecimal("2000.00"), bankService.getTotalBalance());
    }

    @Test
    @DisplayName("Audit log records every operation with correct status")
    void auditLog_shouldRecordOperations() {
        int initialSize = bankService.getAuditLog().size();

        bankService.transfer(UUID.randomUUID().toString(), 1L, 2L, new BigDecimal("100"));

        try {
            bankService.transfer(UUID.randomUUID().toString(), 1L, 2L, new BigDecimal("9999"));
        } catch (InsufficientFundsException ignored) {
        }

        var log = bankService.getAuditLog();
        assertEquals(initialSize + 2, log.size());

        // Last 2 events are ours
        var latest = log.subList(log.size() - 2, log.size());
        assertEquals(TransactionEvent.EventStatus.SUCCESS, latest.get(0).status());
        assertEquals(TransactionEvent.EventStatus.FAILED, latest.get(1).status());
    }
}