package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.PurchaseOrder;
import tz.co.hmy.pis.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String orderNumber,
        UUID supplierId,
        String supplierName,
        UUID requisitionId,
        String requisitionReference,
        PurchaseOrderStatus status,
        LocalDate issuedDate,
        LocalDate expectedDeliveryDate,
        String notes,
        BigDecimal total,
        List<PurchaseOrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static PurchaseOrderResponse from(PurchaseOrder po) {
        var requisition = po.getRequisition();
        return new PurchaseOrderResponse(
                po.getId(), po.getOrderNumber(),
                po.getSupplier().getId(), po.getSupplier().getName(),
                requisition == null ? null : requisition.getId(),
                requisition == null ? null : requisition.getReference(),
                po.getStatus(), po.getIssuedDate(), po.getExpectedDeliveryDate(),
                po.getNotes(), po.getTotal(),
                po.getItems().stream().map(PurchaseOrderItemResponse::from).toList(),
                po.getCreatedAt(), po.getUpdatedAt());
    }
}
