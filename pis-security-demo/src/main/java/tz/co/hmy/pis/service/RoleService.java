package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.RoleRequest;
import tz.co.hmy.pis.dto.RoleResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.AppRoleRepository;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Roles are data now: an admin creates them and changes their permissions
 * while PIS is running. Three rules keep that safe:
 *   1. ADMIN can't be edited or deleted, so nobody can lock everyone out.
 *   2. A built-in role can't be deleted, because the code and the docs refer to it.
 *   3. A role that users still hold can't be deleted.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final AppRoleRepository roles;
    private final AppUserRepository users;

    public List<RoleResponse> findAll() {
        return roles.findAllWithPermissions().stream().map(this::toResponse).toList();
    }

    public List<String> allPermissions() {
        return Arrays.stream(Permission.values()).map(Permission::authority).toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roles.existsById(request.name())) {
            throw new DuplicateResourceException("Role " + request.name() + " already exists");
        }
        AppRole role = new AppRole(request.name(), request.description(), request.permissions());
        return toResponse(roles.save(role));
    }

    @Transactional
    public RoleResponse replacePermissions(String name, Set<Permission> permissions) {
        AppRole role = getOrThrow(name);
        if (AppRole.ADMIN.equals(name)) {
            throw new BusinessRuleException("The ADMIN role always has every permission and can't be changed");
        }
        role.replacePermissions(permissions);
        return toResponse(roles.saveAndFlush(role));
    }

    @Transactional
    public void delete(String name) {
        AppRole role = getOrThrow(name);
        if (role.isBuiltIn()) {
            throw new BusinessRuleException("Role " + name + " is built in and can't be deleted");
        }
        long holders = users.countWithRole(name);
        if (holders > 0) {
            throw new BusinessRuleException("Role " + name + " is held by " + holders
                    + " user(s). Take it away from them first.");
        }
        roles.delete(role);
    }

    private AppRole getOrThrow(String name) {
        return roles.findById(name).orElseThrow(() -> new ResourceNotFoundException("Role", name));
    }

    private RoleResponse toResponse(AppRole role) {
        return RoleResponse.from(role, users.countWithRole(role.getName()));
    }
}
