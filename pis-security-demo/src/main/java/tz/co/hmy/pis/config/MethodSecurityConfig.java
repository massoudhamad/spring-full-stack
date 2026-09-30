package tz.co.hmy.pis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on @PreAuthorize. URL rules in SecurityConfig stay the first line of
 * defence; method rules protect the service itself, whichever controller,
 * job or future endpoint calls it.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig { }
