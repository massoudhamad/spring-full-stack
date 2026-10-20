package tz.co.hmy.pis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tz.co.hmy.pis.config.LoginLimitProperties;
import tz.co.hmy.pis.config.ProblemDetailSecurityHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A filter is a plain class: it can be tested without Spring, with mock
 * requests and a fake clock, so "wait a minute" takes no time at all.
 */
class LoginRateLimitFilterTest {

    /** A clock the test moves by hand. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2030-01-01T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    final TestClock clock = new TestClock();
    final LoginRateLimitFilter filter = new LoginRateLimitFilter(
            new LoginLimitProperties(2, Duration.ofMinutes(1)), clock,
            new ProblemDetailSecurityHandler(new ObjectMapper().findAndRegisterModules()));

    /** Sends one Basic login through the filter; behind it, every password is wrong. */
    private MockHttpServletResponse attempt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/suppliers");
        request.addHeader("Authorization", "Basic b2ZmaWNlcjpndWVzcw==");   // officer:guess
        request.setRemoteAddr("10.1.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                res.setStatus(401);                                            // "wrong password"
            }
        }));
        return response;
    }

    @Test
    void the_block_lifts_when_the_window_ends() throws Exception {
        assertThat(attempt().getStatus()).isEqualTo(401);
        assertThat(attempt().getStatus()).isEqualTo(401);
        assertThat(attempt().getStatus()).isEqualTo(429);                    // blocked

        clock.advance(Duration.ofSeconds(59));
        assertThat(attempt().getStatus()).isEqualTo(429);                    // still blocked

        clock.advance(Duration.ofSeconds(1));                                 // a minute after the first failure
        assertThat(attempt().getStatus()).isEqualTo(401);                    // may try again
    }

    @Test
    void retry_after_says_how_long_to_wait() throws Exception {
        attempt();
        clock.advance(Duration.ofSeconds(20));
        attempt();

        MockHttpServletResponse blocked = attempt();
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("40");          // 60 s from the FIRST failure
        assertThat(blocked.getContentAsString()).contains("Try again in 40 seconds");
    }
}
