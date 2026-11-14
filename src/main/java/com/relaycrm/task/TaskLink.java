package com.relaycrm.task;

/** The deal or lead a task is about. */
public record TaskLink(Kind kind, Long id) {

    public enum Kind {
        OPPORTUNITY, LEAD
    }

    public static TaskLink opportunity(Long id) {
        return new TaskLink(Kind.OPPORTUNITY, id);
    }

    public static TaskLink lead(Long id) {
        return new TaskLink(Kind.LEAD, id);
    }

    public Long opportunityId() {
        return kind == Kind.OPPORTUNITY ? id : null;
    }

    public Long leadId() {
        return kind == Kind.LEAD ? id : null;
    }

    /**
     * Identifies "this rule for this record", for example
     * opportunity:107:proposal-follow-up. The database allows one OPEN task per key.
     */
    public String dedupeKey(String rule) {
        return kind.name().toLowerCase() + ":" + id + ":" + rule;
    }
}
