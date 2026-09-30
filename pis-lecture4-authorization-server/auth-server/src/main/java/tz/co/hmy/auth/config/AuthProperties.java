package tz.co.hmy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** pis.auth.* from application.yml. */
@ConfigurationProperties(prefix = "pis.auth")
public record AuthProperties(String issuer, Users users, Clients clients) {

    public record Users(String officerPassword, String approverPassword, String adminPassword) { }

    public record Clients(String bffSecret, String reportingSecret, String redirectUri) { }
}
