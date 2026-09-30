package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;
import tz.co.hmy.pis.model.SupplierCategory;

/**
 * What the client may send. Deliberately not the entity: the entity has an id,
 * a status and audit columns that the client must not be able to set.
 */
public record SupplierRequest(

        @NotBlank(message = "name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "TIN is required")
        @Pattern(regexp = "\\d{3}-\\d{3}-\\d{3}", message = "TIN must look like 123-456-789")
        String tin,

        @NotBlank(message = "registration number is required")
        @Size(max = 50)
        String registrationNumber,

        @NotNull(message = "category is required")
        SupplierCategory category,

        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        @Size(max = 300)
        String address,

        @Size(max = 150)
        String contactPerson
) { }
