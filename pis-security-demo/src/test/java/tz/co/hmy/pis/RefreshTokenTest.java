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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.model.RefreshToken;
import tz.co.hmy.pis.model.Role;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Refresh tokens: one use each, stored as a hash, revoked by logout, by a
 * disabled account, and, for the whole family, by reuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshTokenTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder encoder;

    /** accessToken and refreshToken from one response. */
    record Pair(String access, String refresh) { }

    @BeforeEach
    void createOfficer() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of(Role.OFFICER)));
    }

    private Pair login() throws Exception {
        return pair(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"officer-pass\" }")));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"refreshToken\": \"%s\" }".formatted(refreshToken)));
    }

    private static Pair pair(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Pair(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private static String rolesIn(String accessToken) {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.roles").toString();
    }

    @Test
    void login_returns_an_opaque_refresh_token_next_to_the_access_token() throws Exception {
        Pair tokens = login();

        assertThat(tokens.access()).contains(".");                 // a JWT: header.payload.signature
        assertThat(tokens.refresh()).doesNotContain(".").hasSize(43);  // 32 random bytes, base64url
    }

    @Test
    void the_refresh_token_is_stored_only_as_a_hash() throws Exception {
        Pair tokens = login();

        assertThat(refreshTokens.findByTokenHash(tokens.refresh())).isEmpty();
        assertThat(refreshTokens.findByTokenHash(RefreshToken.hash(tokens.refresh()))).isPresent();
    }

    @Test
    void refresh_returns_a_new_pair_that_works() throws Exception {
        Pair first = login();
        Pair second = pair(refresh(first.refresh()));

        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second.access()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("officer"));
    }

    @Test
    void reusing_a_refresh_token_revokes_the_whole_family() throws Exception {
        Pair first = login();
        Pair second = pair(refresh(first.refresh()));      // first.refresh is now used

        refresh(first.refresh())                           // an attacker replays the old one
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Invalid refresh token"));

        refresh(second.refresh())                          // and the real user is logged out too
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokes_the_refresh_token() throws Exception {
        Pair tokens = login();

        mvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"refreshToken\": \"%s\" }".formatted(tokens.refresh())))
            .andExpect(status().isNoContent());

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void a_disabled_account_cannot_refresh() throws Exception {
        Pair tokens = login();
        users.findByUsername("officer").orElseThrow().setEnabled(false);
        users.flush();

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void a_role_change_arrives_with_the_next_refresh() throws Exception {
        Pair before = login();
        assertThat(rolesIn(before.access())).isEqualTo("[\"OFFICER\"]");

        users.findByUsername("officer").orElseThrow().getRoles().add(Role.APPROVER);
        users.flush();

        Pair after = pair(refresh(before.refresh()));
        assertThat(rolesIn(after.access())).isEqualTo("[\"APPROVER\",\"OFFICER\"]");
    }

    @Test
    void an_expired_refresh_token_is_rejected() throws Exception {
        AppUser officer = users.findByUsername("officer").orElseThrow();
        refreshTokens.save(new RefreshToken(RefreshToken.hash("expired-token"), officer, UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES)));

        refresh("expired-token").andExpect(status().isUnauthorized());
    }

    @Test
    void an_access_token_is_not_a_refresh_token() throws Exception {
        Pair tokens = login();

        refresh(tokens.access())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("SQL"))));
    }
}
