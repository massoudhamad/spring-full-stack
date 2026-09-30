package tz.co.hmy.pis.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.model.Role;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Roles → authorities, in one place, for every way of logging in.
 *
 * Each role becomes ROLE_<name> (so hasRole still works) and each of its
 * permissions becomes its own authority (for hasAuthority).
 */
public final class Authorities {

    private Authorities() { }

    public static List<GrantedAuthority> of(Collection<Role> roles) {
        Stream<String> roleNames = roles.stream().map(role -> "ROLE_" + role.name());
        return Stream.concat(roleNames, permissions(roles).stream())
                .distinct()
                .sorted()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    /** The union of every role's permissions, e.g. ["requisition:approve", "supplier:read", ...]. */
    public static List<String> permissions(Collection<Role> roles) {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .map(Permission::authority)
                .distinct()
                .sorted()
                .toList();
    }
}
