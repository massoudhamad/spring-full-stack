package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.RolePermissionsRequest;
import tz.co.hmy.pis.dto.RoleRequest;
import tz.co.hmy.pis.dto.RoleResponse;
import tz.co.hmy.pis.service.RoleService;

import java.util.List;

/** Needs the role:manage permission (SecurityConfig). */
@Tag(name = "Roles", description = "Roles and the permissions they grant")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService service;

    @Operation(summary = "Every permission the code knows, for building a role")
    @GetMapping("/permissions")
    public List<String> permissions() {
        return service.allPermissions();
    }

    @Operation(summary = "List roles with their permissions")
    @GetMapping("/roles")
    public List<RoleResponse> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Create a role")
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request, UriComponentsBuilder uri) {
        RoleResponse created = service.create(request);
        return ResponseEntity.created(uri.path("/api/v1/roles/{name}").buildAndExpand(created.name()).toUri())
                .body(created);
    }

    @Operation(summary = "Replace a role's permissions")
    @PutMapping("/roles/{name}/permissions")
    public RoleResponse replacePermissions(@PathVariable String name,
                                           @Valid @RequestBody RolePermissionsRequest request) {
        return service.replacePermissions(name, request.permissions());
    }

    @Operation(summary = "Delete a role nobody holds")
    @DeleteMapping("/roles/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        service.delete(name);
    }
}
