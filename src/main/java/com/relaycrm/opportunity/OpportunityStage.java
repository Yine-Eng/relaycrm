package com.relaycrm.opportunity;

import java.util.Set;

/**
 * Where a deal is in the pipeline, and where it may go next. A deal moves
 * forward one step at a time, may step back one step, and can be lost from
 * anywhere. WON and LOST are final: nothing moves out of them.
 */
public enum OpportunityStage {
    PROSPECTING, PROPOSAL, NEGOTIATION, WON, LOST;

    public Set<OpportunityStage> allowedNext() {
        return switch (this) {
            case PROSPECTING -> Set.of(PROPOSAL, LOST);
            // WON straight from PROPOSAL: the customer accepted the proposal as it was.
            case PROPOSAL -> Set.of(NEGOTIATION, PROSPECTING, WON, LOST);
            case NEGOTIATION -> Set.of(PROPOSAL, WON, LOST);
            case WON, LOST -> Set.of();
        };
    }

    public boolean isClosed() {
        return this == WON || this == LOST;
    }
}
