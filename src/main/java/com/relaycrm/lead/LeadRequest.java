package com.relaycrm.lead;

import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** status defaults to NEW when omitted. */
public record LeadRequest(
        @NotBlank String name,
        String company,
        @Email String email,
        String source,
        LeadStatus status,
        Long ownerId) implements OwnedRecordRequest {
}
