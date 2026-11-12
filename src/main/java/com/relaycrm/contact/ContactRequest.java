package com.relaycrm.contact;

import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email String email,
        String phone,
        String company,
        Long ownerId) implements OwnedRecordRequest {
}
