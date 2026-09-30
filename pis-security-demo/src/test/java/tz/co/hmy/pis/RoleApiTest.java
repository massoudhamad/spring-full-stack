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
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Roles and their permissions live in the database, and an admin changes them
 * at runtime. Every request here uses a real login, so the whole path is
 * covered: role_permission → Authorities → the security rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleApiTest {

    private static final String AUDITOR = """
            { "name": "AUDITOR", "description": "Internal audit: reads everything",
              "permissions": ["supplier:read", "requisition:read", "purchase-order:read", "invoice:read"] }
            """;
    private static final String SUPPLIER = """
            { "name": "Tanga Paper Mills", "tin": "444-555-666", "registrationNumber": "BRELA-2026-4444",
              "category": "GOODS", "email": "sales@tangapaper.co.tz" }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createOfficer() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    private ResultActions asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(httpBasic("admin", "admin-pass")));
    }

    /** Creates the AUDITOR role and an "auditor" account holding it. */
    private void createAuditor() throws Exception {
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isCreated());
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                { "username": "auditor", "password": "auditor-pass", "fullName": "Zuhura Audit",
                  "roles": ["AUDITOR"] }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permissions", hasSize(4)));
    }

    private ResultActions grantSupplierWrite() throws Exception {
        return asAdmin(put("/api/v1/roles/AUDITOR/permissions").contentType(MediaType.APPLICATION_JSON).content("""
                { "permissions": ["supplier:read", "supplier:write", "requisition:read",
                                  "purchase-order:read", "invoice:read"] }
                """));
    }

    // ---------------------------------------------------------------- reading

    @Test
    void the_built_in_roles_come_from_the_migration() throws Exception {
        asAdmin(get("/api/v1/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'APPROVER')].permissions[*]", hasItem("requisition:approve")))
            .andExpect(jsonPath("$[?(@.name == 'ADMIN')].builtIn", hasItem(true)))
            .andExpect(jsonPath("$[?(@.name == 'ADMIN')].permissions[*]", hasItem("role:manage")));
        asAdmin(get("/api/v1/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(13)))
            .andExpect(jsonPath("$", hasItem("role:manage")));
    }

    @Test
    void only_role_managers_may_see_or_change_roles() throws Exception {
        mvc.perform(get("/api/v1/roles").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles").with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- a new role at runtime

    @Test
    void a_new_role_works_without_a_release() throws Exception {
        createAuditor();

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());
    }

    @Test
    void a_basic_login_sees_a_permission_change_on_its_next_request() throws Exception {
        createAuditor();
        grantSupplierWrite().andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions", hasItem("supplier:write")))
            .andExpect(jsonPath("$.users").value(1));

        mvc.perform(post("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isCreated());
    }

    /** The same trade-off as Lesson 3: a token carries what was true when it was issued. */
    @Test
    void a_token_sees_a_permission_change_only_after_refresh() throws Exception {
        createAuditor();
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"auditor\", \"password\": \"auditor-pass\" }"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldToken = JsonPath.read(login, "$.accessToken");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        grantSupplierWrite().andExpect(status().isOk());

        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());                       // old token: old permissions

        String refreshed = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"refreshToken\": \"%s\" }".formatted(refreshToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(refreshed, "$.accessToken"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isCreated());                         // new token: new permissions
    }

    @Test
    void roles_can_be_reassigned_at_runtime() throws Exception {
        createAuditor();
        AppUser officer = users.findByUsername("officer").orElseThrow();

        asAdmin(put("/api/v1/users/{id}/roles", officer.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roles\": [\"AUDITOR\"] }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));

        mvc.perform(post("/api/v1/suppliers").with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());                       // no longer an officer
    }

    // ---------------------------------------------------------------- guard rails

    @Test
    void nobody_can_change_the_admin_role() throws Exception {
        asAdmin(put("/api/v1/roles/ADMIN/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"permissions\": [\"supplier:read\"] }"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_role_in_use_or_built_in_cannot_be_deleted() throws Exception {
        createAuditor();
        asAdmin(delete("/api/v1/roles/AUDITOR")).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Role AUDITOR is held by 1 user(s). Take it away from them first."));
        asAdmin(delete("/api/v1/roles/APPROVER")).andExpect(status().isUnprocessableEntity());

        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"TEMP\", \"description\": \"Unused\", \"permissions\": [] }"))
            .andExpect(status().isCreated());
        asAdmin(delete("/api/v1/roles/TEMP")).andExpect(status().isNoContent());
    }

    @Test
    void bad_input_is_refused_clearly() throws Exception {
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isCreated());
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isConflict());                        // duplicate name
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"X\", \"description\": \"d\", \"permissions\": [\"supplier:fly\"] }"))
            .andExpect(status().isBadRequest())                       // unknown permission
            .andExpect(jsonPath("$.title").value("Malformed request"));
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                { "username": "ghost", "password": "ghost-pass", "fullName": "Ghost", "roles": ["NOPE"] }
                """))
            .andExpect(status().isUnprocessableEntity())              // unknown role
            .andExpect(jsonPath("$.detail").value("Unknown role: NOPE"));
    }

    @Test
    void broken_json_is_a_400_not_a_500() throws Exception {
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{ \"username\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request"));
    }
}
