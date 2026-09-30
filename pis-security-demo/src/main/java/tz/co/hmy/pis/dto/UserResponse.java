package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.model.Role;
import tz.co.hmy.pis.security.Authorities;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** No password and no hash. There is no reason for either to ever leave the server. */
public record UserResponse(
        UUID id,
        String username,
        String fullName,
        boolean enabled,
        Set<Role> roles,
        List<String> permissions,
        Instant createdAt,
        String createdBy
) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.isEnabled(),
                Set.copyOf(u.getRoles()), Authorities.permissions(u.getRoles()),
                u.getCreatedAt(), u.getCreatedBy());
    }
}
