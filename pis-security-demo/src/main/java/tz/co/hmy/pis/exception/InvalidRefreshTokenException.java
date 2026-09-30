package tz.co.hmy.pis.exception;

/** Unknown, expired, used, revoked, or belonging to a disabled account. The client can't tell which. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() { super("Invalid refresh token"); }
}
