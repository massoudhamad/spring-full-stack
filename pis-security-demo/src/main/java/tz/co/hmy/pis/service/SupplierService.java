package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.*;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.*;
import tz.co.hmy.pis.repository.PurchaseOrderRepository;
import tz.co.hmy.pis.repository.SupplierRepository;

import java.util.UUID;

/**
 * The transaction boundary and the home of the business rules.
 *
 * Controllers translate HTTP; repositories translate SQL. Everything that is
 * actually a decision about procurement belongs here.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (suppliers.existsByTin(request.tin())) {
            throw new DuplicateResourceException("A supplier with TIN " + request.tin() + " already exists");
        }
        if (suppliers.existsByRegistrationNumber(request.registrationNumber())) {
            throw new DuplicateResourceException(
                    "A supplier with registration number " + request.registrationNumber() + " already exists");
        }

        Supplier supplier = new Supplier(
                request.name(), request.tin(), request.registrationNumber(), request.category(),
                request.email(), request.phone(), request.address(), request.contactPerson());

        return SupplierResponse.from(suppliers.save(supplier));
    }

    public PageResponse<SupplierResponse> findAll(SupplierStatus status,
                                                  SupplierCategory category,
                                                  String search,
                                                  Pageable pageable) {
        return PageResponse.from(
                suppliers.search(status, category, search, pageable).map(SupplierResponse::from));
    }

    public SupplierResponse findById(UUID id) {
        return SupplierResponse.from(getOrThrow(id));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = getOrThrow(id);
        // TIN and registration number are identity, not attributes — they do not change.
        supplier.update(request.name(), request.category(), request.email(),
                request.phone(), request.address(), request.contactPerson());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse changeStatus(UUID id, SupplierStatus status) {
        Supplier supplier = getOrThrow(id);
        supplier.changeStatus(status);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void delete(UUID id) {
        Supplier supplier = getOrThrow(id);

        // A supplier with purchase order history is part of the audit trail.
        if (purchaseOrders.search(null, id, PageRequest.of(0, 1)).hasContent()) {
            throw new BusinessRuleException(
                    "Supplier has purchase orders and cannot be deleted. "
                    + "Set the status to SUSPENDED or BLACKLISTED instead.");
        }
        suppliers.delete(supplier);
    }

    private Supplier getOrThrow(UUID id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }
}
