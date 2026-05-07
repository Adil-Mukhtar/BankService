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
import java.util.concurrent.TimeUnit;

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
        if (amount == null || amount.compareTo(BankConfig.MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(BankConfig.MAX_TRANSFER_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum allowed");
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

    /**
     * Creates a new account with the given ID and initial balance.
     * 
     * @param id             the unique ID for the new account; must not already
     *                       exist
     * @param initialBalance the initial balance for the account; must be non-null
     *                       and non-negative
     */
    public void createAccount(Long id, BigDecimal initialBalance) {

        accounts.put(id, new Account(id, initialBalance));

        log.info("Account created: id={} initialBalance={}", id, initialBalance);

    }

    /**
     * Returns the total balance across all accounts. This is a snapshot at the
     * moment of the call and may not reflect concurrent updates that happen during
     * the calculation.
     * The method acquires locks on each account sequentially to read their
     * balances, so it may block if there are long-running transactions. If locks
     * cannot be acquired within the timeout,
     * a ServiceUnavailableException is thrown.
     * 
     * @return the total balance across all accounts
     */
    public BigDecimal getTotalBalance() {
        return accounts.values().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns a copy of the transaction audit log. The log contains all transfer,
     * deposit, and withdraw events with their details and status.
     * The returned list is a snapshot and modifications to it do not affect the
     * internal log. The internal log is thread-safe and can be concurrently updated
     * by ongoing transactions.
     * 
     * @return a list of TransactionEvent records representing the audit log of all
     *         transactions.
     */
    public List<TransactionEvent> getAuditLog() {
        synchronized (auditLog) {
            return new ArrayList<>(auditLog); // defensive copy
        }
    }

    /**
     * Returns the current balance of the specified account. This method acquires
     * the account's lock to ensure a consistent read, so it may block if there are
     * long-running transactions on that account. If the lock cannot be acquired
     * within the timeout, a ServiceUnavailableException is thrown.
     * This method is useful for testing and monitoring purposes to check the
     * balance of an account at a given moment.
     * 
     * @param accountId the ID of the account to check; must exist
     * @return the current balance of the specified account
     */
    public BigDecimal getAccountBalance(Long accountId) {
        Account account = requireAccount(accountId);
        return account.getBalance();
    }

    private void acquireLockOrFail(Account account) {

        try {
            if (!account.getLock().tryLock(BankConfig.LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new ServiceUnavailableException(
                        "Could not acquire lock on account " + account.getId() + " — system busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Lock acquisition interrupted");
        }
    }

    /**
     * Transfers money atomically between two accounts.
     *
     * <p>
     * This operation is thread-safe and idempotent. If the same idempotency key
     * is provided twice, only the first call performs the transfer; subsequent
     * calls
     * with the same key are no-ops.
     *
     * <p>
     * Both accounts are locked in consistent ID order to prevent deadlock.
     * If a lock cannot be acquired within the timeout, throws
     * ServiceUnavailableException.
     *
     * @param idempotencyKey unique key for this transfer attempt; required,
     *                       non-blank
     * @param fromId         source account ID; must exist
     * @param toId           destination account ID; must exist and differ from
     *                       fromId
     * @param amount         positive amount to transfer; must not exceed
     *                       MAX_TRANSFER_AMOUNT
     * @throws IllegalArgumentException    if any validation fails
     * @throws InsufficientFundsException  if from-account has insufficient balance
     * @throws ServiceUnavailableException if locks cannot be acquired within
     *                                     timeout
     */

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

            acquireLockOrFail(first);
            try {
                acquireLockOrFail(second);
                try {
                    // ... do the transfer
                    fromAccount.debit(amount); // may throw InsufficientFundsException
                    toAccount.credit(amount);
                    auditLog.add(new TransactionEvent(
                            newEventId(), Instant.now(), TransactionEvent.EventType.TRANSFER,
                            fromId, toId, amount,
                            TransactionEvent.EventStatus.SUCCESS, null));
                    log.info("Transfer completed: key={} from={} to={} amount={}",
                            idempotencyKey, fromId, toId, amount);

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


    /**
     * Deposits the given amount into the specified account.
     * This operation validates the amount and account existence.
     * then delegates to the Account.credit() method, which handles locking.
     * 
     * @throws IllegalArgumentException if validation fails. if the amount exceeds
     *                                  MAX_TRANSFER_AMOUNT, it is rejected to
     *                                  prevent accidental huge deposits.
     * @throws illegalArgumentException if the amount is zero or negative, as those
     *                                  are not valid deposit amounts.
     * @param accountId the ID of the account to deposit into; must exist
     * @param amount    the positive amount to deposit; must not exceed
     *                  MAX_TRANSFER_AMOUNT
     */
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

    /**
     * Withdraws the given amount from the specified account.
     * This operation validates the amount and account existence,
     * then delegates to the Account.debit() method, which handles locking and
     * insufficient funds checks.
     * 
     * @param accountId the ID of the account to withdraw from; must exist
     * @param amount    the positive amount to withdraw; must not exceed the account
     *                  balance and MAX_TRANSFER_AMOUNT
     * @throws IllegalArgumentException   if validation fails. if the amount exceeds
     *                                    MAX_TRANSFER_AMOUNT, it is rejected to
     *                                    prevent accidental huge withdrawals. if
     *                                    the amount is zero or negative, as those
     *                                    are not valid withdrawal amounts.
     * @throws InsufficientFundsException if the account balance is insufficient for
     *                                    the withdrawal
     */
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
