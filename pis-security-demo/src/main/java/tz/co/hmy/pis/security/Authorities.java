package tz.co.hmy.pis.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.AppRoleRepository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Role names → authorities, in one place, for every way of logging in.
 *
 * Lesson 3C read the mapping from the Role enum. Now it comes from the role and
 * role_permission tables, so this is a Spring bean with a repository. Each role
 * becomes ROLE_<name> (so hasRole still works) and each of its permissions
 * becomes its own authority (for hasAuthority).
 */
@Component
@RequiredArgsConstructor
public class Authorities {

    private final AppRoleRepository roles;

    @Transactional(readOnly = true)
    public List<GrantedAuthority> of(Collection<String> roleNames) {
        Stream<String> names = roleNames.stream().map(name -> "ROLE_" + name);
        return Stream.concat(names, permissions(roleNames).stream())
                .distinct()
                .sorted()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    /** The union of every role's permissions, e.g. ["requisition:approve", "supplier:read", ...]. */
    @Transactional(readOnly = true)
    public List<String> permissions(Collection<String> roleNames) {
        if (roleNames.isEmpty()) {
            return List.of();
        }
        return roles.findWithPermissions(roleNames).stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::authority)
                .distinct()
                .sorted()
                .toList();
    }
}
