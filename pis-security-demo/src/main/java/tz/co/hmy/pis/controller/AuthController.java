package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tz.co.hmy.pis.dto.LoginRequest;
import tz.co.hmy.pis.dto.RefreshRequest;
import tz.co.hmy.pis.dto.TokenResponse;
import tz.co.hmy.pis.service.TokenService;

@Tag(name = "Authentication", description = "Exchange a username and password for tokens")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TokenService tokenService;

    @Operation(summary = "Log in",
               description = "Returns an access token (send it as  Authorization: Bearer <token>) and a refresh token.")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenService.login(request.username(), request.password());
    }

    @Operation(summary = "Refresh",
               description = "Trade a refresh token for a new pair. Each refresh token works once. "
                       + "Send no Authorization header: the refresh token in the body is the credential.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenService.refresh(request.refreshToken());
    }

    @Operation(summary = "Log out", description = "Revokes the refresh token and every token rotated from it.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.logout(request.refreshToken());
    }
}
