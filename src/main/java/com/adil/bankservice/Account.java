package com.adil.bankservice;

import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    
    private final long id;
    private BigDecimal balance;
    private final ReentrantLock lock;

    public Account(long id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
        this.lock = new ReentrantLock();
    }

    public long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }


}
