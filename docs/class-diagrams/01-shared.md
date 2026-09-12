# Class Diagram — `shared` Module

The `shared` module contains the cross-cutting value objects and the domain-event contract.  
Every other module depends on `shared`; `shared` depends on nothing.

```mermaid
classDiagram

    %% ── Value Objects ──────────────────────────────────────────
    class Money {
        <<record>>
        +BigDecimal amount
        +Money of(double) Money$
        +Money of(BigDecimal) Money$
        +Money zero() Money$
        +add(Money) Money
        +subtract(Money) Money
        +multiply(BigDecimal) Money
        +multiply(int) Money
        +applyDiscountPercent(BigDecimal) Money
        +isNegative() boolean
        +isZero() boolean
        +isGreaterThan(Money) boolean
        +toString() String
    }
    note for Money "Always TND — 3 decimal places (millimes).\nUses BigDecimal to avoid floating-point rounding."

    class Quantity {
        <<record>>
        +BigDecimal value
        +UnitType unitType
        +each(int) Quantity$
        +kg(double) Quantity$
        +zero(UnitType) Quantity$
        +add(Quantity) Quantity
        +subtract(Quantity) Quantity
        +isGreaterThan(Quantity) boolean
        +isGreaterThanOrEqualTo(Quantity) boolean
        +isZero() boolean
        +toString() String
    }
    note for Quantity "EACH: whole units only (scale=0).\nWEIGHT: kilograms, 3 decimal places (scale=3).\nMixing unit types throws UnitTypeMismatchException."

    class UnitType {
        <<enumeration>>
        EACH
        WEIGHT
    }

    class UnitTypeMismatchException {
        <<RuntimeException>>
        +UnitTypeMismatchException(UnitType a, UnitType b)
    }

    %% ── Domain Event Contract ──────────────────────────────────
    class DomainEvent {
        <<interface>>
        +eventId() UUID
        +occurredAt() Instant
        +eventName() String
    }
    note for DomainEvent "Implemented by Java records in each module.\nSpring Modulith's outbox tracks delivery by eventId.\nConsumers can use eventId for idempotency checks."

    %% ── Relationships ──────────────────────────────────────────
    Quantity --> UnitType : uses
    Quantity --> UnitTypeMismatchException : throws
```

## Notes

| Type | Kind | Key constraint |
|---|---|---|
| `Money` | Java `record` (immutable) | Always TND, always 3 dp, never negative in financial writes |
| `Quantity` | Java `record` + `@Embeddable` | `EACH` must be a whole number; `WEIGHT` is 3 dp kilograms |
| `UnitType` | Inner `enum` of `Quantity` | Used everywhere a unit type is stored (product, lot, transaction line, order line) |
| `DomainEvent` | Interface | All cross-module events implement this; Spring Modulith persists them via the JDBC outbox |
