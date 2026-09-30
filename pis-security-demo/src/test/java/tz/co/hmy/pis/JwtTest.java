package tz.co.hmy.pis;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.Authorities;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Log in once, then send the token. Most tests go through the real login
 * endpoint, so the whole chain is covered: password check, signing, and
 * signature and expiry checks on the way back in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtTest {

    private static final String UNKNOWN_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired Authorities authorities;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of("APPROVER")));
    }

    /** POST /api/v1/auth/login and return just the token. */
    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"%s\", \"password\": \"%s\" }".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void login_returns_a_bearer_token() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"officer-pass\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            // header.payload.signature
            .andExpect(jsonPath("$.accessToken").value(org.hamcrest.Matchers.matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+")));
    }

    @Test
    void a_wrong_password_returns_401_not_500() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"wrong\" }"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Login failed"))
            .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void the_token_opens_the_api() throws Exception {
        String token = login("officer", "officer-pass");

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());
    }

    @Test
    void roles_inside_the_token_are_enforced() throws Exception {
        String officer = login("officer", "officer-pass");
        String approver = login("approver", "approver-pass");

        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer(officer)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer(approver)))
            .andExpect(status().isNotFound());
    }

    @Test
    void me_and_created_by_work_with_a_token() throws Exception {
        String token = login("officer", "officer-pass");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("officer"));

        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Mafia Island Traders", "tin": "777-111-222",
                      "registrationNumber": "BRELA-2025-7777",
                      "category": "GOODS", "email": "hello@mafiatraders.co.tz" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value("officer"));
    }

    @Test
    void an_officer_cannot_promote_themselves_by_editing_the_token() throws Exception {
        String token = login("officer", "officer-pass");
        String[] parts = token.split("\\.");                       // header . payload . signature

        // Anyone can read the payload...
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // ...and anyone can change it. But the signature was made for the original payload.
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("OFFICER", "ADMIN").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forged + "." + parts[2];

        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID).header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
            .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void an_expired_token_is_rejected() throws Exception {
        // Signed with the real key, but it expired five minutes ago.
        // (The decoder allows 60 seconds of clock skew, so one second ago would still pass.)
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pis").subject("officer")
                .issuedAt(past).expiresAt(past.plus(55, ChronoUnit.MINUTES))
                .claim("roles", List.of("OFFICER"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    /**
     * The price of statelessness. The server does not look the user up on each
     * request, so a token issued before the account was disabled still works
     * until it expires. Short expiry times are what keep this window small.
     */
    @Test
    void a_token_outlives_a_disabled_account() throws Exception {
        String token = login("officer", "officer-pass");

        AppUser officer = users.findByUsername("officer").orElseThrow();
        officer.setEnabled(false);
        users.flush();

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"officer-pass\" }"))
            .andExpect(status().isUnauthorized());          // no new token

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());                    // but the old one still works
    }

    /**
     * jwt() skips login and signing entirely, like @WithRole does for Basic.
     * It needs the permissions too (Lesson 3C): a bare ROLE_ADMIN can't delete.
     */
    @Test
    void jwt_post_processor_tests_rules_without_logging_in() throws Exception {
        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID)
                .with(jwt().authorities(authorities.of(Set.of("OFFICER")))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID)
                .with(jwt().authorities(authorities.of(Set.of("ADMIN")))))
            .andExpect(status().isNotFound());
    }
}
