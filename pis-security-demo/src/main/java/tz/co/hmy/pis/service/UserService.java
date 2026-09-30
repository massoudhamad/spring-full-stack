package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.UserRequest;
import tz.co.hmy.pis.dto.UserResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppRoleRepository;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.Authorities;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Authorities authorities;

    @Transactional
    public UserResponse create(UserRequest request) {
        String username = request.username().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException("Username " + username + " is already taken");
        }
        requireExistingRoles(request.roles());
        // The only place a plain password exists: it is hashed before it reaches the entity.
        AppUser user = new AppUser(username, passwordEncoder.encode(request.password()),
                request.fullName(), request.roles());
        return toResponse(users.save(user));
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse findByUsername(String username) {
        return users.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /** Disable, don't delete: the user's name stays on every record they created. */
    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled) {
        AppUser user = getOrThrow(id);
        user.setEnabled(enabled);
        return toResponse(user);
    }

    /** Replaces the user's roles. A Basic login sees the change on its next request; a JWT at its next refresh. */
    @Transactional
    public UserResponse setRoles(UUID id, Set<String> roleNames) {
        AppUser user = getOrThrow(id);
        requireExistingRoles(roleNames);
        user.getRoles().clear();
        user.getRoles().addAll(roleNames);
        return toResponse(user);
    }

    /** Checked here so an unknown role is a clear 422, not a foreign-key error from the database. */
    private void requireExistingRoles(Set<String> roleNames) {
        Set<String> unknown = new TreeSet<>(roleNames);
        roles.findAllById(roleNames).forEach(role -> unknown.remove(role.getName()));
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Unknown role: " + String.join(", ", unknown));
        }
    }

    private AppUser getOrThrow(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.from(user, authorities.permissions(user.getRoles()));
    }
}
