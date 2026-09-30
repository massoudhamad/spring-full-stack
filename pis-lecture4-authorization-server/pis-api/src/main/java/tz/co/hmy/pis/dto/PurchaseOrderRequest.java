package tz.co.hmy.pis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull(message = "supplierId is required") UUID supplierId,
        UUID requisitionId,
        @Future LocalDate expectedDeliveryDate,
        @Size(max = 500) String notes,
        @NotEmpty(message = "a purchase order must have at least one item")
        @Valid List<PurchaseOrderItemRequest> items
) { }
