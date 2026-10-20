package tz.co.hmy.pis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * pis.security.login-limit.* from application.yml.
 *
 * @param maxFailures failed logins allowed from one address within the window
 * @param window      how long the count lasts, from the first failure ("1m", "15m")
 */
@ConfigurationProperties(prefix = "pis.security.login-limit")
public record LoginLimitProperties(int maxFailures, Duration window) { }
