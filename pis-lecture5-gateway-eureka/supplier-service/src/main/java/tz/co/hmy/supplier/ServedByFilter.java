package tz.co.hmy.supplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with which copy of this service answered, e.g.
 * "X-Served-By: supplier-service:8211". Two copies run in this lecture, and
 * this header is how you SEE the load balancer spreading the calls.
 */
@Component
public class ServedByFilter extends OncePerRequestFilter {

    private final String instance;

    public ServedByFilter(@Value("${spring.application.name}") String name,
                          @Value("${server.port}") String port) {
        this.instance = name + ":" + port;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Served-By", instance);
        chain.doFilter(request, response);
    }
}
