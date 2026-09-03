package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "journal_line", schema = "finance")
public class JournalLine {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false)
    private AccountCode accountCode;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "debit_amount", nullable = false, precision = 19, scale = 3))
    private Money debit;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "credit_amount", nullable = false, precision = 19, scale = 3))
    private Money credit;

    @Column(nullable = false)
    private String description;

    protected JournalLine() {
    }

    private JournalLine(AccountCode accountCode, Money debit, Money credit, String description) {
        this.id = UUID.randomUUID();
        this.accountCode = Objects.requireNonNull(accountCode);
        this.debit = Objects.requireNonNull(debit);
        this.credit = Objects.requireNonNull(credit);
        this.description = Objects.requireNonNull(description);
        if (debit.isNegative() || credit.isNegative() || (!debit.isGreaterThan(Money.zero()) && !credit.isGreaterThan(Money.zero()))) {
            throw new IllegalArgumentException("A journal line must contain a positive debit or credit");
        }
        if (debit.isGreaterThan(Money.zero()) && credit.isGreaterThan(Money.zero())) {
            throw new IllegalArgumentException("A journal line cannot contain both debit and credit");
        }
    }

    public static JournalLine debit(AccountCode account, Money amount, String description) {
        return new JournalLine(account, amount, Money.zero(), description);
    }

    public static JournalLine credit(AccountCode account, Money amount, String description) {
        return new JournalLine(account, Money.zero(), amount, description);
    }

    public UUID getId() { return id; }
    public AccountCode getAccountCode() { return accountCode; }
    public Money getDebit() { return debit; }
    public Money getCredit() { return credit; }
    public String getDescription() { return description; }
}
