package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.Requisition;
import tz.co.hmy.pis.model.RequisitionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RequisitionResponse(
        UUID id,
        String reference,
        String department,
        String requestedBy,
        RequisitionStatus status,
        String justification,
        LocalDate requiredByDate,
        BigDecimal estimatedTotal,
        List<RequisitionItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static RequisitionResponse from(Requisition r) {
        return new RequisitionResponse(
                r.getId(), r.getReference(), r.getDepartment(), r.getRequestedBy(),
                r.getStatus(), r.getJustification(), r.getRequiredByDate(),
                r.getEstimatedTotal(),
                r.getItems().stream().map(RequisitionItemResponse::from).toList(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
