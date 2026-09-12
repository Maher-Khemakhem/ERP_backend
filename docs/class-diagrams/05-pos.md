# Class Diagram — `pos` Module

The `pos` module owns tills, cashier sessions, sale transactions, and the phone-scanner QR pairing.  
`PosTransaction` is the central aggregate: it owns `TransactionLine` items and enforces the sale state machine.

```mermaid
classDiagram

    %% ── Till ───────────────────────────────────────────────────
    class Till {
        <<Entity — table: pos.till>>
        -UUID id PK
        -String tillNumber UK
        -TillStatus status
        -long version
        +create(tillNumber) Till$
        +markOpen() void
        +markClosed() void
    }

    class TillStatus {
        <<enumeration>>
        OPEN
        CLOSED
    }

    %% ── Cash Session ───────────────────────────────────────────
    class CashSession {
        <<Entity — table: pos.cash_session>>
        -UUID id PK
        -UUID tillId
        -String cashierName
        -Money openingFloat
        -Money expectedCash
        -Money countedCash
        -SessionStatus status
        -Instant openedAt
        -Instant closedAt
        -long version
        +open(tillId, cashierName, openingFloat) CashSession$
        +recordCashPayment(amount) void
        +close(actualCountedCash) void
    }
    note for CashSession "openingFloat cannot be negative.\nrecordCashPayment accumulates expectedCash for each cash sale.\nclose() seals the session and records countedCash for variance reporting."

    class SessionStatus {
        <<enumeration>>
        OPEN
        CLOSED
    }

    %% ── Transaction ────────────────────────────────────────────
    class PosTransaction {
        <<Entity — table: pos.pos_transaction — AGGREGATE ROOT>>
        -UUID id PK
        -UUID cashSessionId
        -List~TransactionLine~ lines
        -TransactionStatus status
        -PaymentMethod paymentMethod
        -Instant startedAt
        -Instant completedAt
        -long version
        +start(cashSessionId) PosTransaction$
        +addLine(productId, quantity, calculatedPrice) UUID
        +addLine(productId, quantity, calculatedPrice, vatAmount) UUID
        +removeLine(lineId) void
        +complete(paymentMethod) void
        +voidTransaction(reason) void
        +getNetTotal() Money
        +getVatTotal() Money
        +getTotal() Money
        +getVatRate() BigDecimal
    }
    note for PosTransaction "Prices are VAT-exclusive.\nVAT is calculated as 19% of the net subtotal (hard-coded for now).\ncomlete() requires at least one line and rejects empty tickets."

    class TransactionLine {
        <<Entity — table: pos.transaction_line — owned by PosTransaction>>
        -UUID id PK
        -UUID productId
        -Quantity quantity
        -Money lineTotal
        -Money vatAmount
        +of(productId, quantity, calculatedPrice) TransactionLine$
        +of(productId, quantity, calculatedPrice, vatAmount) TransactionLine$
        +getNetTotal() Money
    }

    class TransactionStatus {
        <<enumeration>>
        IN_PROGRESS
        COMPLETED
        VOIDED
    }

    class PaymentMethod {
        <<enumeration>>
        CASH
        CARD
    }

    %% ── Domain Events ──────────────────────────────────────────
    class TransactionCompleted {
        <<record — DomainEvent>>
        +UUID eventId
        +Instant occurredAt
        +UUID transactionId
        +UUID cashSessionId
        +Money netTotal
        +Money vatTotal
        +Money total
        +PaymentMethod paymentMethod
        +List~LineItem~ lines
    }
    note for TransactionCompleted "Consumed by Finance to post sales revenue, VAT, COGS, and payment journal entries."

    class CashSessionClosed {
        <<record — DomainEvent>>
        +UUID eventId
        +Instant occurredAt
        +UUID sessionId
        +UUID tillId
        +Money openingFloat
        +Money expectedCash
        +Money countedCash
    }
    note for CashSessionClosed "Consumed by Finance to post cash deposit and over/short variance."

    %% ── Scanner Pairing ────────────────────────────────────────
    class ScannerSessionService {
        <<Service — in-memory only>>
        -Map scannerSessions
        +createPairing(transactionId) ScannerSession
        +validateAndGetTransaction(sessionId, token) UUID
        +closePairing(sessionId, token) void
        +cleanupExpired() void
    }
    note for ScannerSessionService "Pairings are stored in a ConcurrentHashMap — NOT persisted.\nAll pairings are lost on backend restart.\nEach pairing expires after 30 minutes.\nThe scanner token is the authentication mechanism for public scanner endpoints."

    %% ── Relationships ──────────────────────────────────────────
    Till --> TillStatus : status
    CashSession --> SessionStatus : status
    CashSession --> Till : tillId (logical FK)
    PosTransaction --> TransactionStatus : status
    PosTransaction --> PaymentMethod : paymentMethod
    PosTransaction "1" *-- "many" TransactionLine : lines (OneToMany, cascade ALL)
    PosTransaction --> CashSession : cashSessionId (logical FK)
    PosTransaction ..> TransactionCompleted : publishes on complete()
    CashSession ..> CashSessionClosed : publishes on close()
    ScannerSessionService --> PosTransaction : validates against IN_PROGRESS transaction
```

## State machines

### Till status
```
CLOSED ──markOpen()──▶ OPEN
OPEN ──markClosed()──▶ CLOSED
```

### CashSession status
```
(created) ──open()──▶ OPEN ──close()──▶ CLOSED
```

### PosTransaction status
```
(created) ──start()──▶ IN_PROGRESS ──complete()──▶ COMPLETED
                             │
                             └──voidTransaction()──▶ VOIDED
```

## Key sale flow

1. `PosService.startTransaction(cashSessionId)` — creates an `IN_PROGRESS` transaction.
2. `addLine(productId, quantity)` — calls `PricingEngine.calculatePrice()`, then adds a `TransactionLine`.
3. `complete(paymentMethod, amountReceived)`:
   - Validates payment (cash ≥ total; card = total exactly).
   - Calls `InventoryService.sell()` for every line (FEFO deduction).
   - Calls `CashSession.recordCashPayment()` for CASH payments.
   - Transitions to `COMPLETED` and publishes `TransactionCompleted`.
4. Finance consumes `TransactionCompleted` and posts the balanced journal entry.

> **Stock and transaction completion are in the same Spring transaction** — insufficient stock rolls back the entire sale.
