package tz.co.hmy.pis.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.*;
import tz.co.hmy.pis.model.RequisitionStatus;
import tz.co.hmy.pis.service.RequisitionService;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/requisitions")
public class RequisitionController {

    private final RequisitionService service;

    @PostMapping
    public ResponseEntity<RequisitionResponse> create(@Valid @RequestBody RequisitionRequest request,
                                                      UriComponentsBuilder uri) {
        RequisitionResponse created = service.create(request);
        return ResponseEntity
                .created(uri.path("/api/v1/requisitions/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public PageResponse<RequisitionResponse> findAll(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) String department,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(status, department, pageable);
    }

    @GetMapping("/{id}")
    public RequisitionResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public RequisitionResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody RequisitionRequest request) {
        return service.update(id, request);
    }

    // State transitions are POSTs to a named sub-resource, not a PATCH that
    // lets the client set any status it likes.
    @PostMapping("/{id}/submit")
    public RequisitionResponse submit(@PathVariable UUID id) { return service.submit(id); }

    @PostMapping("/{id}/approve")
    public RequisitionResponse approve(@PathVariable UUID id) { return service.approve(id); }

    @PostMapping("/{id}/reject")
    public RequisitionResponse reject(@PathVariable UUID id) { return service.reject(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
