package tz.co.hmy.pis;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.model.Role;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.service.RequisitionService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Permissions (who may do WHAT) and method security (rules on the service,
 * including rules that look at the record itself).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PermissionTest {

    private static final String REQUISITION = """
            { "department": "ICT", "requestedBy": "Neema Said",
              "justification": "Laptops for the new procurement officers",
              "requiredByDate": "2030-12-31",
              "items": [ { "description": "Laptop", "quantity": 2,
                           "unit": "PIECE", "estimatedUnitPrice": 2500000.00 } ] }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RequisitionService requisitions;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of(Role.OFFICER)));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of(Role.APPROVER)));
    }

    /** Creates and submits a requisition as the given user; returns its id. */
    private String raiseAndSubmit(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/requisitions").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON).content(REQUISITION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(username))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/requisitions/{id}/submit", id).with(httpBasic(username, password)))
            .andExpect(status().isOk());
        return id;
    }

    // ---------------------------------------------------------------- the mapping

    @Test
    void roles_are_bundles_of_permissions() {
        assertThat(Role.APPROVER.permissions())
            .contains(Permission.REQUISITION_APPROVE, Permission.SUPPLIER_APPROVE)
            .doesNotContain(Permission.REQUISITION_WRITE, Permission.SUPPLIER_WRITE, Permission.RECORD_DELETE);
        assertThat(Role.OFFICER.permissions())
            .contains(Permission.REQUISITION_WRITE)
            .doesNotContain(Permission.REQUISITION_APPROVE);
        assertThat(Role.ADMIN.permissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void me_lists_the_permissions_a_login_brings() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(httpBasic("approver", "approver-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("APPROVER"))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems(
                    "requisition:approve", "supplier:approve", "supplier:read")))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.hasItem("supplier:write"))));
    }

    @Test
    void the_token_carries_the_permissions_too() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"approver\", \"password\": \"approver-pass\" }"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(claims).contains("\"permissions\"").contains("requisition:approve");

        // and a token-only request is judged by those permissions
        mvc.perform(patch("/api/v1/suppliers/{id}/status", "33333333-3333-3333-3333-333333333333")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }"))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- URL rules

    @Test
    void permission_rules_behave_like_the_old_role_rules() throws Exception {
        mvc.perform(patch("/api/v1/suppliers/{id}/status", "33333333-3333-3333-3333-333333333333")
                .with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }"))
            .andExpect(status().isForbidden());                     // officer: no supplier:approve
        mvc.perform(post("/api/v1/requisitions").with(httpBasic("approver", "approver-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(REQUISITION))
            .andExpect(status().isForbidden());                     // approver: no requisition:write
    }

    // ---------------------------------------------------------------- method security

    @Test
    void an_approver_approves_an_officers_requisition() throws Exception {
        String id = raiseAndSubmit("officer", "officer-pass");

        mvc.perform(post("/api/v1/requisitions/{id}/approve", id).with(httpBasic("approver", "approver-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void nobody_approves_their_own_requisition_not_even_an_admin() throws Exception {
        String id = raiseAndSubmit("admin", "admin-pass");         // admin holds every permission

        mvc.perform(post("/api/v1/requisitions/{id}/approve", id).with(httpBasic("admin", "admin-pass")))
            .andExpect(status().isForbidden())                      // 403, not 500
            .andExpect(jsonPath("$.title").value("Access denied"))
            .andExpect(jsonPath("$.type").value("https://hmy.co.tz/problems/forbidden"));
        mvc.perform(post("/api/v1/requisitions/{id}/reject", id).with(httpBasic("admin", "admin-pass")))
            .andExpect(status().isForbidden());

        // someone else with the permission may
        mvc.perform(post("/api/v1/requisitions/{id}/approve", id).with(httpBasic("approver", "approver-pass")))
            .andExpect(status().isOk());
    }

    @Test
    void an_unknown_requisition_is_still_404() throws Exception {
        mvc.perform(post("/api/v1/requisitions/{id}/approve", UUID.randomUUID())
                .with(httpBasic("approver", "approver-pass")))
            .andExpect(status().isNotFound());
    }

    /** No HTTP at all: the rule is on the service, so a batch job or another service hits it too. */
    @Test
    @WithRole(Role.OFFICER)
    void the_service_itself_refuses_without_the_permission() {
        assertThatThrownBy(() -> requisitions.approve(UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);
    }
}
