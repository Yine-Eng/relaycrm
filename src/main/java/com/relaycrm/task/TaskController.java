package com.relaycrm.task;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;

/**
 * Tasks work like the other records (raw JSON bodies, ETag and If-Match),
 * except that there is no PUT: a task is completed or edited with PATCH.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** ?status=OPEN|DONE|CANCELLED, ?opportunityId=, ?leadId=, ?page=0&size=50. Soonest due first. */
    @GetMapping
    public List<Task> list(@RequestParam(required = false) TaskStatus status,
                           @RequestParam(required = false) Long opportunityId,
                           @RequestParam(required = false) Long leadId,
                           @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, opportunityId, leadId, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable Long id) {
        return RecordResponse.ok(service.get(id));
    }

    /** A manual task. Automatic ones are created by stage changes, never through this endpoint. */
    @PostMapping
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "The new record. Unknown fields are rejected with 400.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskRequest.class)))
    public ResponseEntity<Task> create(@RequestBody JsonNode body) {
        return RecordResponse.created(service.create(body));
    }

    /** Complete ({"status": "DONE"}), cancel or edit a task (JSON Merge Patch). */
    @PatchMapping(path = "/{id}", consumes = "application/merge-patch+json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "JSON Merge Patch (RFC 7396): send only the fields to change, null clears an optional field. Every field is optional here, whatever the schema marks as required.",
            content = @Content(mediaType = "application/merge-patch+json", schema = @Schema(implementation = TaskRequest.class)))
    public ResponseEntity<Task> patch(@PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody JsonNode patch) {
        return RecordResponse.ok(service.patch(id, ifMatch, patch));
    }

    /** Who changed this task, when, and what: newest first (?page=0&size=50). */
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
