package com.relaycrm.common;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a JSON body into a create/update request (ContactRequest, LeadRequest,
 * OpportunityRequest) and validates it. Used for POST, PUT and PATCH, so the
 * three verbs reject bad input in exactly the same way:
 *
 *   - a field the request type does not have (a typo, or "orgId") -> 400;
 *   - a value of the wrong type or an unknown enum value          -> 400;
 *   - a broken validation rule (blank name, negative amount...)   -> 400.
 *
 * Every 400 names the offending fields. The strictness applies only to the
 * types read here, not to JSON handling in general.
 */
@Component
public class RequestReader {

    private final ObjectMapper mapper;
    private final Validator validator;

    public RequestReader(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Reads and validates a complete request body (POST, PUT). Pass OnUpdate.class for PUT. */
    public <R extends Record> R read(JsonNode body, Class<R> type, Class<?>... extraGroups) {
        rejectUnknownFields(body, type);
        return bindAndValidate(body, type, extraGroups);
    }

    /**
     * JSON Merge Patch (RFC 7396) for flat records: start from the record's
     * current values, overwrite the fields present in the patch (an explicit
     * null clears the field), then validate the result as if it were a PUT.
     */
    public <R extends Record> R merge(R current, JsonNode patch, Class<R> type) {
        rejectUnknownFields(patch, type);
        ObjectNode merged = mapper.valueToTree(current);
        patch.properties().forEach(field -> merged.set(field.getKey(), field.getValue()));
        return bindAndValidate(merged, type, OnUpdate.class);
    }

    private <R extends Record> void rejectUnknownFields(JsonNode body, Class<R> type) {
        if (body == null || !body.isObject()) {
            throw ApiErrors.badRequest("Request body must be a JSON object");
        }
        Set<String> known = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        Map<String, String> errors = new TreeMap<>();
        body.propertyNames().forEach(name -> {
            if (!known.contains(name)) {
                errors.put(name, "unknown field");
            }
        });
        if (!errors.isEmpty()) {
            throw ApiErrors.invalidFields(errors);
        }
    }

    private <R extends Record> R bindAndValidate(JsonNode body, Class<R> type, Class<?>... extraGroups) {
        R request;
        try {
            request = mapper.treeToValue(body, type);
        } catch (JacksonException e) {
            String field = e.getPath().isEmpty() ? null : e.getPath().get(0).getPropertyName();
            if (field == null) {
                throw ApiErrors.badRequest("Request body could not be read");
            }
            throw ApiErrors.invalidFields(Map.of(field, "invalid value"));
        }

        Class<?>[] groups = new Class<?>[extraGroups.length + 1];
        groups[0] = Default.class;
        System.arraycopy(extraGroups, 0, groups, 1, extraGroups.length);

        Map<String, String> errors = new TreeMap<>();
        for (ConstraintViolation<R> violation : validator.validate(request, groups)) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        if (!errors.isEmpty()) {
            throw ApiErrors.invalidFields(errors);
        }
        return request;
    }
}
