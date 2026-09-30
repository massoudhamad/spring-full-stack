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
import tz.co.hmy.pis.model.PurchaseOrderStatus;
import tz.co.hmy.pis.service.PurchaseOrderService;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request,
                                                        UriComponentsBuilder uri) {
        PurchaseOrderResponse created = service.create(request);
        return ResponseEntity
                .created(uri.path("/api/v1/purchase-orders/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public PageResponse<PurchaseOrderResponse> findAll(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) UUID supplierId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(status, supplierId, pageable);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody PurchaseOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/issue")
    public PurchaseOrderResponse issue(@PathVariable UUID id) { return service.issue(id); }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancel(@PathVariable UUID id) { return service.cancel(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
