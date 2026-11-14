package com.relaycrm.lead;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Bodies arrive as raw JSON; the service reads them with RequestReader, which
 * rejects unknown fields and validates, the same way for POST, PUT and PATCH.
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
    public Lead get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Lead create(@RequestBody JsonNode body) {
        return service.create(body);
    }

    /** Full replacement: optional fields left out are cleared. */
    @PutMapping("/{id}")
    public Lead update(@PathVariable Long id, @RequestBody JsonNode body) {
        return service.update(id, body);
    }

    /** Partial update (JSON Merge Patch): fields left out are unchanged, null clears a field. */
    @PatchMapping(path = "/{id}", consumes = "application/merge-patch+json")
    public Lead patch(@PathVariable Long id, @RequestBody JsonNode patch) {
        return service.patch(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
