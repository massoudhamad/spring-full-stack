package tz.co.hmy.pis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two ways to prove who you are, both checked by the same rules:
 *
 *   Authorization: Basic base64(username:password)   password on every request (Lessons 1-2)
 *   Authorization: Bearer <JWT>                      password once, at POST /api/v1/auth/login
 *
 * Base64 and JWT payloads are both readable by anyone, so either is only safe over HTTPS.
 *
 * Two separate questions, answered in two separate places:
 *   Authentication — who are you?       DatabaseUserDetailsService + passwordEncoder()
 *   Authorization  — what may you do?   the rules in filterChain()
 *
 * Users live in the app_user table. DatabaseUserDetailsService is the only
 * UserDetailsService bean, so Spring Security picks it up without being told.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailSecurityHandler problemHandler;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protects browser sessions that send cookies automatically.
                // This API has no session and no cookie, so there is nothing to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Rules are checked top to bottom and the FIRST match wins,
                // so specific paths go above general ones.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()

                        // You can't need a token to get a token. For refresh and logout,
                        // the refresh token in the request body is the credential.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()

                        // Anyone logged in may ask who they are.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()

                        // From here on, every rule names a PERMISSION, never a role.
                        // Which roles hold which permission is decided once, in Role.
                        .requestMatchers("/api/v1/users/**").hasAuthority("user:manage")

                        // Specific actions first: the first match wins.
                        .requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
                                "/api/v1/requisitions/*/reject").hasAuthority("requisition:approve")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/*/status").hasAuthority("supplier:approve")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAuthority("record:delete")

                        // Then read and write, one pair per resource.
                        .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**").hasAuthority("supplier:read")
                        .requestMatchers("/api/v1/suppliers/**").hasAuthority("supplier:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/requisitions/**").hasAuthority("requisition:read")
                        .requestMatchers("/api/v1/requisitions/**").hasAuthority("requisition:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/purchase-orders/**").hasAuthority("purchase-order:read")
                        .requestMatchers("/api/v1/purchase-orders/**").hasAuthority("purchase-order:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/invoices/**").hasAuthority("invoice:read")
                        .requestMatchers("/api/v1/invoices/**").hasAuthority("invoice:write")

                        // Anything not listed above is refused rather than left open.
                        .anyRequest().denyAll())

                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))

                // Reads "Authorization: Bearer ...", checks signature and expiry with the
                // JwtDecoder bean, and turns the roles claim into ROLE_ authorities.
                .oauth2ResourceServer(jwt -> jwt
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)   // bad or expired token
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)   // 401: who are you?
                        .accessDeniedHandler(problemHandler))       // 403: I know you, but no
                .build();
    }

    /**
     * The same AuthenticationManager that HTTP Basic uses (DatabaseUserDetailsService
     * + BCrypt), exposed so TokenService can check a password at login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt by default. Hashes are stored with a prefix, e.g. {bcrypt}$2a$10$...,
     * so the algorithm can be changed later without invalidating existing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
