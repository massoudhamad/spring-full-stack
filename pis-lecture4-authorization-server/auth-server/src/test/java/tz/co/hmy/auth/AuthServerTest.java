package tz.co.hmy.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The parts of the authorization server that need no browser. The full
 * authorization-code flow, with a login page, is covered by the smoke script.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthServerTest {

    @Autowired MockMvc mvc;

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static String payload(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test
    void discovery_document_tells_clients_where_everything_is() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("http://localhost:9100"))
            .andExpect(jsonPath("$.token_endpoint").value("http://localhost:9100/oauth2/token"))
            .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9100/oauth2/jwks"));
    }

    @Test
    void the_public_key_is_published_and_the_private_key_is_not() throws Exception {
        mvc.perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].n").exists())      // public modulus
            .andExpect(jsonPath("$.keys[0].d").doesNotExist()); // private exponent: never
    }

    @Test
    void reporting_client_gets_a_token_with_a_scope_and_no_roles() throws Exception {
        String body = mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basic("pis-reporting", "reporting-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "suppliers.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())   // no user, nothing to refresh
                .andReturn().getResponse().getContentAsString();

        String claims = payload(JsonPath.read(body, "$.access_token"));
        assertThat(JsonPath.<String>read(claims, "$.sub")).isEqualTo("pis-reporting");
        assertThat(JsonPath.<String>read(claims, "$.iss")).isEqualTo("http://localhost:9100");
        assertThat(claims).contains("suppliers.read").doesNotContain("roles");
    }

    @Test
    void a_wrong_client_secret_is_rejected() throws Exception {
        mvc.perform(post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, basic("pis-reporting", "wrong"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void a_client_cannot_ask_for_a_scope_it_was_not_given() throws Exception {
        mvc.perform(post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, basic("pis-reporting", "reporting-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", "pis"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    /*
     * /oauth2/authorize reads its parameters from the QUERY STRING. MockMvc's
     * .param(...) on a GET doesn't put them there, so they go in a URI template,
     * which also encodes the space in "openid pis" correctly.
     */
    private static final String AUTHORIZE = "/oauth2/authorize?response_type=code&client_id=pis-web"
            + "&redirect_uri={redirect}&scope={scope}&state=s1";
    private static final String CHALLENGE =
            "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256";

    @Test
    void the_browser_client_must_use_pkce() throws Exception {
        // No code_challenge. The redirect_uri is registered, so the error is sent back to the app.
        mvc.perform(get(AUTHORIZE, "http://127.0.0.1:3000/callback", "openid pis"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://127.0.0.1:3000/callback?error=invalid_request")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("code_challenge")));
    }

    @Test
    void an_unregistered_redirect_uri_is_never_redirected_to() throws Exception {
        // An attacker's site must never receive anything, so the server answers
        // with an error itself instead of redirecting.
        mvc.perform(get(AUTHORIZE + CHALLENGE, "https://evil.example.com/steal", "openid pis"))
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void a_correct_request_sends_the_browser_to_the_login_page() throws Exception {
        mvc.perform(get(AUTHORIZE + CHALLENGE, "http://127.0.0.1:3000/callback", "openid pis")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/login"));
    }
}
