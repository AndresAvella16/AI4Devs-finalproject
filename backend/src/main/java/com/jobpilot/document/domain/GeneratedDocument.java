package com.jobpilot.document.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Versión de una carta o de un CV adaptado (tabla {@code generated_documents}). Cada edición o regeneración crea
 * una versión nueva. {@code version} es la versión del documento, no un bloqueo optimista.
 */
@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {

    @Id
    private UUID id;

    private UUID offerId;

    @Enumerated(EnumType.STRING)
    private DocumentType type;

    private String contentMarkdown;

    private short version;

    private boolean editedByUser;

    private Instant createdAt;

    protected GeneratedDocument() {
    }

    public GeneratedDocument(UUID offerId, DocumentType type, String contentMarkdown, short version,
            boolean editedByUser, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.offerId = offerId;
        this.type = type;
        this.contentMarkdown = contentMarkdown;
        this.version = version;
        this.editedByUser = editedByUser;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public short getVersion() {
        return version;
    }

    public boolean isEditedByUser() {
        return editedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
