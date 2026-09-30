package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotNull;
import tz.co.hmy.pis.model.Permission;

import java.util.Set;

public record RolePermissionsRequest(
        @NotNull(message = "permissions is required (it may be empty)")
        Set<Permission> permissions
) { }
