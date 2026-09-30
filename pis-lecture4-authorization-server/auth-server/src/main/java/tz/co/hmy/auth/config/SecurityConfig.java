package tz.co.hmy.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.Set;

/**
 * Two filter chains, checked in @Order:
 *
 *   1. The OAuth2 endpoints: /oauth2/authorize, /oauth2/token, /oauth2/jwks,
 *      /.well-known/openid-configuration, /userinfo ... built by Spring Authorization Server.
 *   2. Everything else, mainly the login page where a person types their password.
 *
 * This server is the ONLY place a user's password is ever typed or checked.
 * PIS never sees it; PIS only sees tokens this server signed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .oidc(Customizer.withDefaults()))     // OpenID Connect: id_token, /userinfo, discovery
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // A browser that isn't logged in yet is sent to the login page,
                // and comes back to /oauth2/authorize afterwards.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), browsersOnly()));
        return http.build();
    }

    /**
     * Requests that explicitly ask for HTML. curl and most API clients send
     * "Accept: *\/*", which would otherwise match too, and they would get a 302
     * to an HTML login page instead of a 401 they can act on.
     */
    private static MediaTypeRequestMatcher browsersOnly() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain loginChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // Spring's built-in login page. It has CSRF protection, because here
                // a browser with a session cookie really is involved.
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /**
     * The same three users as PIS Lesson 1. Kept in memory so this lecture
     * stays about OAuth2; Lesson 2's app_user table would plug in here instead.
     */
    @Bean
    public UserDetailsService users(AuthProperties properties, PasswordEncoder encoder) {
        AuthProperties.Users u = properties.users();
        return new InMemoryUserDetailsManager(
                User.withUsername("officer").password(encoder.encode(u.officerPassword()))
                        .roles("OFFICER").build(),
                User.withUsername("approver").password(encoder.encode(u.approverPassword()))
                        .roles("APPROVER").build(),
                User.withUsername("admin").password(encoder.encode(u.adminPassword()))
                        .roles("ADMIN", "APPROVER", "OFFICER").build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
