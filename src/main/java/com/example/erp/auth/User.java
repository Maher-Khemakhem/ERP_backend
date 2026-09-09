package com.example.erp.auth;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private Set<Role> roles = new LinkedHashSet<>();

    @Version
    private long version;

    protected User() {
    }

    private User(UUID id, String username, String displayName, String passwordHash, Set<Role> roles) {
        this.id = Objects.requireNonNull(id);
        this.username = requireText(username, "username").toLowerCase();
        this.displayName = requireText(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.roles = new LinkedHashSet<>(requireRoles(roles));
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public static User create(String username, String displayName, String passwordHash, Set<Role> roles) {
        return new User(UUID.randomUUID(), username, displayName, passwordHash, roles);
    }

    public void replaceRoles(Set<Role> roles) {
        this.roles = new LinkedHashSet<>(requireRoles(roles));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static Set<Role> requireRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("At least one role is required");
        return roles;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
