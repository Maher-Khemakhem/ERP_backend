package com.example.erp.pos;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Till — une caisse physique (poste d'encaissement).
 *
 * Entité simple : représente le poste matériel lui-même. La logique
 * de session de caisse (ouverture, clôture, comptage) vit dans
 * CashSession, pas ici — Till reste une référence stable au poste.
 */
@Entity
@Table(name = "till", schema = "pos")
public class Till {

    @Id
    private UUID id;

    @Column(name = "till_number", nullable = false, unique = true)
    private String tillNumber; // ex. "Caisse 1", "Caisse 2"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TillStatus status;

    protected Till() {
        // requis par JPA/Hibernate
    }

    private Till(UUID id, String tillNumber, TillStatus status) {
        this.id = Objects.requireNonNull(id);
        this.tillNumber = requireNonBlank(tillNumber, "tillNumber");
        this.status = Objects.requireNonNull(status);
    }

    public static Till create(String tillNumber) {
        return new Till(UUID.randomUUID(), tillNumber, TillStatus.CLOSED);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value;
    }

    public void markOpen() {
        if (status == TillStatus.OPEN) {
            throw new IllegalStateException("La caisse " + tillNumber + " est déjà ouverte");
        }
        status = TillStatus.OPEN;
    }

    public void markClosed() {
        if (status == TillStatus.CLOSED) {
            throw new IllegalStateException("La caisse " + tillNumber + " est déjà fermée");
        }
        status = TillStatus.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public String getTillNumber() {
        return tillNumber;
    }

    public TillStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Till other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum TillStatus {
        OPEN, CLOSED
    }
}