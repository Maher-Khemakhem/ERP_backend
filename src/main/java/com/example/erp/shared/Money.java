package com.example.erp.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money value object — always TND (Tunisian Dinar), 3 decimal places (millimes).
 *
 * Deliberately NOT a float/double - those cause silent rounding errors
 * in financial calculations. BigDecimal with a fixed scale is the only
 * safe representation for currency in this domain.
 *
 * Single-currency by design: this ERP is built for one store, pricing
 * everything in TND, so there's no currency field to get wrong and no
 * cross-currency arithmetic to guard against. If multi-currency is ever
 * needed later (e.g. reporting in EUR for a foreign supplier), that's
 * a deliberate, separate extension — not something to speculatively
 * support now.
 */
public record Money(BigDecimal amount) {

    private static final int SCALE = 3; // TND uses millimes (3 decimal places)

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }

    public Money multiply(int factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    /** Applies a percentage discount, e.g. applyDiscountPercent(10) = 10% off. */
    public Money applyDiscountPercent(BigDecimal percent) {
        BigDecimal remainingFraction = BigDecimal.ONE.subtract(
                percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return multiply(remainingFraction);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    @Override
    public String toString() {
        return amount + " TND";
    }
}
