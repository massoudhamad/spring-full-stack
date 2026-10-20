package tz.co.hmy.pis;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LoginRateLimitFilter through the whole chain, with a limit of 3.
 *
 * Its own limit makes this a separate Spring context. Each test sends from its own
 * address, because the filter's counts live as long as the context does.
 */
@SpringBootTest(properties = "pis.security.login-limit.max-failures=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginLimitTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    private static RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    private void wrongBasicLogin(String address) throws Exception {
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "guess")).with(from(address)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void after_three_failures_even_the_right_password_is_refused() throws Exception {
        for (int i = 0; i < 3; i++) wrongBasicLogin("10.0.0.1");

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.1")))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Too many failed logins"));
    }

    @Test
    void failures_at_the_login_endpoint_count_too() throws Exception {
        String wrong = "{ \"username\": \"officer\", \"password\": \"guess\" }";
        String right = "{ \"username\": \"officer\", \"password\": \"officer-pass\" }";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").with(from("10.0.0.2"))
                    .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(from("10.0.0.2"))
                .contentType(MediaType.APPLICATION_JSON).content(right))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void another_address_is_not_affected() throws Exception {
        for (int i = 0; i < 3; i++) wrongBasicLogin("10.0.0.3");

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.4")))
            .andExpect(status().isOk());
    }

    @Test
    void successful_logins_do_not_count() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.5")))
                .andExpect(status().isOk());
        }
    }

    /** No credentials is a 401 too, but it isn't a guess: the filter skips it. */
    @Test
    void requests_without_credentials_do_not_count() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/suppliers").with(from("10.0.0.6"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.6")))
            .andExpect(status().isOk());
    }
}
