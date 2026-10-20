package tz.co.hmy.pis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import tz.co.hmy.pis.config.LoginLimitProperties;
import tz.co.hmy.pis.config.ProblemDetailSecurityHandler;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops password guessing: after too many FAILED logins from one address, every
 * login attempt from that address gets 429 until the window ends.
 *
 * Placed BEFORE the login filters (SecurityConfig), for two reasons:
 *   - it must be able to refuse a request before any password is checked;
 *   - it wraps them, so it sees their answer: a 401 means a failed login.
 *
 * Only login attempts are filtered: POST /api/v1/auth/login, or any request with
 * "Authorization: Basic". A request with no credentials is not a guess.
 *
 * Not a @Component, for the same reason as RequestLogFilter.
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN = "/api/v1/auth/login";

    /** Failed logins from one address, counted from the first one in the window. */
    private record Failures(Instant since, int count) { }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final LoginLimitProperties limit;
    private final Clock clock;
    private final ProblemDetailSecurityHandler problems;

    public LoginRateLimitFilter(LoginLimitProperties limit, Clock clock, ProblemDetailSecurityHandler problems) {
        this.limit = limit;
        this.clock = clock;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean basic = authorization != null && authorization.startsWith("Basic ");
        boolean login = "POST".equals(request.getMethod()) && LOGIN.equals(request.getRequestURI());
        return !(basic || login);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // The address the connection came from. NOT the X-Forwarded-For header:
        // anyone can send that, and an attacker would just send a new one each time.
        String address = request.getRemoteAddr();

        Duration wait = blockedFor(address);
        if (wait != null) {
            problems.tooManyLogins(response, wait);    // answer here: the chain stops
            return;
        }

        chain.doFilter(request, response);             // the login filters, and the rest

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            Failures f = failures.compute(address, (a, old) ->
                    old == null || expired(old) ? new Failures(clock.instant(), 1)
                                                : new Failures(old.since(), old.count() + 1));
            log.warn("Failed login {} of {} from {}", f.count(), limit.maxFailures(), address);
        }
    }

    /** How long this address must wait, or null if it may try. */
    private Duration blockedFor(String address) {
        Failures f = failures.get(address);
        if (f == null || expired(f) || f.count() < limit.maxFailures()) return null;
        return Duration.between(clock.instant(), f.since().plus(limit.window()));
    }

    private boolean expired(Failures f) {
        return !clock.instant().isBefore(f.since().plus(limit.window()));
    }
}
