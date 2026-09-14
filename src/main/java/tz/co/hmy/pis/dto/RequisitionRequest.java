package tz.co.hmy.pis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record RequisitionRequest(

        @NotBlank(message = "department is required")
        @Size(max = 150)
        String department,

        @NotBlank(message = "requestedBy is required")
        @Size(max = 150)
        String requestedBy,

        @Size(max = 1000)
        String justification,

        @Future(message = "requiredByDate must be in the future")
        LocalDate requiredByDate,

        @NotEmpty(message = "a requisition must have at least one item")
        @Valid
        List<RequisitionItemRequest> items
) { }
