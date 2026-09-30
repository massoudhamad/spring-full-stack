package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PIS no longer has a users table, so "who am I?" is answered from the token:
 * everything PIS knows about the caller is what the authorization server signed.
 */
@Tag(name = "Me", description = "What PIS knows about the caller")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @Operation(summary = "Who is this token for?")
    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken authentication) {
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("subject", jwt.getSubject());                        // username, or client id for a machine
        me.put("client", jwt.getClaimAsStringList("aud"));          // which application asked for the token
        me.put("issuer", jwt.getClaimAsString("iss"));
        me.put("expiresAt", jwt.getExpiresAt());
        me.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).sorted().toList());
        return me;
    }
}
