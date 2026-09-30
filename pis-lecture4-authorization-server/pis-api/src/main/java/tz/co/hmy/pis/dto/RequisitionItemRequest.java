package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;
import tz.co.hmy.pis.model.UnitOfMeasure;

import java.math.BigDecimal;

public record RequisitionItemRequest(

        @Size(max = 40)
        String itemCode,

        @NotBlank(message = "description is required")
        @Size(max = 300)
        String description,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        @NotNull(message = "unit is required")
        UnitOfMeasure unit,

        @NotNull(message = "estimated unit price is required")
        @DecimalMin(value = "0.00", message = "price cannot be negative")
        @Digits(integer = 17, fraction = 2)
        BigDecimal estimatedUnitPrice
) { }
