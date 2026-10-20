package tz.co.hmy.pis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * An access log that names the user:  GET /api/v1/suppliers -> 200 by officer in 12 ms
 *
 * Placed AFTER the login filters (SecurityConfig), so the SecurityContext already
 * says who is calling. It reads the status after the rest of the chain has run,
 * so a request refused by AuthorizationFilter is logged with its 401 or 403.
 *
 * A login that FAILS never gets here: BasicAuthenticationFilter answers 401 itself
 * and stops the chain. LoginRateLimitFilter, placed before it, does see those.
 *
 * Not a @Component on purpose: Spring Boot also registers every Filter BEAN as a
 * plain servlet filter, outside the security chain. A plain Filter then runs twice
 * per request. OncePerRequestFilter notices and skips the second run, but there's
 * no reason to depend on that: create it with new, in SecurityConfig.
 */
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);          // the rest of the chain, then the controller
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} by {} in {} ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), who(), ms);
        }
    }

    private static String who() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return "anonymous";
        return auth.getName();
    }
}
