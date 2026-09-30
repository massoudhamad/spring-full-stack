package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.*;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.*;
import tz.co.hmy.pis.repository.*;

import java.time.LocalDate;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrders;
    private final SupplierRepository suppliers;
    private final RequisitionRepository requisitions;
    private final ReferenceGenerator references;

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Purchase orders may only be raised against an ACTIVE supplier. This one is "
                    + supplier.getStatus() + ".");
        }

        Requisition requisition = null;
        if (request.requisitionId() != null) {
            requisition = requisitions.findById(request.requisitionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Requisition", request.requisitionId()));

            if (requisition.getStatus() != RequisitionStatus.APPROVED) {
                throw new BusinessRuleException(
                        "Only an APPROVED requisition can be converted to a purchase order");
            }
            requisition.markConverted();
        }

        PurchaseOrder order = new PurchaseOrder(
                references.nextPurchaseOrderNumber(), supplier, requisition,
                request.expectedDeliveryDate(), request.notes());

        request.items().forEach(i -> order.addItem(toItem(i)));

        return PurchaseOrderResponse.from(purchaseOrders.save(order));
    }

    public PageResponse<PurchaseOrderResponse> findAll(PurchaseOrderStatus status,
                                                       UUID supplierId,
                                                       Pageable pageable) {
        return PageResponse.from(
                purchaseOrders.search(status, supplierId, pageable).map(PurchaseOrderResponse::from));
    }

    public PurchaseOrderResponse findById(UUID id) {
        return PurchaseOrderResponse.from(getWithItems(id));
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, PurchaseOrderRequest request) {
        PurchaseOrder order = getWithItems(id);
        requireDraft(order);

        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        order.update(supplier, request.expectedDeliveryDate(), request.notes());
        order.clearItems();
        request.items().forEach(i -> order.addItem(toItem(i)));

        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse issue(UUID id) {
        PurchaseOrder order = getWithItems(id);
        requireDraft(order);
        order.issue(LocalDate.now());
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder order = getWithItems(id);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new BusinessRuleException("A received purchase order cannot be cancelled");
        }
        order.cancel();
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public void delete(UUID id) {
        PurchaseOrder order = getWithItems(id);
        requireDraft(order);
        purchaseOrders.delete(order);
    }

    private static PurchaseOrderItem toItem(PurchaseOrderItemRequest r) {
        return new PurchaseOrderItem(r.itemCode(), r.description(), r.quantity(),
                r.unit(), r.unitPrice());
    }

    private void requireDraft(PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessRuleException(
                    "A purchase order in status " + order.getStatus() + " can no longer be changed");
        }
    }

    private PurchaseOrder getWithItems(UUID id) {
        return purchaseOrders.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }
}
