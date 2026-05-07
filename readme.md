# Bank Service

A thread-safe, production-grade Java banking service demonstrating fintech engineering patterns.

## Features

- **Thread-safe money transfers** with deadlock prevention via consistent lock ordering
- **Idempotency** — same idempotency key processed only once, network-retry safe
- **Audit trail** — every operation (success and failure) recorded as immutable events
- **Lock timeouts** — operations fail gracefully instead of blocking indefinitely
- **Structured logging** — SLF4J + Logback with severity levels
- **JUnit 5 test coverage** — 8 tests covering business rules and concurrency

## Architecture

- `Account` — domain object owning its own state and lock; exposes `debit` / `credit` operations
- `BankService` — orchestrates multi-account transactions, validates inputs, manages audit log and idempotency
- `TransactionEvent` — immutable record of every state change for compliance/audit
- `BankConfig` — centralized operational parameters

## Running

Requires Java 17 and Maven 3.x.

```bash
mvn clean compile        # build
mvn test                 # run tests
mvn exec:java            # run interactive demo
```

## Testing

```bash
mvn test
```

8 tests cover:
- Successful transfer balance updates
- Validation of amount, accounts, same-account
- Insufficient funds rejection
- Idempotency under repeated keys
- Concurrent transfer correctness (10 threads, 5000 transfers)
- Audit log accuracy

## Design Decisions

- **No Spring/Hibernate** — vanilla Java to demonstrate understanding of underlying patterns
- **BigDecimal for amounts** — never floating-point for money
- **Per-account ReentrantLock** — concurrency at the granularity of individual accounts
- **Lock ordering by ID** — prevents the classic deadlock pattern in two-account transfers
- **synchronized List for audit** — append-heavy workload, defensive copy on read
- **ConcurrentHashMap.newKeySet for idempotency keys** — atomic check-and-add

## Future Improvements

- Persistence layer (PostgreSQL via jOOQ)
- REST API (SparkJava or similar lightweight framework)
- Event sourcing (currently we only log events; we don't replay them)
- Distributed locks (Redis) for multi-instance deployments