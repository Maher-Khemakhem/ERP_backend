package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CashSession — aggregate root représentant une session de caisse :
 * de l'ouverture (fond de caisse initial) à la clôture (comptage réel,
 * calcul de l'écart).
 *
 * Toutes les transactions POS d'une caisse pendant une journée
 * s'accumulent dans la session active. C'est cette classe qui porte
 * la logique de réconciliation — pas Till, qui reste une simple
 * référence au poste physique.
 */
@Entity
@Table(name = "cash_session", schema = "pos")
public class CashSession {

    @Id
    private UUID id;

    @Column(name = "till_id", nullable = false)
    private UUID tillId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "opening_float"))
    private Money openingFloat;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "expected_cash"))
    private Money expectedCash;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "counted_cash"))
    private Money countedCash; // null tant que la session n'est pas clôturée

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected CashSession() {
        // requis par JPA/Hibernate
    }

    private CashSession(UUID id, UUID tillId, Money openingFloat) {
        this.id = Objects.requireNonNull(id);
        this.tillId = Objects.requireNonNull(tillId);
        this.openingFloat = Objects.requireNonNull(openingFloat);
        this.expectedCash = openingFloat;
        this.status = SessionStatus.OPEN;
        this.openedAt = Instant.now();
    }

    public static CashSession open(UUID tillId, Money openingFloat) {
        if (openingFloat.isNegative()) {
            throw new IllegalArgumentException("Le fond de caisse ne peut pas être négatif");
        }
        CashSession session = new CashSession(UUID.randomUUID(), tillId, openingFloat);
        session.domainEvents.add(CashSessionOpened.of(session.id, tillId, openingFloat));
        return session;
    }

    /**
     * Enregistre l'encaissement en espèces d'une transaction complétée
     * (appelé par PosTransaction.complete() via le service applicatif,
     * uniquement si le mode de paiement est espèces).
     */
    public void recordCashPayment(Money amountPaidInCash) {
        requireOpen();
        expectedCash = expectedCash.add(amountPaidInCash);
    }

    /**
     * Clôture la session : enregistre le comptage réel des espèces en
     * caisse et calcule l'écart (positif = surplus, négatif = manquant).
     */
    public void close(Money actualCountedCash) {
        requireOpen();
        this.countedCash = actualCountedCash;
        this.status = SessionStatus.CLOSED;
        this.closedAt = Instant.now();

        Money variance = actualCountedCash.subtract(expectedCash);
        domainEvents.add(CashSessionClosed.of(id, tillId, expectedCash, actualCountedCash, variance));

        if (!variance.equals(Money.zero())) {
            domainEvents.add(TillOverShortDetected.of(id, tillId, variance));
        }
    }

    private void requireOpen() {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("La session de caisse " + id + " n'est pas ouverte");
        }
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTillId() {
        return tillId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Money getExpectedCash() {
        return expectedCash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CashSession other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum SessionStatus {
        OPEN, CLOSED
    }
}