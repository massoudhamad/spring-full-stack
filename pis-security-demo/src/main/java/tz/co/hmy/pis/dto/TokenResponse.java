package tz.co.hmy.pis.dto;

/** Field names follow OAuth 2 (RFC 6749 §5.1), so any client library understands them. */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) { }
