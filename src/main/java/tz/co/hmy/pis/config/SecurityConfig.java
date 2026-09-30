package tz.co.hmy.pis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP Basic authentication.
 *
 * Every request carries  Authorization: Basic base64(username:password).
 * Base64 is an encoding, not encryption, so Basic is only safe over HTTPS.
 *
 * Two separate questions, answered in two separate places:
 *   Authentication — who are you?       userDetailsService() + passwordEncoder()
 *   Authorization  — what may you do?   the rules in filterChain()
 *
 * Users live in memory for now. Replace UserDetailsService with one backed by a
 * users table and nothing else in this class changes.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailSecurityHandler problemHandler;

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

                        // Approving is a different job from requesting.
                        .requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
                                "/api/v1/requisitions/*/reject").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/*/status").hasRole("APPROVER")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers("/api/v1/**").hasRole("OFFICER")

                        // Anything not listed above is refused rather than left open.
                        .anyRequest().denyAll())

                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)   // 401: who are you?
                        .accessDeniedHandler(problemHandler))       // 403: I know you, but no
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${pis.security.officer-password}") String officerPassword,
            @Value("${pis.security.approver-password}") String approverPassword,
            @Value("${pis.security.admin-password}") String adminPassword) {

        // roles("OFFICER") stores the authority "ROLE_OFFICER"; hasRole("OFFICER") checks for it.
        return new InMemoryUserDetailsManager(
                User.withUsername("officer")
                        .password(encoder.encode(officerPassword))
                        .roles("OFFICER")
                        .build(),
                User.withUsername("approver")
                        .password(encoder.encode(approverPassword))
                        .roles("APPROVER")
                        .build(),
                User.withUsername("admin")
                        .password(encoder.encode(adminPassword))
                        .roles("ADMIN", "APPROVER", "OFFICER")
                        .build());
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
