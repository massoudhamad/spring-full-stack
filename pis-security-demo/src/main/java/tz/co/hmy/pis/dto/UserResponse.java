package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** No password and no hash. There is no reason for either to ever leave the server. */
public record UserResponse(
        UUID id,
        String username,
        String fullName,
        boolean enabled,
        List<String> roles,
        List<String> permissions,
        Instant createdAt,
        String createdBy
) {
    /** The permissions come from the role tables, so the caller looks them up. */
    public static UserResponse from(AppUser u, List<String> permissions) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.isEnabled(),
                u.getRoles().stream().sorted().toList(), permissions,
                u.getCreatedAt(), u.getCreatedBy());
    }
}
