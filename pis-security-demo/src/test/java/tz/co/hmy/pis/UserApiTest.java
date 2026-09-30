package tz.co.hmy.pis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Users in the database, end to end: create, log in, disable, and the
 * created_by column filled in from whoever is logged in.
 *
 * Every test uses real credentials (httpBasic), because the point is that
 * the password check now reads from app_user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiTest {

    private static final String NEW_USER = """
            { "username": "Asha.Juma", "password": "s3cure-pass",
              "fullName": "Asha Juma", "roles": ["APPROVER"] }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createOfficer() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    @Test
    void admin_creates_a_user_and_the_response_has_no_password() throws Exception {
        mvc.perform(post("/api/v1/users").with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.username").value("asha.juma"))
            .andExpect(jsonPath("$.roles[0]").value("APPROVER"))
            .andExpect(jsonPath("$.createdBy").value("admin"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void the_password_is_stored_as_a_bcrypt_hash() throws Exception {
        mvc.perform(post("/api/v1/users").with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
            .andExpect(status().isCreated());

        String stored = users.findByUsername("asha.juma").orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("{bcrypt}").doesNotContain("s3cure-pass");
    }

    @Test
    void a_new_user_can_log_in_straight_away() throws Exception {
        mvc.perform(post("/api/v1/users").with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
            .andExpect(status().isCreated());

        // Username lookup ignores case: the account was stored as asha.juma.
        mvc.perform(get("/api/v1/users/me").with(httpBasic("Asha.Juma", "s3cure-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("asha.juma"))
            .andExpect(jsonPath("$.fullName").value("Asha Juma"));
    }

    @Test
    void a_duplicate_username_returns_409() throws Exception {
        mvc.perform(post("/api/v1/users").with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "OFFICER", "password": "another-pass",
                      "fullName": "Someone Else", "roles": ["OFFICER"] }
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void a_short_password_is_rejected() throws Exception {
        mvc.perform(post("/api/v1/users").with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "shorty", "password": "abc",
                      "fullName": "Short Password", "roles": ["OFFICER"] }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void an_officer_cannot_list_users() throws Exception {
        mvc.perform(get("/api/v1/users").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isForbidden());
    }

    @Test
    void any_user_can_ask_who_they_are() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("officer"))
            .andExpect(jsonPath("$.roles[0]").value("OFFICER"));
    }

    @Test
    void a_disabled_user_can_no_longer_log_in() throws Exception {
        AppUser officer = users.findByUsername("officer").orElseThrow();

        mvc.perform(patch("/api/v1/users/{id}/enabled", officer.getId())
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"enabled\": false }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void a_new_supplier_records_who_created_it() throws Exception {
        mvc.perform(post("/api/v1/suppliers").with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Pemba Hardware Ltd", "tin": "888-777-666",
                      "registrationNumber": "BRELA-2024-8888",
                      "category": "GOODS", "email": "info@pembahw.co.tz" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value("officer"))
            .andExpect(jsonPath("$.updatedBy").value("officer"));
    }
}
