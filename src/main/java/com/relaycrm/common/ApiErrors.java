package com.relaycrm.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errors the API returns, rendered by Spring as RFC 9457 problem+json. */
public final class ApiErrors {

    private ApiErrors() {
    }

    /** Used both for "does not exist" and "exists in another organization", so the two are indistinguishable. */
    public static ErrorResponseException notFound(String what, Long id) {
        return error(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }

    public static ErrorResponseException forbidden(String detail) {
        return error(HttpStatus.FORBIDDEN, detail);
    }

    public static ErrorResponseException badRequest(String detail) {
        return error(HttpStatus.BAD_REQUEST, detail);
    }

    private static ErrorResponseException error(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
