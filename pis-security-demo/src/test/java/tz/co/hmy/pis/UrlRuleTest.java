package tz.co.hmy.pis;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.repository.UrlRuleRepository;
import tz.co.hmy.pis.security.DatabaseUrlRules;

import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * URL rules in the database: the url_rule table decides what each /api/v1 endpoint
 * needs. Every other test class still passes against the seeded table, which is
 * the proof that V8 holds exactly the rules SecurityConfig had.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UrlRuleTest {

    private static final String SUPPLIER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String APPROVERS_ONLY = """
            { "priority": 65, "method": "GET", "pattern": "/api/v1/requisitions/**",
              "permission": "requisition:approve", "description": "Only approvers may read requisitions" }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired UrlRuleRepository rules;
    @Autowired DatabaseUrlRules urlRules;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of("APPROVER")));
    }

    /**
     * The test's transaction rolls back every rule it added, but the in-memory copy
     * would keep them and leak into the next test. Reload after the rollback.
     */
    @AfterTransaction
    void reloadRules() {
        urlRules.reload();
    }

    private ResultActions as(String user, String password, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(httpBasic(user, password)));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as("admin", "admin-pass", request);
    }

    private ResultActions addRule(String json) throws Exception {
        return asAdmin(post("/api/v1/url-rules").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOfRule(int priority) {
        return rules.findByPriority(priority).orElseThrow().getId();
    }

    // ---------------------------------------------------------------- the table

    @Test
    void the_table_holds_the_rules_in_the_order_they_are_checked() throws Exception {
        asAdmin(get("/api/v1/url-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(12)))
            .andExpect(jsonPath("$[0].pattern").value("/api/v1/requisitions/*/approve"))
            .andExpect(jsonPath("$[3].method").value("DELETE"))
            .andExpect(jsonPath("$[3].permission").value("record:delete"))
            .andExpect(jsonPath("$[5].method").value("ANY"));
    }

    @Test
    void only_role_managers_may_see_or_change_the_rules() throws Exception {
        as("officer", "officer-pass", get("/api/v1/url-rules")).andExpect(status().isForbidden());
        as("officer", "officer-pass", post("/api/v1/url-rules").contentType(MediaType.APPLICATION_JSON)
                .content(APPROVERS_ONLY)).andExpect(status().isForbidden());
    }

    @Test
    void match_says_which_rule_decides() throws Exception {
        asAdmin(get("/api/v1/url-rules/match").param("method", "patch").param("path", "/api/v1/suppliers/" + SUPPLIER_ID + "/status"))
            .andExpect(jsonPath("$.rule.priority").value(30))
            .andExpect(jsonPath("$.decision").value("needs supplier:approve (rule 30)"));
        asAdmin(get("/api/v1/url-rules/match").param("method", "GET").param("path", "/api/v1/reports"))
            .andExpect(jsonPath("$.rule").doesNotExist())                // null fields are left out
            .andExpect(jsonPath("$.decision").value("no rule matches: denied"));
    }

    // ---------------------------------------------------------------- changes take effect

    @Test
    void a_new_rule_works_on_the_next_request() throws Exception {
        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isOk());

        String body = addRule(APPROVERS_ONLY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedBy").value("admin"))
            .andReturn().getResponse().getContentAsString();

        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isForbidden());
        as("approver", "approver-pass", get("/api/v1/requisitions")).andExpect(status().isOk());

        Integer id = JsonPath.read(body, "$.id");
        asAdmin(delete("/api/v1/url-rules/{id}", id)).andExpect(status().isNoContent());
        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isOk());
    }

    /**
     * The first match decides, so deleting a SPECIFIC rule lets a broader one below
     * it decide instead. Here that OPENS something: rule 60 (any method on suppliers,
     * supplier:write) now covers the status change, and officers have supplier:write.
     */
    @Test
    void deleting_a_specific_rule_lets_a_broader_one_decide() throws Exception {
        MockHttpServletRequestBuilder activate = patch("/api/v1/suppliers/{id}/status", SUPPLIER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }");
        as("officer", "officer-pass", activate).andExpect(status().isForbidden());

        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(30))).andExpect(status().isNoContent());

        as("officer", "officer-pass", activate).andExpect(status().isOk());
    }

    /** No rule: denied. Even the admin, who holds every permission. */
    @Test
    void when_no_rule_matches_even_the_admin_is_refused() throws Exception {
        asAdmin(get("/api/v1/invoices")).andExpect(status().isOk());

        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(110))).andExpect(status().isNoContent());
        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(120))).andExpect(status().isNoContent());

        asAdmin(get("/api/v1/invoices")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- guard rails

    /** The admin endpoints are matched in code BEFORE the table is asked. */
    @Test
    void a_rule_cannot_open_the_admin_endpoints() throws Exception {
        asAdmin(get("/api/v1/url-rules")).andExpect(status().isOk());
        addRule("""
                { "priority": 1, "pattern": "/api/v1/**", "permission": "supplier:read",
                  "description": "Anyone who can read suppliers can do anything" }
                """).andExpect(status().isCreated());

        as("officer", "officer-pass", get("/api/v1/roles")).andExpect(status().isForbidden());
        as("officer", "officer-pass", get("/api/v1/users")).andExpect(status().isForbidden());
        as("officer", "officer-pass", get("/api/v1/url-rules")).andExpect(status().isForbidden());
    }

    @Test
    void rules_for_admin_endpoints_or_outside_the_api_are_refused() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/roles/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isUnprocessableEntity());
        addRule("""
                { "priority": 5, "pattern": "/actuator/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_pattern_spring_cannot_read_is_refused() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/**/approve", "permission": "supplier:read", "description": "x" }
                """)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Pattern /api/v1/**/approve is not valid")));
    }

    @Test
    void two_rules_cannot_share_a_priority() throws Exception {
        addRule("""
                { "priority": 50, "pattern": "/api/v1/reports/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isConflict());
    }

    @Test
    void unknown_permissions_and_methods_are_a_400() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/reports/**", "permission": "supplier:fly", "description": "x" }
                """).andExpect(status().isBadRequest());
        addRule("""
                { "priority": 5, "method": "FETCH", "pattern": "/api/v1/reports/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isBadRequest());
    }
}
