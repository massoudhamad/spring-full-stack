package tz.co.hmy.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderRequest(
        @NotBlank(message = "supplierId is required") String supplierId,
        @NotBlank(message = "item is required") String item,
        @Min(value = 1, message = "quantity must be at least 1") int quantity,
        @NotNull(message = "unitPrice is required") @DecimalMin(value = "0.00", message = "unitPrice cannot be negative") BigDecimal unitPrice
) { }
