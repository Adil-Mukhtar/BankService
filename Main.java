package BankService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) {

        // Create 5 accounts: A=1000, B=1000, C=1000, D=1000, E=1000 (total = 5000)
        BankService bankService = new BankService();
        bankService.createAccount(1L, new BigDecimal("1000.00"));
        bankService.createAccount(2L, new BigDecimal("1000.00"));
        bankService.createAccount(3L, new BigDecimal("1000.00"));
        bankService.createAccount(4L, new BigDecimal("1000.00"));
        bankService.createAccount(5L, new BigDecimal("1000.00"));

        int threadCount = 10;

        
        //press 1 to run right way, press 2 to run wrong way (without locks)
        // Print success/failure counts at the end of every stress test
        System.out.println("Press 1 to run the test with locks\nPress 2 to run the test without locks\nPress 3 to transfer to non-existing accounts");
        System.out.println("Press 4 to transfer negative amount");
        System.out.println("Press 5 to transfer to the same account");
        System.out.println("Press 6 to transfer more than the balance");
        System.out.println("Press 7 to deposit money");
        System.out.println("Press 8 to withdraw money");
        System.out.println("Press 9 to run the stress test with random deposits and withdrawals");
        System.out.println("Press any other key to exit");
        System.out.print("Enter your choice: ");

        int choice = new java.util.Scanner(System.in).nextInt();

        if(choice == 1) {
            System.out.println("Running test with locks...");

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            AtomicInteger success = new AtomicInteger();
            AtomicInteger failure = new AtomicInteger();

            // Spawn 10 threads, each does 1000 random transfers between random accounts (random amounts 1-50)
            //  After all threads finish, sum all balances. The total MUST still equal 5000.
            for(int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for(int j = 0; j < 1000; j++) {
                            long fromId = (long)(ThreadLocalRandom.current().nextInt(1, 6));
                            long toId = (long)(ThreadLocalRandom.current().nextInt(1, 6));
                            BigDecimal amount = new BigDecimal((int)(ThreadLocalRandom.current().nextInt(1, 51)));
                            try {
                                bankService.transfer(fromId, toId, amount);
                                success.incrementAndGet();
                            } catch (IllegalArgumentException | InsufficientFundsException e) {
                                failure.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }


                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();}

            // Sum all balances
            BigDecimal totalBalance = bankService.getTotalBalance();
            System.out.println("Total balance across all accounts: " + totalBalance);
            executor.shutdown();

            if(totalBalance.compareTo(new BigDecimal("5000.00")) == 0) {
                System.out.println("Test passed: Total balance is consistent (with locks).");
            } else {
                System.out.println("Test failed: Total balance is inconsistent (with locks)!");
            }

            //stress results
            System.out.println("Successful transfers: " + success.get());
            System.out.println("Failed transfers: " + failure.get());
            System.out.println("Total transfers: " + (success.get() + failure.get()));


            


        }

        else if(choice == 2) {
            System.out.println("Running test without locks...");

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            //ANOTHER TEST: NO lets use the transferWithoutLocks methods to check against the right standards vs the wrong standards, we should see that the total balance is not consistent and less
            
            // Spawn 10 threads, each does 1000 random transfers between random accounts (random amounts 1-50)
            //  After all threads finish, sum all balances. The total MUST still equal 5000, but we expect it to be less due to race conditions and deadlocks

            AtomicInteger success = new AtomicInteger();
            AtomicInteger failure = new AtomicInteger();

            for(int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for(int j = 0; j < 1000; j++) {
                            long fromId = (long)(ThreadLocalRandom.current().nextInt(1, 6));
                            long toId = (long)(ThreadLocalRandom.current().nextInt(1, 6));
                            BigDecimal amount = new BigDecimal((int)(ThreadLocalRandom.current().nextInt(1, 51)));
                            try {
                                bankService.transferWithoutLocks(fromId, toId, amount);
                                success.incrementAndGet();
                            } catch (IllegalArgumentException | InsufficientFundsException e) {
                                    failure.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Sum all balances
            BigDecimal totalBalance = bankService.getTotalBalance();
            System.out.println("Total balance across all accounts: " + totalBalance);
            executor.shutdown();

            if(totalBalance.compareTo(new BigDecimal("5000.00")) == 0) {
                System.out.println("Test passed: Total balance is consistent (without locks).");
            } else {
                System.out.println("Test failed: Total balance is inconsistent (without locks)!");
            }

            //stress results
            System.out.println("Successful transfers: " + success.get());
            System.out.println("Failed transfers: " + failure.get());
            System.out.println("Total transfers: " + (success.get() + failure.get()));


        }

        else if (choice == 3) {
            //Press 3 to transfer to non-existing accounts
            try {
                bankService.transfer(1L, 999L, new BigDecimal("100.00"));
            } catch (IllegalArgumentException e) {
                System.out.println("Caught expected exception for non-existing accounts: " + e.getMessage());
            }
            
        }

        else if (choice == 4) {
            //Press 4 to transfer negative amount
            try {
                bankService.transfer(1L, 2L, new BigDecimal("-100.00"));
            } catch (IllegalArgumentException e) {
                System.out.println("Caught expected exception for negative amount: " + e.getMessage());
            }   
        }

        else if (choice == 5) {
            //Press 5 to transfer to the same account
            try {
                bankService.transfer(1L, 1L, new BigDecimal("100.00"));
            } catch (IllegalArgumentException e) {
                System.out.println("Caught expected exception for transferring to the same account: " + e.getMessage());
            }
        }

        else if (choice == 6) {
            //Press 6 to transfer more than the balance
            try {
                bankService.transfer(1L, 2L, new BigDecimal("2000.00"));
            } catch (InsufficientFundsException e) {
                System.out.println("Caught expected exception for insufficient funds: " + e.getMessage());
            }
        }

        else if (choice == 7) {
            //Press 7 to deposit money
            //also test accoutn existence and amount validity
            System.out.println("Testing deposit...");
            System.out.println("Press 1 to deposit negative amount");
            System.out.println("Press 2 to deposit to non-existing account");
            System.out.println("Press 3 to deposit valid amount to existing account");
            System.out.print("Enter your choice: ");

            int option = new java.util.Scanner(System.in).nextInt();
            if(option == 1) {
                try {
                    bankService.deposit(1L, new BigDecimal("-100.00"));
                } catch (IllegalArgumentException e) {
                    System.out.println("Caught expected exception for negative deposit: " + e.getMessage());
                }
            } else if(option == 2) {
                try {
                    bankService.deposit(999L, new BigDecimal("100.00"));
                } catch (IllegalArgumentException e) {
                    System.out.println("Caught expected exception for non-existing account deposit: " + e.getMessage());
                }
            } else if(option == 3) {
            try {
                bankService.deposit(1L, new BigDecimal("500.00"));
                System.out.println("Deposit successful. New balance: " + bankService.getTotalBalance());
            } catch (IllegalArgumentException e) {
                System.out.println("Caught expected exception for deposit: " + e.getMessage());
            }
        }
    }


        else if (choice == 8) {
            //Press 8 to withdraw money
            //also test accoutn existence and amount validity
            System.out.println("Testing deposit...");
            System.out.println("Press 1 to deposit negative amount");
            System.out.println("Press 2 to deposit to non-existing account");
            System.out.println("Press 3 to deposit valid amount to existing account");
            System.out.print("Enter your choice: ");

            int option2 = new java.util.Scanner(System.in).nextInt();
            if(option2 == 1) {
                try {
                    bankService.withdraw(1L, new BigDecimal("-100.00"));
                } catch (IllegalArgumentException e) {
                    System.out.println("Caught expected exception for negative withdrawal: " + e.getMessage());
                }
            } else if(option2 == 2) {
                try {
                    bankService.withdraw(999L, new BigDecimal("100.00"));
                } catch (IllegalArgumentException e) {
                    System.out.println("Caught expected exception for non-existing account withdrawal: " + e.getMessage());
                }
            } else if(option2 == 3) {
            try {
                bankService.withdraw(1L, new BigDecimal("200.00"));
                System.out.println("Withdrawal successful. New balance: " + bankService.getTotalBalance());
            } catch (IllegalArgumentException | InsufficientFundsException e) {
                System.out.println("Caught expected exception for withdrawal: " + e.getMessage());
            }
        }
        
    }

    // add a stress test in Main: 10 threads each do 1000 random
    // deposits or withdrawals (random amounts 1-10) on random accounts. Track success and failure counts.
    else if(choice == 9) {
        System.out.println("Running stress test with random deposits and withdrawals...");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for(int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for(int j = 0; j < 1000; j++) {
                        long accountId = (long)(ThreadLocalRandom.current().nextInt(1, 6));
                        BigDecimal amount = new BigDecimal((int)(ThreadLocalRandom.current().nextInt(1, 11)));
                        boolean isDeposit = ThreadLocalRandom.current().nextBoolean();
                        try {
                            if(isDeposit) {
                                bankService.deposit(accountId, amount);
                            } else {
                                bankService.withdraw(accountId, amount);
                            }
                            success.incrementAndGet();
                        } catch (IllegalArgumentException | InsufficientFundsException e) {
                            failure.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        BigDecimal totalBalance = bankService.getTotalBalance();
        System.out.println("Total balance across all accounts: " + totalBalance);
        System.out.println("Successful operations: " + success.get());
        System.out.println("Failed operations: " + failure.get());
        executor.shutdown();
    }

    else {
            System.out.println("Exiting...");
    }
    
}

}
