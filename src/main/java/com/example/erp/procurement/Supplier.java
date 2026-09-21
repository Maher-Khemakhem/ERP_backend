package com.example.erp.procurement;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Supplier — un fournisseur auprès duquel on passe des commandes.
 *
 * Entité de référence simple, sans logique métier complexe pour
 * l'instant — sert principalement à identifier qui livre quoi, et à
 * qui adresser une commande.
 */
@Entity
@Table(name = "supplier", schema = "procurement")
public class Supplier {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "address", length = 500)
    private String address;

    protected Supplier() {
        // requis par JPA/Hibernate
    }

    private Supplier(UUID id, String name, String contactPhone, String contactEmail, String address) {
        this.id = Objects.requireNonNull(id);
        this.name = requireNonBlank(name, "name");
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.address = address;
    }

    public static Supplier create(String name, String contactPhone, String contactEmail) {
        return create(name, contactPhone, contactEmail, null);
    }

    public static Supplier create(String name, String contactPhone, String contactEmail, String address) {
        return new Supplier(UUID.randomUUID(), name, contactPhone, contactEmail, address);
    }

    /** Updates mutable fields in place, preserving the identity (id). */
    public void update(String name, String contactPhone, String contactEmail, String address) {
        this.name = requireNonBlank(name, "name");
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.address = address;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supplier other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}