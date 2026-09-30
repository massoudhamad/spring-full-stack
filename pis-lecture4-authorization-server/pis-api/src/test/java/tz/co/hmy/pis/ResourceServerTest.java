package tz.co.hmy.pis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PIS's rules, tested with jwt(): a ready-made token, no authorization server
 * needed. Whether real tokens from the real server are accepted is what the
 * smoke script checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResourceServerTest {

    private static final String UNKNOWN_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired MockMvc mvc;

    /** What pis-web gets for a person: sub, roles, and the scopes the user agreed to. */
    private static RequestPostProcessor user(String username, String... roles) {
        return jwt()
                .jwt(token -> token.subject(username).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(GrantedAuthority[]::new));
    }

    /** What pis-reporting gets: no person, no roles, only the scope it was registered for. */
    private static RequestPostProcessor reportingJob() {
        return jwt()
                .jwt(token -> token.subject("pis-reporting").claim("scope", "suppliers.read"))
                .authorities(new SimpleGrantedAuthority("SCOPE_suppliers.read"));
    }

    @Test
    void no_token_is_401_with_a_bearer_challenge() throws Exception {
        mvc.perform(get("/api/v1/suppliers"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void basic_auth_is_gone() throws Exception {
        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Basic b2ZmaWNlcjpvZmZpY2VyMTIz"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_shows_what_the_token_says() throws Exception {
        mvc.perform(get("/api/v1/me").with(user("officer", "OFFICER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value("officer"))
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_OFFICER"));
    }

    @Test
    void roles_from_the_token_drive_the_same_rules_as_before() throws Exception {
        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID).with(user("officer", "OFFICER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID).with(user("approver", "APPROVER")))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID).with(user("admin", "ADMIN", "APPROVER", "OFFICER")))
            .andExpect(status().isNotFound());
    }

    @Test
    void the_reporting_job_may_read_suppliers_and_nothing_else() throws Exception {
        mvc.perform(get("/api/v1/suppliers").with(reportingJob()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/requisitions").with(reportingJob()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/suppliers").with(reportingJob())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void created_by_is_the_token_subject() throws Exception {
        mvc.perform(post("/api/v1/suppliers").with(user("officer", "OFFICER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Kilwa Office Supplies", "tin": "321-654-987",
                      "registrationNumber": "BRELA-2026-3210",
                      "category": "GOODS", "email": "orders@kilwaoffice.co.tz" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value("officer"));
    }
}
