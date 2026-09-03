package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "journal_entry", schema = "finance")
public class JournalEntry {
    @Id
    private UUID id;

    @Column(name = "event_key", nullable = false, unique = true)
    private String eventKey;

    @Column(nullable = false)
    private String description;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    private JournalEntry(String eventKey, String description, UUID referenceId, List<JournalLine> lines) {
        this.id = UUID.randomUUID();
        this.eventKey = requireText(eventKey, "eventKey");
        this.description = requireText(description, "description");
        this.referenceId = referenceId;
        this.postedAt = Instant.now();
        this.lines.addAll(lines);
        Money debits = lines.stream().map(JournalLine::getDebit).reduce(Money.zero(), Money::add);
        Money credits = lines.stream().map(JournalLine::getCredit).reduce(Money.zero(), Money::add);
        if (!debits.amount().equals(credits.amount())) {
            throw new IllegalArgumentException("Journal entry is not balanced: debit " + debits + ", credit " + credits);
        }
        if (lines.isEmpty() || !debits.isGreaterThan(Money.zero())) {
            throw new IllegalArgumentException("Journal entry must contain a positive amount");
        }
    }

    public static JournalEntry post(String eventKey, String description, UUID referenceId, List<JournalLine> lines) {
        return new JournalEntry(eventKey, description, referenceId, lines);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public UUID getId() { return id; }
    public String getEventKey() { return eventKey; }
    public String getDescription() { return description; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getPostedAt() { return postedAt; }
    public List<JournalLine> getLines() { return List.copyOf(lines); }
}
