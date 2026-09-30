package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.Supplier;
import tz.co.hmy.pis.model.SupplierCategory;
import tz.co.hmy.pis.model.SupplierStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String tin,
        String registrationNumber,
        SupplierCategory category,
        SupplierStatus status,
        String email,
        String phone,
        String address,
        String contactPerson,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
    /** Entity to response. Lives here so there is no mapper class to maintain. */
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(
                s.getId(), s.getName(), s.getTin(), s.getRegistrationNumber(),
                s.getCategory(), s.getStatus(), s.getEmail(), s.getPhone(),
                s.getAddress(), s.getContactPerson(),
                s.getCreatedAt(), s.getUpdatedAt(),
                s.getCreatedBy(), s.getUpdatedBy());
    }
}
