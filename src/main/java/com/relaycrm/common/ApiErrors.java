package com.relaycrm.common;

import java.util.Map;

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

    /** 400 that names each rejected field: {"errors": {"lastName": "must not be blank"}}. */
    public static ErrorResponseException invalidFields(Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid fields: " + String.join(", ", errors.keySet()));
        problem.setProperty("errors", errors);
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }

    private static ErrorResponseException error(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
