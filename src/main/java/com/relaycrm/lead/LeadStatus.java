package com.relaycrm.lead;

import java.util.Set;

/**
 * How far a lead has come, and where it may go next. No status is final: a
 * disqualified lead can be picked up again, and a lead qualified by mistake
 * can be put back. Nothing returns to NEW.
 */
public enum LeadStatus {
    NEW, CONTACTED, QUALIFIED, DISQUALIFIED;

    public Set<LeadStatus> allowedNext() {
        return switch (this) {
            case NEW -> Set.of(CONTACTED, QUALIFIED, DISQUALIFIED);
            case CONTACTED -> Set.of(QUALIFIED, DISQUALIFIED);
            case QUALIFIED -> Set.of(CONTACTED, DISQUALIFIED);
            case DISQUALIFIED -> Set.of(CONTACTED);
        };
    }
}
