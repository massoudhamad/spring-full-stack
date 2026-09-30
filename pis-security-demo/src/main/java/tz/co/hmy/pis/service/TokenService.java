package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.config.JwtProperties;
import tz.co.hmy.pis.dto.TokenResponse;
import tz.co.hmy.pis.exception.InvalidRefreshTokenException;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.model.RefreshToken;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.repository.RefreshTokenRepository;
import tz.co.hmy.pis.security.Authorities;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Issues a pair at login: a short-lived JWT access token and a long-lived,
 * revocable refresh token. The refresh token buys a new pair, once.
 */
@RequiredArgsConstructor
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;

    @Transactional
    public TokenResponse login(String username, String password) {
        // The same check as HTTP Basic: DatabaseUserDetailsService + BCrypt.
        // Wrong password, unknown user or disabled account throws AuthenticationException.
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));

        AppUser user = users.findByUsername(auth.getName()).orElseThrow();
        return issue(user, UUID.randomUUID());   // a new login starts a new family
    }

    /**
     * Rotation: every refresh token works exactly once. A second use means two
     * parties hold the same token, so one of them stole it. We can't tell
     * which, so the whole family dies and the real user logs in again.
     *
     * noRollbackFor: the revocation must be committed even though we then
     * throw. Without it, the exception rolls the revocation back.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String presented) {
        RefreshToken current = refreshTokens.findByTokenHash(RefreshToken.hash(presented))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (current.getUsedAt() != null) {                      // reuse: assume theft
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }

        AppUser user = current.getUser();
        if (!user.isEnabled()) {                                // closes the Lesson 3 gap
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        current.setUsedAt(now);
        return issue(user, current.getFamilyId());
    }

    /** Ends this login everywhere it was refreshed. The access token lives on until it expires. */
    @Transactional
    public void logout(String presented) {
        // No error for an unknown token: logout should never tell a caller what exists.
        refreshTokens.findByTokenHash(RefreshToken.hash(presented))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private TokenResponse issue(AppUser user, UUID familyId) {
        Instant now = Instant.now();

        // Roles are read from the database on every login AND every refresh,
        // so a role change reaches the user within one access-token lifetime.
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pis")
                .subject(user.getUsername())      // becomes Authentication.getName() on later requests
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiry()))
                .claim("roles", roles)
                .claim("permissions", Authorities.permissions(user.getRoles()))   // what the user may DO
                .build();

        // Without an explicit header Spring would pick RS256, which needs a private key we don't have.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        // Opaque, not a JWT: it means nothing without a row in refresh_token.
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(RefreshToken.hash(refreshToken), user, familyId,
                now.plus(properties.refreshExpiry())));

        return new TokenResponse(accessToken, "Bearer", properties.expiry().toSeconds(), refreshToken);
    }
}
