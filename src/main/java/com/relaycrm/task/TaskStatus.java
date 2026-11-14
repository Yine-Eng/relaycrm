package com.relaycrm.task;

import java.util.Set;

/** An OPEN task is either completed or cancelled; both are final. */
public enum TaskStatus {
    OPEN, DONE, CANCELLED;

    public Set<TaskStatus> allowedNext() {
        return this == OPEN ? Set.of(DONE, CANCELLED) : Set.of();
    }
}
