package tz.co.hmy.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerTest {

    @Autowired TestRestTemplate http;

    @Test
    void the_registry_answers_and_starts_empty() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> apps = http.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(apps.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apps.getBody()).contains("\"applications\"").doesNotContain("\"instance\"");
    }

    @Test
    void the_dashboard_is_served() {
        ResponseEntity<String> page = http.getForEntity("/", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Eureka");
    }
}
