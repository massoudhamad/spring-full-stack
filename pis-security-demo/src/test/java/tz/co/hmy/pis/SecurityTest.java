package tz.co.hmy.pis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP Basic, end to end.
 *
 * The admin account is created at startup by AdminAccountInitializer, with
 * admin-pass from application-test.yml. The officer and approver are inserted
 * before each test and rolled back after it, like any other test data.
 *
 * Two tools, two purposes:
 *   httpBasic("user", "pass")  sends a real Authorization header, so the
 *                              password check itself is tested.
 *   @WithRole("X")          skips the password check and tests only
 *                              the authorization rules (Lesson 3C).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTest {

    private static final String UNKNOWN_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of("APPROVER")));
    }

    @Nested
    class Authentication {

        @Test
        void no_credentials_returns_401_with_a_basic_challenge() throws Exception {
            mvc.perform(get("/api/v1/suppliers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"pis\""))
                .andExpect(jsonPath("$.title").value("Authentication required"));
        }

        @Test
        void wrong_password_returns_401() throws Exception {
            mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "wrong")))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void unknown_user_returns_401() throws Exception {
            mvc.perform(get("/api/v1/suppliers").with(httpBasic("nobody", "officer-pass")))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void correct_credentials_return_200() throws Exception {
            mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")))
                .andExpect(status().isOk());
        }

        /** What httpBasic() does for you: "username:password", Base64-encoded. */
        @Test
        void a_hand_built_authorization_header_works_too() throws Exception {
            String token = Base64.getEncoder()
                    .encodeToString("officer:officer-pass".getBytes(StandardCharsets.UTF_8));
            // token is "b2ZmaWNlcjpvZmZpY2VyLXBhc3M=", which anyone can decode. Hence HTTPS.

            mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Basic " + token))
                .andExpect(status().isOk());
        }

        @Test
        void health_and_swagger_stay_public() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }

    /**
     * 401 means "I don't know who you are". 403 means "I know who you are, and no".
     *
     * An unknown id is used on purpose: 404 proves the request got PAST
     * security and reached the service, without changing any data.
     */
    @Nested
    class Authorization {

        @Test
        @WithRole("OFFICER")
        void officer_cannot_approve_a_requisition() throws Exception {
            mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
        }

        @Test
        @WithRole("APPROVER")
        void approver_can_approve_a_requisition() throws Exception {
            mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        @WithRole("APPROVER")
        void approver_cannot_create_a_supplier() throws Exception {
            mvc.perform(post("/api/v1/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithRole("OFFICER")
        void officer_cannot_delete() throws Exception {
            mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID))
                .andExpect(status().isForbidden());
        }

        @Test
        void admin_can_delete_using_real_credentials() throws Exception {
            mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID)
                    .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isNotFound());
        }

        @Test
        @WithRole("APPROVER")
        void any_authenticated_user_can_read() throws Exception {
            mvc.perform(get("/api/v1/requisitions")).andExpect(status().isOk());
        }
    }
}
