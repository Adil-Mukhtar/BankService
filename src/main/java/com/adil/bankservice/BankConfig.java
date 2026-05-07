package com.adil.bankservice;

import java.math.BigDecimal;

/**
 * Centralized configuration for BankService operational parameters.
 * In a real production system, these would be loaded from a config file
 * or environment variables.
 */
public final class BankConfig {

    /** Maximum time to wait for lock acquisition before failing. */
    public static final long LOCK_TIMEOUT_SECONDS = 5;

    /** Maximum amount allowed in a single transfer. Prevents accidental huge transfers. */
    public static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("1000000");

    /** Minimum allowed amount for any operation. Prevents zero or tiny amounts. */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private BankConfig() {
        // Prevent instantiation — this is a constants class
    }
}