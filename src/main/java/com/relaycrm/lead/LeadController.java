package com.relaycrm.lead;

import java.util.List;

import com.relaycrm.audit.AuditEvent;
import com.relaycrm.common.RecordResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;

/**
 * Bodies arrive as raw JSON; the service reads them with RequestReader, which
 * rejects unknown fields and validates, the same way for POST, PUT and PATCH.
 * A single record is returned with its version as the ETag header; PUT and
 * PATCH must send that version back in If-Match (checked in the service).
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping
    public List<Lead> list(@PageableDefault(size = 50, sort = "id") Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lead> get(@PathVariable Long id) {
        return RecordResponse.ok(service.get(id));
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "The new record. Unknown fields are rejected with 400.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LeadRequest.class)))
    public ResponseEntity<Lead> create(@RequestBody JsonNode body) {
        return RecordResponse.created(service.create(body));
    }

    /** Full replacement: optional fields left out are cleared. */
    @PutMapping("/{id}")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "The whole record: optional fields left out are cleared. Unknown fields are rejected with 400.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LeadRequest.class)))
    public ResponseEntity<Lead> update(@PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody JsonNode body) {
        return RecordResponse.ok(service.update(id, ifMatch, body));
    }

    /** Partial update (JSON Merge Patch): fields left out are unchanged, null clears a field. */
    @PatchMapping(path = "/{id}", consumes = "application/merge-patch+json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "JSON Merge Patch (RFC 7396): send only the fields to change, null clears an optional field. Every field is optional here, whatever the schema marks as required.",
            content = @Content(mediaType = "application/merge-patch+json", schema = @Schema(implementation = LeadRequest.class)))
    public ResponseEntity<Lead> patch(@PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody JsonNode patch) {
        return RecordResponse.ok(service.patch(id, ifMatch, patch));
    }

    /** Who changed this record, when, and what: newest first (?page=0&size=50). */
    @GetMapping("/{id}/history")
    public List<AuditEvent> history(@PathVariable Long id, @PageableDefault(size = 50) Pageable pageable) {
        return service.history(id, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
