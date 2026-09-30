package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tz.co.hmy.pis.model.Permission;

import java.util.Set;

/** A new role. Permissions are written as authorities: "supplier:read". */
public record RoleRequest(

        @NotBlank(message = "name is required")
        @Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores")
        String name,

        @NotBlank(message = "description is required")
        @Size(max = 200)
        String description,

        @NotNull(message = "permissions is required (it may be empty)")
        Set<Permission> permissions
) { }
