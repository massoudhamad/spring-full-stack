package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.UserEnabledRequest;
import tz.co.hmy.pis.dto.UserRequest;
import tz.co.hmy.pis.dto.UserResponse;
import tz.co.hmy.pis.dto.UserRolesRequest;
import tz.co.hmy.pis.service.UserService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Accounts that can log in")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    @Operation(summary = "Who am I?", description = "The account behind the credentials on this request.")
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        // Not @AuthenticationPrincipal UserDetails: with a token the principal is a Jwt,
        // and that parameter would be null. getName() works for Basic and Bearer alike.
        return service.findByUsername(authentication.getName());
    }

    @Operation(summary = "Create an account (ADMIN)")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request,
                                               UriComponentsBuilder uri) {
        UserResponse created = service.create(request);
        return ResponseEntity
                .created(uri.path("/api/v1/users/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "List accounts (ADMIN)")
    @GetMapping
    public List<UserResponse> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Enable or disable an account (ADMIN)")
    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable UUID id,
                                   @Valid @RequestBody UserEnabledRequest request) {
        return service.setEnabled(id, request.enabled());
    }

    @Operation(summary = "Replace an account's roles (ADMIN)")
    @PutMapping("/{id}/roles")
    public UserResponse setRoles(@PathVariable UUID id, @Valid @RequestBody UserRolesRequest request) {
        return service.setRoles(id, request.roles());
    }
}
