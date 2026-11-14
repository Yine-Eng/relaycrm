package com.relaycrm.audit;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRawValue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One saved change to a contact, lead or opportunity: who did it, when, and
 * what changed. Events are only ever inserted; there are no setters, every
 * column is non-updatable, and the database refuses UPDATE and DELETE.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    /** JSON text, stored as jsonb: {"field": {"from": old, "to": new}}. */
    @JsonRawValue
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String changes;

    @Column(name = "version_after", nullable = false, updatable = false)
    private long versionAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    AuditEvent(Long orgId, Long actorUserId, String entityType, Long entityId, AuditAction action,
               String changes, long versionAfter) {
        this.orgId = orgId;
        this.actorUserId = actorUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.changes = changes;
        this.versionAfter = versionAfter;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getChanges() {
        return changes;
    }

    public long getVersionAfter() {
        return versionAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
