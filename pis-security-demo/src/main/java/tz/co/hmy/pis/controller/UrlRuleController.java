package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.UrlRuleMatch;
import tz.co.hmy.pis.dto.UrlRuleRequest;
import tz.co.hmy.pis.dto.UrlRuleResponse;
import tz.co.hmy.pis.security.DatabaseUrlRules;
import tz.co.hmy.pis.service.UrlRuleService;

import java.util.List;

/**
 * Needs the role:manage permission (SecurityConfig, in code).
 *
 * After every change it reloads the rules. That happens HERE, after the service
 * method has returned, because by then its transaction has committed: a reload
 * inside the transaction could keep a rule that is then rolled back.
 */
@Tag(name = "URL rules", description = "Which permission each endpoint needs")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/url-rules")
public class UrlRuleController {

    private final UrlRuleService service;
    private final DatabaseUrlRules urlRules;

    @Operation(summary = "List the rules, in the order they are checked")
    @GetMapping
    public List<UrlRuleResponse> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Which rule decides a request, e.g. ?method=GET&path=/api/v1/invoices")
    @GetMapping("/match")
    public UrlRuleMatch match(@RequestParam String method, @RequestParam String path) {
        return service.match(method, path);
    }

    @Operation(summary = "Add a rule")
    @PostMapping
    public ResponseEntity<UrlRuleResponse> create(@Valid @RequestBody UrlRuleRequest request, UriComponentsBuilder uri) {
        UrlRuleResponse created = service.create(request);
        urlRules.reload();
        return ResponseEntity.created(uri.path("/api/v1/url-rules/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Change a rule")
    @PutMapping("/{id}")
    public UrlRuleResponse update(@PathVariable Long id, @Valid @RequestBody UrlRuleRequest request) {
        UrlRuleResponse updated = service.update(id, request);
        urlRules.reload();
        return updated;
    }

    @Operation(summary = "Delete a rule")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
        urlRules.reload();
    }
}
