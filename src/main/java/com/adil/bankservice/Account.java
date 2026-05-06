package com.adil.bankservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

public class Account {

    private static final Logger log = LoggerFactory.getLogger(Account.class);

    private final long id;
    private BigDecimal balance;
    private final ReentrantLock lock;

    public Account(long id, BigDecimal initialBalance) {
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be null or negative");
        }
        this.id = id;
        this.balance = initialBalance;
        this.lock = new ReentrantLock();
    }

    public long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Debits (subtracts) the given amount from this account.
     * Acquires the account's lock for the duration of the operation.
     *
     * @throws InsufficientFundsException if balance would go negative
     */
    void debit(BigDecimal amount) {
        lock.lock();
        try {
            if (amount.compareTo(balance) > 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds: balance=" + balance + ", requested=" + amount);
            }
            balance = balance.subtract(amount);
            log.debug("Debit: accountId={} amount={} newBalance={}", id, amount, balance);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Credits (adds) the given amount to this account.
     * Acquires the account's lock for the duration of the operation.
     */
    void credit(BigDecimal amount) {
        lock.lock();
        try {
            balance = balance.add(amount);
            log.debug("Credit: accountId={} amount={} newBalance={}", id, amount, balance);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the lock for external coordination (e.g., two-account transfers).
     * Package-private — only BankService should use this.
     */
    ReentrantLock getLock() {
        return lock;
    }
}