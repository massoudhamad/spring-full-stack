package tz.co.hmy.pis.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tz.co.hmy.pis.dto.InvoiceRequest;
import tz.co.hmy.pis.dto.InvoiceResponse;
import tz.co.hmy.pis.dto.PageResponse;
import tz.co.hmy.pis.service.InvoiceService;

import java.util.UUID;

/** The five CRUD endpoints, nothing else. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<InvoiceResponse> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.getAll(pageable);
    }

    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody InvoiceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
