package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

/** What the client may send. No id, no status — the server owns those. */
public record InvoiceRequest(

        @NotBlank(message = "invoice number is required")
        @Size(max = 40)
        String invoiceNumber,

        @NotNull(message = "invoice date is required")
        LocalDate invoiceDate,

        @NotNull(message = "supplier is required")
        UUID supplierId,

        LocalDate dueDate,

        @Size(max = 500)
        String notes
) { }
