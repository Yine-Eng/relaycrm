package com.relaycrm.common;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.Role;

/**
 * The role rules, in one place. They apply after organization scoping:
 * by the time these are asked, the record is already known to be in the
 * caller's organization.
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    /** Reps may edit only records they own; managers and admins may edit any record. */
    public static boolean canEdit(CurrentUser user, OwnedRecord record) {
        return user.role() != Role.REP || record.getOwnerId().equals(user.id());
    }

    /** Only admins may delete. */
    public static boolean canDelete(CurrentUser user) {
        return user.role() == Role.ADMIN;
    }

    /**
     * The history of a record that still exists is visible to everyone who can see
     * the record. Once the record is deleted, only managers and admins may read it.
     */
    public static boolean canViewHistoryOfDeletedRecords(CurrentUser user) {
        return user.role() != Role.REP;
    }

    /** A rep sees only their own tasks; managers and admins see every task in the organization. */
    public static boolean canViewTasksOfOthers(CurrentUser user) {
        return user.role() != Role.REP;
    }

    /** Only managers and admins may give a record to another user. */
    public static boolean canAssignToOthers(CurrentUser user) {
        return user.role() != Role.REP;
    }
}
