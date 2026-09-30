package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.InvoiceRequest;
import tz.co.hmy.pis.dto.InvoiceResponse;
import tz.co.hmy.pis.dto.PageResponse;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.Invoice;
import tz.co.hmy.pis.model.Supplier;
import tz.co.hmy.pis.repository.InvoiceRepository;
import tz.co.hmy.pis.repository.SupplierRepository;

import java.util.UUID;

/** Plain CRUD: create, read, update, delete. No workflow transitions. */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final SupplierRepository suppliers;

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        if (invoices.existsByInvoiceNumber(request.invoiceNumber())) {
            throw new DuplicateResourceException(
                    "An invoice numbered " + request.invoiceNumber() + " already exists");
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(request.invoiceNumber())
                .invoiceDate(request.invoiceDate())
                .supplier(supplierOrThrow(request.supplierId()))
                .dueDate(request.dueDate())
                .notes(request.notes())
                .build();

        return InvoiceResponse.from(invoices.save(invoice));
    }

    public PageResponse<InvoiceResponse> getAll(Pageable pageable) {
        return PageResponse.from(invoices.findAll(pageable).map(InvoiceResponse::from));
    }

    public InvoiceResponse findById(UUID id) {
        return InvoiceResponse.from(getOrThrow(id));
    }

    @Transactional
    public InvoiceResponse update(UUID id, InvoiceRequest request) {
        Invoice invoice = getOrThrow(id);
        // The invoice number is identity, not an attribute — it does not change.
        invoice.update(request.invoiceDate(), supplierOrThrow(request.supplierId()),
                invoice.getPurchaseOrder(), request.dueDate(), request.notes());
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public void delete(UUID id) {
        invoices.delete(getOrThrow(id));
    }

    private Invoice getOrThrow(UUID id) {
        return invoices.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    private Supplier supplierOrThrow(UUID id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }
}
