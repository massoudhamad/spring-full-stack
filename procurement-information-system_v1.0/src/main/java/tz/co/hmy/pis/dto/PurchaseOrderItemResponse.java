package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.PurchaseOrderItem;
import tz.co.hmy.pis.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id, String itemCode, String description, int quantity,
        UnitOfMeasure unit, BigDecimal unitPrice, BigDecimal lineTotal
) {
    public static PurchaseOrderItemResponse from(PurchaseOrderItem i) {
        return new PurchaseOrderItemResponse(
                i.getId(), i.getItemCode(), i.getDescription(), i.getQuantity(),
                i.getUnit(), i.getUnitPrice(), i.getLineTotal());
    }
}
