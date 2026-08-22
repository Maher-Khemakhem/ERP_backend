package com.example.erp.shared;


import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Quantity value object.
 *
 * A supermarket has two genuinely different kinds of quantity:
 *   - EACH: whole countable units (3 cans, 12 yogurts)
 *   - WEIGHT: continuous weighed amounts in kg (0.450 kg of tomatoes)
 *
 * These must never be silently interchangeable - selling "3" of a
 * weighed product or "0.450" of a per-unit product is a bug, not a
 * valid state. Encoding UnitType directly on the value object makes
 * that bug impossible to represent, rather than something to remember
 * to check in application code.
 */
@Embeddable
public record Quantity(BigDecimal value, UnitType unitType) {

    private static final int EACH_SCALE = 0;   // whole units only
    private static final int WEIGHT_SCALE = 3; // grams precision within kg

    public Quantity {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(unitType, "unitType must not be null");

        int scale = unitType == UnitType.EACH ? EACH_SCALE : WEIGHT_SCALE;
        value = value.setScale(scale, RoundingMode.HALF_UP);

        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + value);
        }
        if (unitType == UnitType.EACH && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("EACH quantity must be a whole number: " + value);
        }
    }

    public static Quantity each(int count) {
        return new Quantity(BigDecimal.valueOf(count), UnitType.EACH);
    }

    public static Quantity kg(double kilograms) {
        return new Quantity(BigDecimal.valueOf(kilograms), UnitType.WEIGHT);
    }

    public static Quantity zero(UnitType unitType) {
        return new Quantity(BigDecimal.ZERO, unitType);
    }

    public Quantity add(Quantity other) {
        requireSameUnitType(other);
        return new Quantity(this.value.add(other.value), unitType);
    }

    public Quantity subtract(Quantity other) {
        requireSameUnitType(other);
        return new Quantity(this.value.subtract(other.value), unitType);
    }

    public boolean isGreaterThan(Quantity other) {
        requireSameUnitType(other);
        return this.value.compareTo(other.value) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Quantity other) {
        requireSameUnitType(other);
        return this.value.compareTo(other.value) >= 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    private void requireSameUnitType(Quantity other) {
        if (this.unitType != other.unitType) {
            throw new UnitTypeMismatchException(this.unitType, other.unitType);
        }
    }

    @Override
    public String toString() {
        return unitType == UnitType.EACH
                ? value.toBigInteger() + " unit(s)"
                : value + " kg";
    }

    public enum UnitType {
        EACH, WEIGHT
    }

    public static final class UnitTypeMismatchException extends RuntimeException {
        public UnitTypeMismatchException(UnitType a, UnitType b) {
            super("Cannot operate on different unit types: " + a + " vs " + b);
        }
    }
}