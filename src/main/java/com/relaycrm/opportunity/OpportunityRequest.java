package com.relaycrm.opportunity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.relaycrm.common.OwnedRecordRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** stage defaults to PROSPECTING when omitted. */
public record OpportunityRequest(
        @NotBlank String name,
        Long contactId,
        @PositiveOrZero BigDecimal amount,
        OpportunityStage stage,
        LocalDate closeDate,
        Long ownerId) implements OwnedRecordRequest {
}
