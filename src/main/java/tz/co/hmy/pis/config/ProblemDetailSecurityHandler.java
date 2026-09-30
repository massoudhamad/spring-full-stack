package tz.co.hmy.pis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * 401 and 403 as RFC 9457 problem details, the same shape as every other error.
 *
 * GlobalExceptionHandler cannot do this: security runs in a servlet filter,
 * before the request reaches any controller, so @RestControllerAdvice never sees it.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** No credentials, or wrong ones. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        // A 401 must say which scheme to use (RFC 9110). This header is also
        // what makes a browser show its own username/password dialog.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"pis\"");
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
                "Send a valid username and password using HTTP Basic", "unauthorized");
    }

    /** Valid credentials, but the role does not allow this operation. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied",
                "Your role does not permit this operation", "forbidden");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title,
                       String detail, String type) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://hmy.co.tz/problems/" + type));
        pd.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
