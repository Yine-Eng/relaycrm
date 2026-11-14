package com.relaycrm.task;

import java.time.Instant;

import com.relaycrm.common.OnUpdate;
import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * What a client may set on a task. status may be omitted on create (defaults
 * to OPEN) but is required on update. source and dedupeKey are not here: they
 * are decided by the server.
 */
public record TaskRequest(
        @NotBlank String title,
        @NotNull Instant dueAt,
        @NotNull(groups = OnUpdate.class) TaskStatus status,
        Long opportunityId,
        Long leadId,
        Long ownerId) implements OwnedRecordRequest {

    /** A task's current values in request form (for PATCH and for the audit trail). */
    public static TaskRequest of(Task task) {
        return new TaskRequest(task.getTitle(), task.getDueAt(), task.getStatus(),
                task.getOpportunityId(), task.getLeadId(), task.getOwnerId());
    }
}
