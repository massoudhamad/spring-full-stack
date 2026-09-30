package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotNull;
import tz.co.hmy.pis.model.SupplierStatus;

public record SupplierStatusRequest(@NotNull SupplierStatus status) { }
