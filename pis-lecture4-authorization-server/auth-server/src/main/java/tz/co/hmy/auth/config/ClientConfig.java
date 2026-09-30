package tz.co.hmy.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

/**
 * Every application allowed to ask for tokens must be registered here first.
 * Three clients, one of each kind OAuth2 cares about:
 *
 *   pis-web        a browser app. It can't keep a secret (anyone can read its
 *                  JavaScript), so it is PUBLIC and must use PKCE instead.
 *   pis-bff        a backend-for-frontend: server code, so it CAN keep a secret.
 *                  Confidential clients also get refresh tokens.
 *   pis-reporting  a nightly job with no user at all: client credentials.
 */
@Configuration
public class ClientConfig {

    @Bean
    public RegisteredClientRepository registeredClients(AuthProperties properties, PasswordEncoder encoder) {
        AuthProperties.Clients c = properties.clients();

        // Short access tokens, like Lesson 3B. Refresh tokens rotate: each works once.
        TokenSettings userTokens = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .reuseRefreshTokens(false)
                .build();

        RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("pis-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)   // public: no secret
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(c.redirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("pis")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)                    // PKCE is mandatory
                        .requireAuthorizationConsent(false)       // our own app: no "allow access?" screen
                        .build())
                .tokenSettings(userTokens)
                .build();

        RegisteredClient bff = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("pis-bff")
                .clientSecret(encoder.encode(c.bffSecret()))       // stored hashed, like a password
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(c.redirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("pis")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)                    // PKCE for confidential clients too (OAuth 2.1)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(userTokens)
                .build();

        RegisteredClient reporting = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("pis-reporting")
                .clientSecret(encoder.encode(c.reportingSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("suppliers.read")                          // this job may only read suppliers
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(web, bff, reporting);
    }
}
