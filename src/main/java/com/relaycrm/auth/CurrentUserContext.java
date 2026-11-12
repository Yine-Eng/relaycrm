package com.relaycrm.auth;

import java.util.Optional;

/**
 * Holds the authenticated caller for the duration of one request (one thread).
 * Set and cleared by {@link ApiKeyAuthFilter}; read by the services and by the
 * transaction manager that tells PostgreSQL which organization is active.
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<CurrentUser> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CurrentUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user on this thread"));
    }
}
