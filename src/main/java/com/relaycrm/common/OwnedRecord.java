package com.relaycrm.common;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

/**
 * Base for every CRM record: it belongs to one organization and is owned by
 * one user of that organization. org_id is written once and never updated.
 * Only {@link OwnedRecordService} (same package) can set the organization. The
 * owner is set by that service too; the setter is protected so that a record
 * type can offer a narrow, named way to change it (see Task.moveTo).
 *
 * version is managed by JPA for optimistic locking: it starts at 0, goes up by
 * one on every update that changes something, and an update based on an older
 * version is refused. Clients see it in the JSON body and as the ETag header.
 */
@MappedSuperclass
public abstract class OwnedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    protected void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
