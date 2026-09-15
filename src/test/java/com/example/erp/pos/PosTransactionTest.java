package com.example.erp.pos;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosTransactionTest {

    @Test
    void totalsCalculation_withoutLines_returnsZero() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());

        assertThat(transaction.getTotal()).isEqualTo(Money.zero());
        assertThat(transaction.getNetTotal()).isEqualTo(Money.zero());
        assertThat(transaction.getVatTotal()).isEqualTo(Money.zero());
    }

    @Test
    void totalsCalculation_withLines_calculatesCorrectlyWithoutRecursion() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());

        // line 1: gross 10.000, VAT 1.900 -> net 8.100
        transaction.addLine(UUID.randomUUID(), Quantity.each(1), Money.of(10.0), Money.of(1.9));
        // line 2: gross 5.000, VAT 0.500 -> net 4.500
        transaction.addLine(UUID.randomUUID(), Quantity.kg(2.5), Money.of(5.0), Money.of(0.5));

        assertThat(transaction.getTotal()).isEqualTo(Money.of(15.0));
        assertThat(transaction.getVatTotal()).isEqualTo(Money.of(2.4));
        assertThat(transaction.getNetTotal()).isEqualTo(Money.of(12.6));
    }

    @Test
    void removeLine_updatesTotals() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());
        UUID line1 = transaction.addLine(UUID.randomUUID(), Quantity.each(1), Money.of(10.0), Money.of(1.9));
        UUID line2 = transaction.addLine(UUID.randomUUID(), Quantity.each(1), Money.of(5.0), Money.of(0.5));

        transaction.removeLine(line1);

        assertThat(transaction.getLines()).hasSize(1);
        assertThat(transaction.getTotal()).isEqualTo(Money.of(5.0));
        assertThat(transaction.getVatTotal()).isEqualTo(Money.of(0.5));
        assertThat(transaction.getNetTotal()).isEqualTo(Money.of(4.5));
    }

    @Test
    void complete_emptyTicket_throwsException() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());

        assertThatThrownBy(() -> transaction.complete(PosTransaction.PaymentMethod.CASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete an empty ticket");
    }
}
