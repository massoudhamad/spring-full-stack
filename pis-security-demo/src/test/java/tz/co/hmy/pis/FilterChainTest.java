package tz.co.hmy.pis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.LoginRateLimitFilter;
import tz.co.hmy.pis.security.RequestLogFilter;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain itself: what's in it, in which order, and what our
 * two filters see. OutputCaptureExtension hands each test the log it produced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class FilterChainTest {

    private static final String SUPPLIER_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired MockMvc mvc;
    @Autowired FilterChainProxy filterChainProxy;      // the bean that runs every SecurityFilterChain
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    // ---------------------------------------------------------------- the chain

    @Test
    void the_chain_is_a_list_of_filters_and_ours_are_where_we_put_them() {
        List<String> filters = filterChainProxy.getFilterChains().get(0).getFilters().stream()
                .map(f -> f.getClass().getSimpleName()).toList();

        // addFilterBefore(..., BearerTokenAuthenticationFilter.class)
        assertThat(filters.indexOf("LoginRateLimitFilter"))
                .isEqualTo(filters.indexOf("BearerTokenAuthenticationFilter") - 1);
        // addFilterAfter(..., BasicAuthenticationFilter.class)
        assertThat(filters.indexOf("RequestLogFilter"))
                .isEqualTo(filters.indexOf("BasicAuthenticationFilter") + 1);
        // the decision is always last
        assertThat(filters).endsWith("ExceptionTranslationFilter", "AuthorizationFilter");
        // .csrf(AbstractHttpConfigurer::disable) REMOVES a filter
        assertThat(filters).doesNotContain("CsrfFilter");
    }

    /** A Filter bean is ALSO registered as a plain servlet filter, outside the chain. */
    @Test
    void our_filters_are_not_beans() {
        assertThat(context.getBeansOfType(RequestLogFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(LoginRateLimitFilter.class)).isEmpty();
    }

    // ---------------------------------------------------------------- RequestLogFilter

    @Test
    void the_request_log_names_the_user_and_the_status(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isOk());

        assertThat(log.getOut()).contains("GET /api/v1/suppliers -> 200 by officer in ");
    }

    /** AuthorizationFilter runs after ours, so we see its refusal on the way back. */
    @Test
    void a_refused_request_is_logged_with_its_403(CapturedOutput log) throws Exception {
        mvc.perform(patch("/api/v1/suppliers/{id}/status", SUPPLIER_ID).with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }"))
            .andExpect(status().isForbidden());

        assertThat(log.getOut()).contains("PATCH /api/v1/suppliers/" + SUPPLIER_ID + "/status -> 403 by officer");
    }

    @Test
    void no_credentials_is_logged_as_anonymous(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/requisitions")).andExpect(status().isUnauthorized());

        assertThat(log.getOut()).contains("GET /api/v1/requisitions -> 401 by anonymous");
    }

    /**
     * A wrong password: BasicAuthenticationFilter answers 401 and STOPS the chain,
     * so the request never reaches RequestLogFilter. LoginRateLimitFilter, placed
     * before it, sees the 401 on the way back.
     */
    @Test
    void a_failed_login_stops_the_chain_before_the_request_log(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/invoices").with(httpBasic("officer", "wrong"))
                .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
            .andExpect(status().isUnauthorized());

        assertThat(log.getOut()).doesNotContain("GET /api/v1/invoices ->");
        assertThat(log.getOut()).contains("Failed login 1 of 1000 from 10.9.9.9");
    }
}
