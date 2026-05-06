package com.adil.bankservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    private final ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();

    private final List<TransactionEvent> auditLog = Collections.synchronizedList(new ArrayList<>());

    private final Set<String> processedIdempotencyKeys = ConcurrentHashMap.newKeySet();

    // helper methods

    private String newEventId() {
        return UUID.randomUUID().toString();
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private Account requireAccount(Long id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new IllegalArgumentException("Account " + id + " doesn't exist");
        }
        return account;
    }

    private void validateAccountIds(Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
    }

    public void createAccount(Long id, BigDecimal initialBalance) {

        accounts.put(id, new Account(id, initialBalance));
        log.info("Account created: id={} initialBalance={}", id, initialBalance);
    }

    public BigDecimal getTotalBalance() {
        return accounts.values().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<TransactionEvent> getAuditLog() {
        synchronized (auditLog) {
            return new ArrayList<>(auditLog); // defensive copy
        }
    }

    public BigDecimal getAccountBalance(Long accountId) {
        Account account = requireAccount(accountId);
        return account.getBalance();
    }
    // main transfer method with proper locking to ensure thread safety

    public void transfer(String idempotencyKey, Long fromId, Long toId, BigDecimal amount) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        // Atomically check-and-add. If add() returns false, key was already there.
        if (!processedIdempotencyKeys.add(idempotencyKey)) {
            log.info("Idempotency hit: key={} — skipping duplicate transfer", idempotencyKey);
            return;
        }

        log.debug("Transfer initiated: key={} from={} to={} amount={}",
                idempotencyKey, fromId, toId, amount);

        try {
            validateAmount(amount);
            validateAccountIds(fromId, toId);

            Account fromAccount = requireAccount(fromId);
            Account toAccount = requireAccount(toId);

            // Lock ordering: always lock the lower-ID account first to prevent deadlock
            Account first = fromAccount.getId() < toAccount.getId() ? fromAccount : toAccount;
            Account second = first == fromAccount ? toAccount : fromAccount;

            first.getLock().lock();
            try {
                second.getLock().lock();
                try {
                    fromAccount.debit(amount); // throws InsufficientFundsException if not enough
                    toAccount.credit(amount);
                    auditLog.add(new TransactionEvent(
                            newEventId(), Instant.now(), TransactionEvent.EventType.TRANSFER,
                            fromId, toId, amount,
                            TransactionEvent.EventStatus.SUCCESS, null));

                    log.info("Transfer completed: from={} to={} amount={}", fromId, toId, amount);
                } finally {
                    second.getLock().unlock();
                }
            } finally {
                first.getLock().unlock();
            }
        } catch (IllegalArgumentException e) {
            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.TRANSFER,
                    fromId, toId, amount,
                    TransactionEvent.EventStatus.FAILED, e.getMessage()));

            log.warn("Transfer rejected: from={} to={} amount={} reason={}",
                    fromId, toId, amount, e.getMessage());
            throw e;
        } catch (InsufficientFundsException e) {

            auditLog.add(new TransactionEvent(
                
                    newEventId(), Instant.now(), TransactionEvent.EventType.TRANSFER,
                    fromId, toId, amount,
                    TransactionEvent.EventStatus.FAILED, e.getMessage()));
            log.warn("Transfer rejected (insufficient funds): from={} amount={}", fromId, amount);
            throw e;
        }
    }

    public void deposit(Long accountId, BigDecimal amount) {
        log.debug("Deposit initiated: accountId={} amount={}", accountId, amount);

        try {
            validateAmount(amount);
            Account account = requireAccount(accountId);
            account.credit(amount);

            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.DEPOSIT,
                    null, accountId, amount,
                    TransactionEvent.EventStatus.SUCCESS, null));

            log.info("Deposit completed: accountId={} amount={}", accountId, amount);
        } catch (IllegalArgumentException e) {

            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.DEPOSIT,
                    null, accountId, amount,
                    TransactionEvent.EventStatus.FAILED, e.getMessage()));

            log.warn("Deposit rejected: accountId={} amount={} reason={}",
                    accountId, amount, e.getMessage());
            throw e;
        }
    }

    public void withdraw(Long accountId, BigDecimal amount) {
        log.debug("Withdraw initiated: accountId={} amount={}", accountId, amount);

        try {
            validateAmount(amount);
            Account account = requireAccount(accountId);
            account.debit(amount); // handles its own lock + insufficient funds check

            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.WITHDRAW,
                    accountId, null, amount,
                    TransactionEvent.EventStatus.SUCCESS, null));

            log.info("Withdraw completed: accountId={} amount={}", accountId, amount);

        } catch (IllegalArgumentException e) {

            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.WITHDRAW,
                    accountId, null, amount,
                    TransactionEvent.EventStatus.FAILED, e.getMessage()));

            log.warn("Withdraw rejected: accountId={} amount={} reason={}",
                    accountId, amount, e.getMessage());
            throw e;
        } catch (InsufficientFundsException e) {

            auditLog.add(new TransactionEvent(
                    newEventId(), Instant.now(), TransactionEvent.EventType.WITHDRAW,
                    accountId, null, amount,
                    TransactionEvent.EventStatus.FAILED, e.getMessage()));

            log.warn("Withdraw rejected (insufficient funds): accountId={} amount={}", accountId, amount);
            throw e;
        }

    }

}
