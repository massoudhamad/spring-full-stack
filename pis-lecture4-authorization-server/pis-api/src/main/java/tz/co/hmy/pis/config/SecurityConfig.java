package tz.co.hmy.pis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;

/**
 * PIS as a pure OAuth2 resource server.
 *
 * PIS has no login, no passwords, no users table and no signing key any more.
 * It accepts only "Authorization: Bearer <token>" where the token was signed by
 * the authorization server named in spring.security.oauth2.resourceserver.jwt.issuer-uri.
 * At startup Spring reads that server's discovery document, finds its public
 * keys (JWKS), and checks every token's signature, expiry and "iss" against them.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailSecurityHandler problemHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // First match wins, as in every lesson so far.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()

                        // Who is this token for? Users and machine clients alike.
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
                                "/api/v1/requisitions/*/reject").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/*/status").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")

                        // Reading suppliers: any PIS user, or a machine client granted
                        // the suppliers.read scope (the nightly reporting job).
                        .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**")
                                .hasAnyAuthority("ROLE_OFFICER", "ROLE_APPROVER", "ROLE_ADMIN", "SCOPE_suppliers.read")
                        // Everything else to read: people only.
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("OFFICER", "APPROVER", "ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("OFFICER")

                        .anyRequest().denyAll())

                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .build();
    }

    /**
     * Two kinds of permission arrive in a token, and PIS uses both:
     *   "roles": ["OFFICER"]          what the PERSON may do    → ROLE_OFFICER
     *   "scope": "suppliers.read"     what the CLIENT was granted → SCOPE_suppliers.read
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();   // "scope" → SCOPE_x

        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        Converter<Jwt, Collection<GrantedAuthority>> both = jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.addAll(roles.convert(jwt));
            return authorities;
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(both);
        return converter;
    }
}
