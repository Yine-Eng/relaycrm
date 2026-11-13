package com.relaycrm.opportunity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.relaycrm.common.OnUpdate;
import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** stage may be omitted on create (defaults to PROSPECTING) but is required on update. */
public record OpportunityRequest(
        @NotBlank String name,
        Long contactId,
        @PositiveOrZero BigDecimal amount,
        @NotNull(groups = OnUpdate.class) OpportunityStage stage,
        LocalDate closeDate,
        Long ownerId) implements OwnedRecordRequest {
}
