package tz.co.hmy.pis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * pis.security.jwt.* from application.yml, bound and type-checked at startup.
 *
 * Durations accept "5m", "8h" or "PT5M". A wrong value stops the app starting,
 * rather than failing on the first login.
 *
 * @param expiry        how long an access token (the JWT) lives
 * @param refreshExpiry how long a refresh token lives, if it is never used
 */
@ConfigurationProperties(prefix = "pis.security.jwt")
public record JwtProperties(String secret, Duration expiry, Duration refreshExpiry) { }
