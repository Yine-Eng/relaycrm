package com.relaycrm.common;

/** Common shape of create/update request bodies. There is no orgId: it always comes from the caller. */
public interface OwnedRecordRequest {

    /** Optional. Only managers and admins may assign a record to someone else. */
    Long ownerId();
}
