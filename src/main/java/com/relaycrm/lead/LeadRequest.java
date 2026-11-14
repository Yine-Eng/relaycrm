package com.relaycrm.lead;

import com.relaycrm.common.OnUpdate;
import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** status may be omitted on create (defaults to NEW) but is required on update. */
public record LeadRequest(
        @NotBlank String name,
        String company,
        @Email String email,
        String source,
        @NotNull(groups = OnUpdate.class) LeadStatus status,
        Long ownerId) implements OwnedRecordRequest {
}
