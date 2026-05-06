package com.adil.bankservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    final private ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();

    // helper methods
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

    // main transfer method with proper locking to ensure thread safety

    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        log.debug("Transfer initiated: from={} to={} amount={}", fromId, toId, amount);

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
                    log.info("Transfer completed: from={} to={} amount={}", fromId, toId, amount);
                } finally {
                    second.getLock().unlock();
                }
            } finally {
                first.getLock().unlock();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Transfer rejected: from={} to={} amount={} reason={}",
                    fromId, toId, amount, e.getMessage());
            throw e;
        } catch (InsufficientFundsException e) {
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
            log.info("Deposit completed: accountId={} amount={}", accountId, amount);
        } catch (IllegalArgumentException e) {
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
            log.info("Withdraw completed: accountId={} amount={}", accountId, amount);

        } catch (IllegalArgumentException e) {
            log.warn("Withdraw rejected: accountId={} amount={} reason={}",
                    accountId, amount, e.getMessage());
            throw e;
        } catch (InsufficientFundsException e) {
            log.warn("Withdraw rejected (insufficient funds): accountId={} amount={}", accountId, amount);
            throw e;
        }

    }

}
