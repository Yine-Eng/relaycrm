package com.relaycrm.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Responses that carry a single record also carry its version as an ETag
 * header, for example ETag: "3". A client sends that value back in If-Match
 * when it updates the record.
 */
public final class RecordResponse {

    private RecordResponse() {
    }

    public static <E extends OwnedRecord> ResponseEntity<E> ok(E record) {
        return ResponseEntity.ok().eTag(etag(record)).body(record);
    }

    public static <E extends OwnedRecord> ResponseEntity<E> created(E record) {
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(record)).body(record);
    }

    private static String etag(OwnedRecord record) {
        return "\"" + record.getVersion() + "\"";
    }
}
