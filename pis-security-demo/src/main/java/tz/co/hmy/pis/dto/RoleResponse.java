package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;

import java.time.Instant;
import java.util.List;

public record RoleResponse(
        String name,
        String description,
        boolean builtIn,
        List<String> permissions,
        long users,
        Instant updatedAt,
        String updatedBy
) {
    public static RoleResponse from(AppRole role, long users) {
        return new RoleResponse(role.getName(), role.getDescription(), role.isBuiltIn(),
                role.getPermissions().stream().map(Permission::authority).sorted().toList(),
                users, role.getUpdatedAt(), role.getUpdatedBy());
    }
}
