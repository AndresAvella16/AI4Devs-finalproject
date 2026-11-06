package com.jobpilot.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Usuario de la aplicación (tabla {@code users}). El email llega ya normalizado a minúsculas. */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    private String email;

    private String passwordHash;

    private String fullName;

    private Instant createdAt;

    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
