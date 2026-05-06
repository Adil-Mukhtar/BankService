package com.adil.bankservice;

import java.math.BigDecimal;
import java.time.Instant;


public record TransactionEvent(

    String eventId,           // unique ID for this event
    Instant timestamp,        // when it happened
    EventType type,           // TRANSFER / DEPOSIT / WITHDRAW
    Long fromAccountId,       // null for DEPOSIT
    Long toAccountId,         // null for WITHDRAW
    BigDecimal amount,
    EventStatus status,       // SUCCESS / FAILED
    String reason             // null for SUCCESS, message for FAILED
    
) {
    public enum EventType { TRANSFER, DEPOSIT, WITHDRAW }
    public enum EventStatus { SUCCESS, FAILED }
}
