package BankService;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

public class BankService {
    
    final private ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();

    //helper methods
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


    private void insufficientFunds(BigDecimal amount, BigDecimal balance) {
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
    }


    public void createAccount(Long id, BigDecimal initialBalance){

        accounts.put(id, new Account(id, initialBalance));
    }

    public BigDecimal getTotalBalance() {
        return accounts.values().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    // main transfer method with proper locking to ensure thread safety

    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        validateAmount(amount);

        validateAccountIds(fromId, toId);

        requireAccount(toId);
        requireAccount(fromId);

        Account fromAccount = accounts.get(fromId);
        Account toAccount = accounts.get(toId);


        Account first = fromAccount.getId() < toAccount.getId() ? fromAccount : toAccount;
        Account second = first == fromAccount ? toAccount : fromAccount;
        

        //now here happens the real transfer logic, and thread safety is guaranteed by
        // locking the accounts in a consistent order (by account id) to prevent deadlocks


        first.getLock().lock();
        try {
            second.getLock().lock();
            try {
                insufficientFunds(amount, fromAccount.getBalance());
                fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
                toAccount.setBalance(toAccount.getBalance().add(amount));
            } finally {
                second.getLock().unlock();
            }
        } finally {
            first.getLock().unlock();
        }

    }

    //lets also implement a way to not handle deadlock and raceconditions
    // so to cause race conditions and deadlocks, we will not lock the accounts in a consistent order
    // and we will not use locks at all, we actually want to cause bad results just to test
    public void transferWithoutLocks(Long fromId, Long toId, BigDecimal amount) {

        validateAmount(amount);

        validateAccountIds(fromId, toId);

        requireAccount(toId);
        requireAccount(fromId);
        
        Account fromAccount = accounts.get(fromId);
        Account toAccount = accounts.get(toId);

        // Check if the from account has sufficient funds
        insufficientFunds(amount, fromAccount.getBalance());


        // Perform the transfer
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
    }


    public void deposit(Long accountId, BigDecimal amount) {

            validateAmount(amount);
            requireAccount(accountId);

            Account account = accounts.get(accountId);
            account.getLock().lock();
            try {
                account.setBalance(account.getBalance().add(amount));
            } finally {
                account.getLock().unlock();
            }
    }

    public void withdraw(Long accountId, BigDecimal amount) {

        validateAmount(amount);
        requireAccount(accountId);

        Account account = accounts.get(accountId);
        account.getLock().lock();
        try {
            // Check if the from account has sufficient funds
            insufficientFunds(amount, accounts.get(accountId).getBalance());
            account.setBalance(account.getBalance().subtract(amount));
        } finally {
            account.getLock().unlock();
        }
    }

}
