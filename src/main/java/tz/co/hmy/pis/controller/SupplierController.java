package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import tz.co.hmy.pis.model.SupplierCategory;
import tz.co.hmy.pis.model.SupplierStatus;
import tz.co.hmy.pis.service.SupplierService;

import java.util.UUID;

/**
 * HTTP only. No business rules, no repository calls — if logic appears here it
 * cannot be reused or tested without a web layer.
 */
@Tag(name = "Suppliers", description = "Supplier registration and approval")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService service;

    @Operation(summary = "Register a supplier",
               description = "TIN and registration number must be unique. New suppliers start as PENDING_APPROVAL.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; the Location header carries the new URL"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content()),
            @ApiResponse(responseCode = "409", description = "TIN or registration number already exists", content = @Content())
    })
    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request,
                                                   UriComponentsBuilder uri) {
        SupplierResponse created = service.create(request);
        // 201 with a Location header, not 200 with a body and no clue where it went.
        return ResponseEntity
                .created(uri.path("/api/v1/suppliers/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "List suppliers",
               description = "Filter by status, category, or a free-text search over name and TIN.")
    @GetMapping
    public PageResponse<SupplierResponse> findAll(
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(required = false) SupplierCategory category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findAll(status, category, search, pageable);
    }

    @Operation(summary = "Fetch one supplier")
    @ApiResponses({
            // Listing 200 explicitly: a lone @ApiResponse REPLACES the generated
            // default rather than adding to it, which silently loses the 200.
            @ApiResponse(responseCode = "200", description = "The supplier"),
            @ApiResponse(responseCode = "404", description = "No supplier with that id", content = @Content())
    })
    @GetMapping("/{id}")
    public SupplierResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public SupplierResponse changeStatus(@PathVariable UUID id,
                                         @Valid @RequestBody SupplierStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
