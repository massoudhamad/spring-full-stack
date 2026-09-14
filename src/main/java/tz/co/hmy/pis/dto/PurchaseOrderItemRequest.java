package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;
import tz.co.hmy.pis.model.UnitOfMeasure;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        @Size(max = 40) String itemCode,
        @NotBlank @Size(max = 300) String description,
        @Min(1) int quantity,
        @NotNull UnitOfMeasure unit,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice
) { }
