package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.UserRequest;
import tz.co.hmy.pis.dto.UserResponse;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(UserRequest request) {
        String username = request.username().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException("Username " + username + " is already taken");
        }
        // The only place a plain password exists: it is hashed before it reaches the entity.
        AppUser user = new AppUser(username, passwordEncoder.encode(request.password()),
                request.fullName(), request.roles());
        return UserResponse.from(users.save(user));
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse findByUsername(String username) {
        return users.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /** Disable, don't delete: the user's name stays on every record they created. */
    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setEnabled(enabled);
        return UserResponse.from(user);
    }
}
