package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.RequisitionItem;
import tz.co.hmy.pis.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.UUID;

public record RequisitionItemResponse(
        UUID id,
        String itemCode,
        String description,
        int quantity,
        UnitOfMeasure unit,
        BigDecimal estimatedUnitPrice,
        BigDecimal lineTotal
) {
    public static RequisitionItemResponse from(RequisitionItem i) {
        return new RequisitionItemResponse(
                i.getId(), i.getItemCode(), i.getDescription(), i.getQuantity(),
                i.getUnit(), i.getEstimatedUnitPrice(), i.getLineTotal());
    }
}
