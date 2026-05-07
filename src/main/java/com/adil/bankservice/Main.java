package com.adil.bankservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    public static void main(String[] args) {
        BankService bankService = new BankService();
        // In a real service, you'd start an HTTP server here
        // For now, this is just a placeholder
        Logger log = LoggerFactory.getLogger(Main.class);
        log.info("BankService ready");
    }
}