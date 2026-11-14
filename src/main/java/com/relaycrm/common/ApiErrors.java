package com.relaycrm.common;

import java.util.Collection;
import java.util.List;
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

    /** 409: the request is valid, but not for the record as it is now. */
    public static ErrorResponseException conflict(String detail) {
        return error(HttpStatus.CONFLICT, detail);
    }

    /**
     * 409 for a stage or status move that the rules do not allow. The body says
     * where the record is, where the caller wanted to take it, and where it may go:
     * {"field": "stage", "from": "WON", "to": "PROPOSAL", "allowed": []}.
     */
    public static ErrorResponseException invalidTransition(String field, Enum<?> from, Enum<?> to,
                                                           Collection<? extends Enum<?>> allowed) {
        List<String> names = allowed.stream().sorted().map(Enum::name).toList();
        String detail = "Cannot change " + field + " from " + from + " to " + to + ". "
                + (names.isEmpty() ? from + " is final" : "Allowed from " + from + ": " + String.join(", ", names));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("field", field);
        problem.setProperty("from", from.name());
        problem.setProperty("to", to.name());
        problem.setProperty("allowed", names);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }

    /** 428: an update arrived without the If-Match header. */
    public static ErrorResponseException preconditionRequired() {
        return error(HttpStatus.PRECONDITION_REQUIRED,
                "If-Match header is required: send the version from the record's ETag, for example If-Match: \"3\"");
    }

    /** 412: the record changed after the caller read it. */
    public static ErrorResponseException preconditionFailed() {
        return error(HttpStatus.PRECONDITION_FAILED,
                "The record was changed by someone else. Fetch it again and retry with the new ETag");
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
