package com.relaycrm.auth;

/** The authenticated caller: who they are, which organization they belong to, and their role. */
public record CurrentUser(Long id, Long orgId, Role role) {
}
