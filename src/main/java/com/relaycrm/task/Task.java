package com.relaycrm.task;

import java.time.Instant;

import com.relaycrm.common.OwnedRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Something to do by a certain time, optionally about one deal or one lead.
 * A task is an OwnedRecord like contacts, leads and opportunities, so it has
 * the same organization isolation, owner, version (If-Match) and audit trail.
 */
@Entity
@Table(name = "tasks")
public class Task extends OwnedRecord {

    @Column(nullable = false)
    private String title;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /** Set once, when the task is created. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TaskSource source = TaskSource.MANUAL;

    @Column(name = "opportunity_id")
    private Long opportunityId;

    @Column(name = "lead_id")
    private Long leadId;

    /** Automatic tasks only; see TaskLink.dedupeKey. Set once, by the insert in FollowUpTasks. */
    @Column(name = "dedupe_key", updatable = false)
    private String dedupeKey;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskSource getSource() {
        return source;
    }

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    /** Used only when the deal or lead this automatic task belongs to changes owner. */
    void moveTo(Long newOwnerId) {
        setOwnerId(newOwnerId);
    }
}
